package org.aincraft.kitsune.embedding;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.aincraft.kitsune.config.KitsuneConfig;
import org.aincraft.kitsune.embedding.download.*;
import org.aincraft.kitsune.KitsunePlatform;

/**
 * Embedding service for multilingual-e5-large-instruct model.
 * Hardcoded to use the intfloat/multilingual-e5-large-instruct embedding model with 1024 dimensions.
 * Uses task prefixes for queries (INSTRUCT format) but not for documents.
 * Uses virtual threads for ONNX inference and file I/O operations (JDK 21+).
 */
public class MultilingualE5LargeInstructEmbeddingService implements EmbeddingService {
    private final Logger logger;
    private final KitsunePlatform dataFolderProvider;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private OrtEnvironment env;
    private OrtSession session;
    private HuggingFaceTokenizer tokenizer;

    // Hardcoded model configuration
    private static final String MODEL_NAME = "multilingual-e5-large-instruct";
    private static final int EMBEDDING_DIM = 1024;
    private static final String REPOSITORY = "intfloat/multilingual-e5-large-instruct";
    private static final String MODEL_PATH = "onnx/model.onnx";
    private static final String TOKENIZER_PATH = "tokenizer.json";
    private static final int MAX_SEQUENCE_LENGTH = 512;

    // Task prefix for query/search inputs
    private static final String INSTRUCT_PREFIX = "Instruct: Given a web search query, retrieve relevant passages that answer the query\nQuery: ";

    private final KitsuneConfig config;
    private final boolean autoDownloadEnabled;
    private HttpClient httpClient;
    private HuggingFaceModelDownloader downloader;

    public MultilingualE5LargeInstructEmbeddingService(KitsuneConfig config, Logger logger, KitsunePlatform dataFolderProvider) {
        this.logger = logger;
        this.dataFolderProvider = dataFolderProvider;
        this.config = config;
        this.autoDownloadEnabled = true;
    }

    @Override
    public CompletableFuture<Void> initialize() {
        return ensureModelAvailable()
            .thenCompose(v -> doInitialize());
    }

    private CompletableFuture<Void> ensureModelAvailable() {
        Path dataFolder = dataFolderProvider.getDataFolder();
        Path modelsDir = dataFolder.resolve("models");
        Path modelPath = modelsDir.resolve(MODEL_NAME + ".onnx");
        Path dataFilePath = modelsDir.resolve("model.onnx_data");
        Path tokenizerPath = modelsDir.resolve("tokenizer.json");

        // If all files exist, skip download
        if (Files.exists(modelPath) && Files.exists(dataFilePath) && Files.exists(tokenizerPath)) {
            logger.info("multilingual-e5-large-instruct ONNX model, data file, and tokenizer found locally");
            return CompletableFuture.completedFuture(null);
        }

        // If auto-download disabled, fail
        if (!autoDownloadEnabled) {
            logger.warning("multilingual-e5-large-instruct model files missing and auto-download is disabled");
            return CompletableFuture.failedFuture(
                new IllegalStateException("multilingual-e5-large-instruct ONNX model not found and auto-download is disabled"));
        }

        // Download from hardcoded Hugging Face repository
        logger.info("multilingual-e5-large-instruct model files missing, downloading from Hugging Face...");
        initializeDownloader();
        ModelSpec spec = ModelRegistry.fromCustomConfig(MODEL_NAME, REPOSITORY, MODEL_PATH, TOKENIZER_PATH, EMBEDDING_DIM);
        DownloadProgressListener listener = new ConsoleProgressReporter(logger);
        return downloader.downloadModel(spec, modelsDir, listener)
            .thenCompose(v -> {
                // Also download the external data file
                if (!Files.exists(dataFilePath)) {
                    logger.info("Downloading multilingual-e5-large-instruct external data file (model.onnx_data)...");
                    return downloader.downloadFile(REPOSITORY, "onnx/model.onnx_data", dataFilePath, listener)
                        .thenRun(() -> {}); // Convert Path return to Void
                }
                return CompletableFuture.completedFuture(null);
            });
    }

    private void initializeDownloader() {
        httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        downloader = new HuggingFaceModelDownloader(httpClient, logger, 3);
    }

    private CompletableFuture<Void> doInitialize() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                env = OrtEnvironment.getEnvironment();
                Path dataFolder = dataFolderProvider.getDataFolder();
                Path modelPath = dataFolder.resolve("models").resolve(MODEL_NAME + ".onnx");

                if (!Files.exists(modelPath)) {
                    logger.warning("multilingual-e5-large-instruct ONNX model not found at " + modelPath);
                    throw new IllegalStateException("multilingual-e5-large-instruct ONNX model not found");
                }

                session = env.createSession(modelPath.toString());

                Path tokenizerJsonPath = dataFolder.resolve("models").resolve("tokenizer.json");

                // Fix classloader context for DJL native library loading in plugin environments
                // See: https://github.com/deepjavalibrary/djl/issues/2224
                ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
                try {
                    Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

                    if (Files.exists(tokenizerJsonPath)) {
                        tokenizer = HuggingFaceTokenizer.newInstance(tokenizerJsonPath);
                        logger.info("Loaded tokenizer from tokenizer.json");
                    } else {
                        logger.warning("No tokenizer found");
                        throw new IllegalStateException("Tokenizer not found");
                    }
                } finally {
                    Thread.currentThread().setContextClassLoader(originalClassLoader);
                }

