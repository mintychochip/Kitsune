package org.aincraft.kitsune.embedding.download;

import java.util.Map;
import java.util.Optional;

/**
 * Registry of known ONNX models with their Hugging Face locations.
 */
public class ModelRegistry {
    private static final Map<String, ModelSpec> KNOWN_MODELS = Map.of(
        "nomic-embed-text-v1.5", new ModelSpec(
            "nomic-embed-text-v1.5",
            "nomic-ai/nomic-embed-text-v1.5",
            "onnx/model.onnx",
            "tokenizer.json",
            768
        )
    );

    public static Optional<ModelSpec> getSpec(String modelName) {
        return Optional.ofNullable(KNOWN_MODELS.get(modelName));
    }

    public static ModelSpec fromCustomConfig(String modelName, String repo, String modelPath, String tokenizerPath, int dimension) {
        return new ModelSpec(modelName, repo, modelPath, tokenizerPath, dimension);
    }
}
