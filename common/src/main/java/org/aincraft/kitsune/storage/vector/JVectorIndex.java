package org.aincraft.kitsune.storage.vector;

import io.github.jbellis.jvector.disk.ReaderSupplier;
import io.github.jbellis.jvector.disk.ReaderSupplierFactory;
import io.github.jbellis.jvector.graph.GraphIndexBuilder;
import io.github.jbellis.jvector.graph.GraphSearcher;
import io.github.jbellis.jvector.graph.ListRandomAccessVectorValues;
import io.github.jbellis.jvector.graph.SearchResult;
import io.github.jbellis.jvector.graph.disk.OnDiskGraphIndex;
import io.github.jbellis.jvector.graph.similarity.BuildScoreProvider;
import io.github.jbellis.jvector.graph.similarity.DefaultSearchScoreProvider;
import io.github.jbellis.jvector.util.Bits;
import io.github.jbellis.jvector.vector.VectorSimilarityFunction;
import io.github.jbellis.jvector.vector.types.VectorFloat;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JVector-based implementation of VectorIndex for approximate nearest neighbor search.
 * Uses an in-memory vector store with an on-disk graph index for persistence.
 *
 * Architecture:
 * - In-memory: List<VectorFloat<?>> vectors indexed by ordinal
 * - On-disk: vectors.idx containing the HNSW graph and vector data
 * - Thread-safe: ReadWriteLock ensures safe concurrent access
 * - Lazy rebuild: Index marked dirty on mutations, rebuilt before next search
 *
 * Graph parameters (configurable):
 * - GRAPH_DEGREE = 16 (max neighbors per node)
 * - CONSTRUCTION_SEARCH_DEPTH = 100 (search depth during graph construction)
 * - OVERFLOW_FACTOR = 1.2f (tolerance for degree overflow during construction)
 * - ALPHA = 1.2f (used in distance calculations for graph pruning)
 */
public final class JVectorIndex implements VectorIndex {

    private static final int GRAPH_DEGREE = 16;
    private static final int CONSTRUCTION_SEARCH_DEPTH = 100;
    private static final float OVERFLOW_FACTOR = 1.2f;
    private static final float ALPHA = 1.2f;

    private final Logger logger;
    private final Path dataDir;
    private final int dimension;
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(4);
    private final ReadWriteLock indexLock = new ReentrantReadWriteLock();

    // In-memory vector storage indexed by ordinal
    private final List<VectorFloat<?>> vectors = new ArrayList<>();

    // On-disk graph index state
    private OnDiskGraphIndex graphIndex;
    private ReaderSupplier readerSupplier;

    // Track if index needs rebuilding after mutations
    private volatile boolean indexDirty = false;

