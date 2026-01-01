package org.aincraft.kitsune.embedding;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import okhttp3.OkHttpClient;
import org.aincraft.kitsune.config.KitsuneConfig;
import org.aincraft.kitsune.embedding.download.*;
import org.aincraft.kitsune.KitsunePlatform;

public class OnnxEmbeddingService implements EmbeddingService {
    private final Logger logger;
    private final KitsunePlatform dataFolderProvider;
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
    private OrtEnvironment env;
    private OrtSession session;
    private HuggingFaceTokenizer tokenizer;

    private final String modelName;
    private final int embeddingDim;
    private static final int MAX_SEQUENCE_LENGTH = 512;

    private final KitsuneConfig config;
    private final boolean autoDownloadEnabled;
    private OkHttpClient httpClient;
    private HuggingFaceModelDownloader downloader;

    public OnnxEmbeddingService(KitsuneConfig config, Logger logger, KitsunePlatform dataFolderProvider) {
        this.logger = logger;
        this.dataFolderProvider = dataFolderProvider;
        this.config = config;
        this.modelName = config.getOnnxModel();
        this.embeddingDim = config.getEmbeddingDimension();
        this.autoDownloadEnabled = config.isOnnxAutoDownloadEnabled();
    }

    @Override
    public CompletableFuture<Void> initialize() {
        return ensureModelAvailable()
            .thenCompose(v -> doInitialize());
    }

    private CompletableFuture<Void> ensureModelAvailable() {
        Path dataFolder = dataFolderProvider.getDataFolder();
        Path modelsDir = dataFolder.resolve("models");
        Path modelPath = modelsDir.resolve(modelName + ".onnx");
        Path tokenizerPath = modelsDir.resolve("tokenizer.json");

        // If both files exist, skip download
        if (Files.exists(modelPath) && Files.exists(tokenizerPath)) {
            logger.info("ONNX model and tokenizer found locally");
            return CompletableFuture.completedFuture(null);
        }

        // If auto-download disabled, fail
        if (!autoDownloadEnabled) {
            logger.warning("Model files missing and auto-download is disabled");
            return CompletableFuture.failedFuture(
                new IllegalStateException("ONNX model not found and auto-download is disabled"));
        }

        // Get model spec from registry or config
        ModelSpec spec = getModelSpec();
        if (spec == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Unknown model: " + modelName + ". Configure repository in config."));
        }

        // Initialize downloader and download
        logger.info("Model files missing, downloading from Hugging Face...");
        initializeDownloader();
        DownloadProgressListener listener = new ConsoleProgressReporter(logger);
        return downloader.downloadModel(spec, modelsDir, listener);
    }

    private ModelSpec getModelSpec() {
        // Check if custom repository is configured
        String customRepo = config.getOnnxRepository();
        if (customRepo != null && !customRepo.isEmpty()) {
            String modelPath = config.getOnnxModelPath();
            String tokenizerPath = config.getOnnxTokenizerPath();
            if (modelPath.isEmpty()) modelPath = "onnx/model.onnx";
            if (tokenizerPath.isEmpty()) tokenizerPath = "tokenizer.json";
            return ModelRegistry.fromCustomConfig(modelName, customRepo, modelPath, tokenizerPath, embeddingDim);
        }
        // Lookup from registry
        return ModelRegistry.getSpec(modelName).orElse(null);
    }

    private void initializeDownloader() {
        httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(config.getDownloadTimeoutSeconds(), TimeUnit.SECONDS)
            .build();
        downloader = new HuggingFaceModelDownloader(httpClient, logger, config.getDownloadRetries());
    }

    private CompletableFuture<Void> doInitialize() {
        // Move the existing initialize() logic here (lines 41-84)
        return CompletableFuture.supplyAsync(() -> {
            try {
                env = OrtEnvironment.getEnvironment();
                Path dataFolder = dataFolderProvider.getDataFolder();
                Path modelPath = dataFolder.resolve("models").resolve(modelName + ".onnx");
                Path vocabPath = dataFolder.resolve("models").resolve("vocab.txt");

                if (!Files.exists(modelPath)) {
                    logger.warning("ONNX model not found at " + modelPath);
                    throw new IllegalStateException("ONNX model not found");
                }

                session = env.createSession(modelPath.toString());

                Path tokenizerJsonPath = dataFolder.resolve("models").resolve("tokenizer.json");
                if (Files.exists(tokenizerJsonPath)) {
                    tokenizer = HuggingFaceTokenizer.newInstance(tokenizerJsonPath);
                    logger.info("Loaded tokenizer from tokenizer.json");
                } else if (Files.exists(vocabPath)) {
                    Map<String, String> options = new HashMap<>();
                    options.put("modelMaxLength", String.valueOf(MAX_SEQUENCE_LENGTH));
                    options.put("addSpecialTokens", "true");
                    options.put("padding", "false");
                    options.put("truncation", "true");
                    tokenizer = HuggingFaceTokenizer.newInstance(vocabPath, options);
                    logger.info("Loaded tokenizer from vocab.txt");
                } else {
                    logger.warning("No tokenizer found");
                    throw new IllegalStateException("Tokenizer not found");
                }

                logger.info("ONNX embedding service initialized with " + modelName + " (" + embeddingDim + " dimensions)");
                return null;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to initialize ONNX session", e);
                throw new RuntimeException("ONNX initialization failed", e);
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

                // Tokenize text
                Encoding encoding = tokenizer.encode(text);
                long[] inputIds = encoding.getIds();
                long[] attentionMask = encoding.getAttentionMask();

                // Create tensors
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

                    if (outputs == null) {
                        throw new RuntimeException("No output from ONNX model");
                    }

                    var lastHiddenStateOpt = outputs.get("last_hidden_state");
                    if (lastHiddenStateOpt.isEmpty()) {
                        throw new RuntimeException("No last_hidden_state in output");
                    }

                    OnnxTensor lastHiddenState = (OnnxTensor) lastHiddenStateOpt.get();
                    float[] embedding = meanPooling((float[][][]) lastHiddenState.getValue(), attentionMask);

                    return embedding;
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to embed text", e);
                throw new RuntimeException("Embedding failed", e);
            }
        }, executor);
    }

    private float[] meanPooling(float[][][] lastHiddenState, long[] attentionMask) {
        float[] result = new float[embeddingDim];
        float sumMask = 0;

        // Mean pooling weighted by attention mask
        for (int i = 0; i < lastHiddenState[0].length && i < attentionMask.length; i++) {
            if (attentionMask[i] == 1) {
                for (int j = 0; j < embeddingDim && j < lastHiddenState[0][i].length; j++) {
                    result[j] += lastHiddenState[0][i][j];
                }
                sumMask += 1.0f;
            }
        }

        // Average
        if (sumMask > 0) {
            for (int i = 0; i < embeddingDim; i++) {
                result[i] /= sumMask;
            }
        }

        // L2 normalization
        normalizeEmbedding(result);
        return result;
    }

    private void normalizeEmbedding(float[] embedding) {
        float norm = 0;
        for (float v : embedding) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(norm);

        if (norm > 0) {
            for (int i = 0; i < embedding.length; i++) {
                embedding[i] /= norm;
            }
        }
    }

    @Override
    public void shutdown() {
        if (downloader != null) {
            downloader.shutdown();
        }
        if (httpClient != null) {
            httpClient.dispatcher().executorService().shutdown();
            httpClient.connectionPool().evictAll();
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
