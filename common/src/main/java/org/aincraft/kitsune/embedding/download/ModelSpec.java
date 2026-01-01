package org.aincraft.kitsune.embedding.download;

/**
 * Specification for an ONNX model including its Hugging Face location.
 */
public record ModelSpec(
    String modelName,        // e.g., "nomic-embed-text-v1.5"
    String huggingFaceRepo,  // e.g., "nomic-ai/nomic-embed-text-v1.5"
    String modelPath,        // e.g., "onnx/model.onnx"
    String tokenizerPath,    // e.g., "tokenizer.json"
    int dimension            // e.g., 768
) {}
