package org.aincraft.kitsune.embedding;

import java.util.concurrent.CompletableFuture;

public interface EmbeddingService {
    /**
     * Embeds text into a vector representation asynchronously.
     * @param text The text to embed
     * @return CompletableFuture containing the embedding vector
     */
    CompletableFuture<float[]> embed(String text);

    /**
     * Embeds text into a vector representation with specified task type.
     * For services that support task type specification (e.g., Google Gemini).
     * @param text The text to embed
     * @param taskType The task type for embedding (e.g., "RETRIEVAL_DOCUMENT", "RETRIEVAL_QUERY")
     * @return CompletableFuture containing the embedding vector
     */
    CompletableFuture<float[]> embed(String text, String taskType);

    /**
     * Initializes the embedding service.
     * @return CompletableFuture that completes when initialization is done
     */
    CompletableFuture<Void> initialize();

    /**
     * Shuts down the embedding service and releases resources.
     */
    void shutdown();
}
