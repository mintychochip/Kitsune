package org.aincraft.kitsune.embedding;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.aincraft.kitsune.logging.ChestFindLogger;

public class GoogleEmbeddingService implements EmbeddingService {
    private final ChestFindLogger logger;
    private final String apiKey;
    private final String model;
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
    private final OkHttpClient httpClient;
    private final Gson gson = new Gson();

    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/%s:embedContent";

    public GoogleEmbeddingService(ChestFindLogger logger, String apiKey, String model) {
        this.logger = logger;
        this.apiKey = apiKey;
        // Use the stable model by default
        this.model = model != null && !model.isEmpty() ? model : "gemini-embedding-001";
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            if (apiKey == null || apiKey.isEmpty() || apiKey.equals("your-api-key")) {
                logger.warning("Google API key not configured!");
                logger.warning("Get an API key from https://aistudio.google.com/app/apikey");
                throw new IllegalStateException("Google API key not configured");
            }
            logger.info("Google embedding service initialized with model: " + model);
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
                // Log task type and text being embedded
                int previewLen = Math.min(150, text.length());
                logger.info("Embedding with taskType=" + taskType + ", text=" +
                    text.substring(0, previewLen) + (previewLen < text.length() ? "..." : ""));

                // Build request body per Gemini API spec
                JsonObject parts = new JsonObject();
                parts.addProperty("text", text);

                JsonArray partsArray = new JsonArray();
                partsArray.add(parts);

                JsonObject content = new JsonObject();
                content.add("parts", partsArray);

                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("model", "models/" + model);
                requestBody.add("content", content);
                requestBody.addProperty("taskType", taskType);

                String url = String.format(GEMINI_API_URL, model);

                Request request = new Request.Builder()
                        .url(url)
                        .header("x-goog-api-key", apiKey)
                        .header("Content-Type", "application/json")
                        .post(RequestBody.create(
                                gson.toJson(requestBody),
                                MediaType.parse("application/json")))
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "No error body";
                        logger.warning("Google API error: " + response.code() + " - " + errorBody);
                        throw new RuntimeException("Google API error: " + response.code());
                    }

                    String responseBody = response.body().string();
                    JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);

                    JsonObject embedding = jsonResponse.getAsJsonObject("embedding");
                    JsonArray values = embedding.getAsJsonArray("values");

                    float[] result = new float[values.size()];
                    for (int i = 0; i < values.size(); i++) {
                        result[i] = values.get(i).getAsFloat();
                    }

                    // Log response vector length
                    logger.info("Embedding response: vector size=" + result.length);

                    // L2 normalize the embedding
                    normalizeEmbedding(result);
                    return result;
                }
            } catch (IOException e) {
                logger.log(ChestFindLogger.LogLevel.WARNING, "Failed to call Google API", e);
                throw new RuntimeException("Embedding failed", e);
            }
        }, executor);
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
        executor.shutdown();
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
}