                logger.info("multilingual-e5-large-instruct embedding service initialized (" + EMBEDDING_DIM + " dimensions)");
                return null;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to initialize multilingual-e5-large-instruct ONNX session", e);
                throw new RuntimeException("multilingual-e5-large-instruct initialization failed", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<float[]> embed(String text) {
        return embed(text, "RETRIEVAL_DOCUMENT");
    }

    @Override
    public CompletableFuture<float[]> embed(String text, String taskType) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (session == null || tokenizer == null) {
                    throw new IllegalStateException("ONNX session or tokenizer not initialized");
                }

                // Apply task prefix for queries, use text as-is for documents
                String inputText = text;
                if ("RETRIEVAL_QUERY".equals(taskType)) {
                    inputText = INSTRUCT_PREFIX + text;
                }

                Encoding encoding = tokenizer.encode(inputText);
                long[] inputIds = encoding.getIds();
                long[] attentionMask = encoding.getAttentionMask();

                // Create tensors - batch size of 1
                long[][] inputIdsTensor = new long[1][inputIds.length];
                long[][] attentionMaskTensor = new long[1][attentionMask.length];

                inputIdsTensor[0] = inputIds;
                attentionMaskTensor[0] = attentionMask;

                Map<String, OnnxTensor> inputs = new HashMap<>();
                OnnxTensor inputTensor = OnnxTensor.createTensor(env, inputIdsTensor);
                OnnxTensor attentionTensor = OnnxTensor.createTensor(env, attentionMaskTensor);

                inputs.put("input_ids", inputTensor);
                inputs.put("attention_mask", attentionTensor);

                try (var outputs = session.run(inputs)) {
                    inputTensor.close();
                    attentionTensor.close();

                    if (outputs == null || outputs.size() == 0) {
                        throw new RuntimeException("No output from ONNX model");
                    }

                    // multilingual-e5-large-instruct outputs pre-pooled embeddings at index 0
                    // Similar to BGE-M3, shape: [batch_size, 1024] - already normalized and pooled
                    OnnxTensor embeddingTensor = (OnnxTensor) outputs.get(0);
                    float[][] embeddingOutput = (float[][]) embeddingTensor.getValue();
                    return embeddingOutput[0]; // Return first batch
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to embed text", e);
                throw new RuntimeException("Embedding failed", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<float[]>> embedBatch(List<String> texts, String taskType) {
        if (texts == null || texts.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                if (session == null || tokenizer == null) {
                    throw new IllegalStateException("ONNX session or tokenizer not initialized");
                }

                int batchSize = texts.size();
                logger.info("Starting batch embedding for " + batchSize + " texts");

                // Apply task prefix to queries, use documents as-is
                List<String> processedTexts = new ArrayList<>();
                for (String text : texts) {
                    if ("RETRIEVAL_QUERY".equals(taskType)) {
                        processedTexts.add(INSTRUCT_PREFIX + text);
                    } else {
                        processedTexts.add(text);
                    }
                }

                // Tokenize all texts and find max length for padding
                List<Encoding> encodings = new ArrayList<>();
                int maxLen = 0;
                for (String text : processedTexts) {
                    Encoding enc = tokenizer.encode(text);
                    encodings.add(enc);
                    maxLen = Math.max(maxLen, enc.getIds().length);
                }

                // Create padded tensors for batch
                long[][] inputIdsTensor = new long[batchSize][maxLen];
                long[][] attentionMaskTensor = new long[batchSize][maxLen];

                for (int i = 0; i < batchSize; i++) {
                    Encoding enc = encodings.get(i);
                    long[] ids = enc.getIds();
                    long[] mask = enc.getAttentionMask();

                    // Copy with zero padding
                    System.arraycopy(ids, 0, inputIdsTensor[i], 0, ids.length);
                    System.arraycopy(mask, 0, attentionMaskTensor[i], 0, mask.length);
                }

                Map<String, OnnxTensor> inputs = new HashMap<>();
                OnnxTensor inputTensor = OnnxTensor.createTensor(env, inputIdsTensor);
                OnnxTensor attentionTensor = OnnxTensor.createTensor(env, attentionMaskTensor);

                inputs.put("input_ids", inputTensor);
                inputs.put("attention_mask", attentionTensor);

                try (var outputs = session.run(inputs)) {
                    inputTensor.close();
                    attentionTensor.close();

                    if (outputs == null || outputs.size() == 0) {
                        throw new RuntimeException("No output from ONNX model");
                    }

                    // multilingual-e5-large-instruct outputs pre-pooled embeddings at index 0
                    // Shape: [batch_size, 1024] - already normalized and pooled
                    OnnxTensor embeddingTensor = (OnnxTensor) outputs.get(0);
                    float[][] batchOutput = (float[][]) embeddingTensor.getValue();

                    List<float[]> results = new ArrayList<>();
                    for (int i = 0; i < batchSize; i++) {
                        results.add(batchOutput[i]);
                    }

                    logger.info("Batch embedding completed for " + batchSize + " texts");
                    return results;
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Batch embedding failed", e);
                throw new RuntimeException("Batch embedding failed", e);
            }
        }, executor);
    }

    @Override
    public int getDimension() {
        return EMBEDDING_DIM;
    }

    @Override
    public void shutdown() {
        if (downloader != null) {
            downloader.shutdown();
        }
        if (session != null) {
            try {
                session.close();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to close ONNX session", e);
            }
        }
        if (env != null) {
            env.close();
        }
        if (tokenizer != null) {
            tokenizer.close();
        }
        executor.shutdown();
    }
}
