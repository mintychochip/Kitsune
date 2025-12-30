package org.aincraft.chestfind.embedding;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OnnxTensor;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;

public class OnnxEmbeddingService implements EmbeddingService {
    private final JavaPlugin plugin;
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
    private OrtEnvironment env;
    private OrtSession session;
    private HuggingFaceTokenizer tokenizer;

    private static final String MODEL_NAME = "all-MiniLM-L6-v2";
    private static final int EMBEDDING_DIM = 384;
    private static final int MAX_SEQUENCE_LENGTH = 512;

    public OnnxEmbeddingService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                env = OrtEnvironment.getEnvironment();
                Path modelPath = Paths.get(plugin.getDataFolder().getAbsolutePath(), "models", MODEL_NAME + ".onnx");
                Path vocabPath = Paths.get(plugin.getDataFolder().getAbsolutePath(), "models", "vocab.txt");

                if (!Files.exists(modelPath)) {
                    plugin.getLogger().warning("ONNX model not found at " + modelPath);
                    plugin.getLogger().warning("Please download " + MODEL_NAME + ".onnx and place it in plugins/ChestFind/models/");
                    throw new IllegalStateException("ONNX model not found");
                }

                // Initialize ONNX session
                session = env.createSession(modelPath.toString());

                // Try to initialize tokenizer from tokenizer.json (preferred for all-MiniLM)
                Path tokenizerJsonPath = Paths.get(plugin.getDataFolder().getAbsolutePath(), "models", "tokenizer.json");
                if (Files.exists(tokenizerJsonPath)) {
                    tokenizer = HuggingFaceTokenizer.newInstance(tokenizerJsonPath);
                    plugin.getLogger().info("Loaded tokenizer from tokenizer.json");
                } else if (Files.exists(vocabPath)) {
                    // Fallback to vocab.txt (legacy BERT tokenization)
                    Map<String, String> options = new HashMap<>();
                    options.put("modelMaxLength", String.valueOf(MAX_SEQUENCE_LENGTH));
                    options.put("addSpecialTokens", "true");
                    options.put("padding", "false");
                    options.put("truncation", "true");
                    tokenizer = HuggingFaceTokenizer.newInstance(vocabPath, options);
                    plugin.getLogger().info("Loaded tokenizer from vocab.txt");
                } else {
                    plugin.getLogger().warning("No tokenizer found. Please provide either:");
                    plugin.getLogger().warning("  - tokenizer.json (recommended for all-MiniLM-L6-v2)");
                    plugin.getLogger().warning("  - vocab.txt (legacy BERT tokenization)");
                    throw new IllegalStateException("Tokenizer not found");
                }

                plugin.getLogger().info("ONNX embedding service initialized with " + MODEL_NAME);
                return null;
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to initialize ONNX session", e);
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
                plugin.getLogger().log(Level.WARNING, "Failed to embed text", e);
                throw new RuntimeException("Embedding failed", e);
            }
        }, executor);
    }

    private float[] meanPooling(float[][][] lastHiddenState, long[] attentionMask) {
        float[] result = new float[EMBEDDING_DIM];
        float sumMask = 0;

        // Mean pooling weighted by attention mask
        for (int i = 0; i < lastHiddenState[0].length && i < attentionMask.length; i++) {
            if (attentionMask[i] == 1) {
                for (int j = 0; j < EMBEDDING_DIM && j < lastHiddenState[0][i].length; j++) {
                    result[j] += lastHiddenState[0][i][j];
                }
                sumMask += 1.0f;
            }
        }

        // Average
        if (sumMask > 0) {
            for (int i = 0; i < EMBEDDING_DIM; i++) {
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
        if (session != null) {
            try {
                session.close();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to close ONNX session", e);
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
