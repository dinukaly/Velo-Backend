package com.dinukaly.velo.service;

import java.util.List;
import java.util.Optional;

/**
 * Contract for generating dense vector embeddings from text.
 *
 * Implementations plug in a specific embedding provider (Gemini, OpenAI, Ollama, Mock).
 * The rest of the codebase calls only this interface — switching providers requires
 * only a property change, not code changes.
 */
public interface EmbeddingProviderService {

    /**
     * Generates a dense embedding vector for a single text input.
     *
     * @param text The text to embed (e.g. a code chunk's content)
     * @return Optional containing the float[] embedding vector, or empty if unavailable/disabled
     */
    Optional<float[]> embed(String text);

    /**
     * Returns the number of vector dimensions this provider produces.
     * Must match the Elasticsearch dense_vector field mapping.
     */
    int getDimensions();

    /**
     * Returns true if the embedding provider is currently enabled and available.
     */
    boolean isEnabled();
}
