package org.aincraft.kitsune.embedding;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.aincraft.kitsune.config.KitsuneConfig;
import org.aincraft.kitsune.logging.ChestFindLogger;

public class OpenAIEmbeddingService implements EmbeddingService {
    private final ChestFindLogger logger;
    private final KitsuneConfig config;
    private final OkHttpClient httpClient;
    private final Gson gson = new Gson();
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/embeddings";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    public OpenAIEmbeddingService(ChestFindLogger logger, KitsuneConfig config) {
        this.logger = logger;
        this.config = config;
        this.httpClient = new OkHttpClient();
    }

    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            String apiKey = config.getOpenAIApiKey();
            if (apiKey == null || apiKey.isEmpty()) {
                throw new IllegalStateException("OpenAI API key not configured");
            }
            logger.info("OpenAI embedding service initialized with model: " + config.getOpenAIModel());
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
                String apiKey = config.getOpenAIApiKey();

                JsonObject body = new JsonObject();
                body.addProperty("input", text);
                body.addProperty("model", config.getOpenAIModel());

                RequestBody requestBody = RequestBody.create(gson.toJson(body), JSON);

                Request request = new Request.Builder()
                    .url(OPENAI_API_URL)
                    .post(requestBody)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new RuntimeException("OpenAI API error: " + response.code() + " " + response.message());
                    }

                    String responseBody = response.body().string();
                    JsonObject responseJson = gson.fromJson(responseBody, JsonObject.class);

                    JsonArray data = responseJson.getAsJsonArray("data");
                    if (data == null || data.size() == 0) {
                        throw new RuntimeException("No embedding data in response");
                    }

                    JsonObject embedding = data.get(0).getAsJsonObject();
                    JsonArray embeddingArray = embedding.getAsJsonArray("embedding");

                    float[] result = new float[embeddingArray.size()];
                    for (int i = 0; i < embeddingArray.size(); i++) {
                        result[i] = embeddingArray.get(i).getAsFloat();
                    }

                    return result;
                }
            } catch (Exception e) {
                logger.log(ChestFindLogger.LogLevel.WARNING, "Failed to embed text with OpenAI", e);
                throw new RuntimeException("OpenAI embedding failed", e);
            }
        }, executor);
    }

    @Override
    public void shutdown() {
        httpClient.dispatcher().executorService().shutdown();
        executor.shutdown();
    }
}