    /**
     * Create a new JVectorIndex.
     *
     * @param logger Logger for diagnostic output
     * @param dataDir Path to directory for storing index files
     * @param dimension Dimension of vectors to be indexed
     */
    public JVectorIndex(Logger logger, Path dataDir, int dimension) {
        this.logger = logger;
        this.dataDir = dataDir;
        this.dimension = dimension;
    }

    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(dataDir);
                loadIndex();
                logger.info("JVectorIndex initialized at " + dataDir.toAbsolutePath());
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to initialize JVectorIndex", e);
                throw new RuntimeException("JVectorIndex initialization failed", e);
            }
        }, executor);
    }

    /**
     * Load existing index from disk if available.
     * Gracefully handles missing or corrupted index files by marking for rebuild.
     */
    private void loadIndex() {
        Path indexPath = dataDir.resolve("vectors.idx");

        if (Files.exists(indexPath)) {
            try {
                readerSupplier = ReaderSupplierFactory.open(indexPath);
                graphIndex = OnDiskGraphIndex.load(readerSupplier);

                int graphSize = graphIndex.size();
                logger.info("Loaded JVectorIndex graph with " + graphSize + " nodes");

                // Load vectors from graph's view
                var graphView = graphIndex.getView();
                for (int ordinal = 0; ordinal < graphSize; ordinal++) {
                    try {
                        VectorFloat<?> vec = graphView.getVector(ordinal);
                        if (vec != null) {
                            // Expand vectors list to fit ordinal
                            while (vectors.size() <= ordinal) {
                                vectors.add(null);
                            }
                            vectors.set(ordinal, vec);
                        }
                    } catch (Exception e) {
                        logger.warning("Failed to load vector for ordinal " + ordinal + ": " + e.getMessage());
                    }
                }

                long validVectorCount = vectors.size();
                logger.info("Loaded " + validVectorCount + " vectors from JVectorIndex graph");
            } catch (Exception e) {
                logger.warning("Failed to load existing index, will rebuild: " + e.getMessage());
                graphIndex = null;
                if (readerSupplier != null) {
                    try {
                        readerSupplier.close();
                    } catch (Exception ignored) {}
                    readerSupplier = null;
                }
                indexDirty = true;
            }
        }
    }

    @Override
    public CompletableFuture<Void> addVector(int ordinal, float[] embedding) {
        return CompletableFuture.runAsync(() -> {
            indexLock.writeLock().lock();
            try {
                // Expand vectors list to fit ordinal
                while (vectors.size() <= ordinal) {
                    vectors.add(null);
                }

                // Add or update vector
                vectors.set(ordinal, toVectorFloat(embedding));
                indexDirty = true;
                logger.fine("Added vector at ordinal " + ordinal);
            } finally {
                indexLock.writeLock().unlock();
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> removeVector(int ordinal) {
        return CompletableFuture.runAsync(() -> {
            indexLock.writeLock().lock();
            try {
                if (ordinal >= 0 && ordinal < vectors.size()) {
                    vectors.set(ordinal, null);
                    indexDirty = true;
                    logger.fine("Removed vector at ordinal " + ordinal);
                }
            } finally {
                indexLock.writeLock().unlock();
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<VectorSearchResult>> search(float[] queryEmbedding, int limit) {
        return search(queryEmbedding, limit, null);
    }

    @Override
    public CompletableFuture<List<VectorSearchResult>> searchWithFilter(
            float[] queryEmbedding, int limit, Set<Integer> allowedOrdinals) {
        return search(queryEmbedding, limit, allowedOrdinals);
    }

    /**
     * Internal search implementation supporting both filtered and unfiltered search.
     *
     * @param queryEmbedding the query vector
     * @param limit maximum results
     * @param allowedOrdinals optional set to filter by; null means no filtering
     * @return list of search results
     */
    private CompletableFuture<List<VectorSearchResult>> search(
            float[] queryEmbedding, int limit, Set<Integer> allowedOrdinals) {
        return CompletableFuture.supplyAsync(() -> {
            // Rebuild index if needed - BEFORE acquiring read lock to avoid deadlock
            if (indexDirty || graphIndex == null) {
                indexLock.writeLock().lock();
                try {
                    // Double-check after acquiring lock
                    if (indexDirty || graphIndex == null) {
                        rebuildIndexInternal();
                    }
                } finally {
                    indexLock.writeLock().unlock();
                }
            }

            indexLock.readLock().lock();
            try {
                if (graphIndex == null || vectors.isEmpty()) {
                    logger.info("Search aborted: graphIndex=" + (graphIndex != null)
                            + ", vectorCount=" + vectors.size());
                    return Collections.emptyList();
                }

                VectorFloat<?> queryVector = toVectorFloat(queryEmbedding);
                List<VectorSearchResult> results = new ArrayList<>();

                try (GraphSearcher searcher = new GraphSearcher(graphIndex)) {
                    ListRandomAccessVectorValues ravv = new ListRandomAccessVectorValues(vectors, dimension);
                    DefaultSearchScoreProvider ssp = DefaultSearchScoreProvider.exact(
                            queryVector, VectorSimilarityFunction.COSINE, ravv
                    );

                    // Create optional filter bits
                    Bits filterBits = (allowedOrdinals != null && !allowedOrdinals.isEmpty())
                            ? allowedOrdinals::contains
                            : Bits.ALL;

                    int searchLimit = Math.min(limit * 10, vectors.size());
                    SearchResult sr = searcher.search(ssp, searchLimit, filterBits);

                    // Convert search results to VectorSearchResult
                    for (SearchResult.NodeScore nodeScore : sr.getNodes()) {
                        int ordinal = nodeScore.node;

                        // Safety check: skip stale ordinals
                        if (ordinal >= vectors.size()) {
                            logger.warning("Skipping stale ordinal " + ordinal);
                            continue;
                        }

                        if (vectors.get(ordinal) != null) {
                            results.add(new VectorSearchResult(ordinal, nodeScore.score));
                            if (results.size() >= limit) {
                                break;
                            }
                        }
                    }
                }

                logger.fine("Search complete: " + results.size() + " results returned");
                return results;
            } catch (Exception e) {
                logger.log(Level.WARNING, "Search failed", e);
                return Collections.emptyList();
            } finally {
                indexLock.readLock().unlock();
            }
        }, executor);
    }

    @Override
    public Optional<float[]> getVector(int ordinal) {
        indexLock.readLock().lock();
        try {
            if (ordinal >= 0 && ordinal < vectors.size()) {
                VectorFloat<?> vf = vectors.get(ordinal);
                if (vf != null) {
                    float[] arr = new float[dimension];
                    for (int i = 0; i < Math.min(dimension, vf.length()); i++) {
                        arr[i] = vf.get(i);
                    }
                    return Optional.of(arr);
                }
            }
            return Optional.empty();
        } finally {
            indexLock.readLock().unlock();
        }
    }

    @Override
    public CompletableFuture<Void> rebuildIndex() {
        return CompletableFuture.runAsync(() -> {
            indexLock.writeLock().lock();
            try {
                rebuildIndexInternal();
            } finally {
                indexLock.writeLock().unlock();
            }
        }, executor);
    }

    /**
     * Internal index rebuild logic - must be called with write lock held.
     * Compacts ordinal space by removing holes from deleted vectors.
     */
    private void rebuildIndexInternal() {
        try {
            if (!indexDirty && graphIndex != null) {
                return;
            }

            // Build compaction map: oldOrdinal -> newOrdinal
            Map<Integer, Integer> oldToNew = new HashMap<>();
            List<VectorFloat<?>> compactVectors = new ArrayList<>();

            int newOrdinal = 0;
            for (int oldOrdinal = 0; oldOrdinal < vectors.size(); oldOrdinal++) {
                VectorFloat<?> vec = vectors.get(oldOrdinal);
                if (vec != null) {
                    oldToNew.put(oldOrdinal, newOrdinal);
                    compactVectors.add(vec);
                    newOrdinal++;
                }
            }

            if (compactVectors.isEmpty()) {
                logger.info("No vectors to index, clearing index");
                cleanupGraphIndex();
                indexDirty = false;
                return;
            }

            // Replace in-memory structure with compacted version
            vectors.clear();
            vectors.addAll(compactVectors);

            logger.info("Rebuilding JVectorIndex with " + compactVectors.size() + " vectors");

            // Delete old index file to ensure clean rebuild
            Path indexPath = dataDir.resolve("vectors.idx");
            cleanupGraphIndex();
            Files.deleteIfExists(indexPath);

            // Build new graph index
            ListRandomAccessVectorValues ravv = new ListRandomAccessVectorValues(compactVectors, dimension);
            BuildScoreProvider bsp = BuildScoreProvider.randomAccessScoreProvider(
                    ravv, VectorSimilarityFunction.COSINE
            );

            try (GraphIndexBuilder builder = new GraphIndexBuilder(
                    bsp, dimension, GRAPH_DEGREE, CONSTRUCTION_SEARCH_DEPTH,
                    OVERFLOW_FACTOR, ALPHA,
                    false)) { // addHierarchy

                var index = builder.build(ravv);

                // Write to disk
                OnDiskGraphIndex.write(index, ravv, indexPath);

                // Reload from disk
                readerSupplier = ReaderSupplierFactory.open(indexPath);
                graphIndex = OnDiskGraphIndex.load(readerSupplier);

                indexDirty = false;
                logger.info("JVectorIndex rebuilt successfully with " + compactVectors.size() + " vectors");
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to rebuild index", e);
            throw new RuntimeException("Index rebuild failed", e);
        }
    }

    /**
     * Clean up graph index resources.
     */
    private void cleanupGraphIndex() throws IOException {
        if (readerSupplier != null) {
            try {
                readerSupplier.close();
            } catch (Exception ignored) {}
            readerSupplier = null;
        }
        graphIndex = null;
    }

    @Override
    public CompletableFuture<Void> purgeAll() {
        return CompletableFuture.runAsync(() -> {
            indexLock.writeLock().lock();
            try {
                // Clear in-memory vectors
                vectors.clear();

                // Clear graph index
                cleanupGraphIndex();

                // Delete index file
                Path indexPath = dataDir.resolve("vectors.idx");
                Files.deleteIfExists(indexPath);

                indexDirty = false;
                logger.info("Purged all vectors from JVectorIndex");
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to purge all vectors", e);
                throw new RuntimeException("Purge failed", e);
            } finally {
                indexLock.writeLock().unlock();
            }
        }, executor);
    }

    @Override
    public int size() {
        indexLock.readLock().lock();
        try {
            return vectors.size();
        } finally {
            indexLock.readLock().unlock();
        }
    }

    @Override
    public boolean isDirty() {
        return indexDirty;
    }

    @Override
    public int getDimension() {
        return dimension;
    }

    @Override
    public void shutdown() {
        try {
            // Rebuild index if dirty before shutdown
            if (indexDirty) {
                indexLock.writeLock().lock();
                try {
                    rebuildIndexInternal();
                } finally {
                    indexLock.writeLock().unlock();
                }
            }

            // Close graph index resources
            cleanupGraphIndex();

            logger.info("JVectorIndex shut down successfully");
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error during shutdown", e);
        }
        executor.shutdown();
    }

    /**
     * Convert a float array to JVector's VectorFloat type.
     *
     * @param array float array to convert
     * @return VectorFloat<?> representation
     */
    private VectorFloat<?> toVectorFloat(float[] array) {
        return io.github.jbellis.jvector.vector.VectorizationProvider
                .getInstance()
                .getVectorTypeSupport()
                .createFloatVector(array);
    }
}
