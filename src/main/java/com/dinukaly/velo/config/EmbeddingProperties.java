package com.dinukaly.velo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binding class for all {@code velo.ai.embedding.*} properties.
 *
 * Allows switching the embedding provider and model
 */
@Configuration
@ConfigurationProperties(prefix = "velo.ai.embedding")
@Data
public class EmbeddingProperties {

    /** Enable or disable vector embedding generation during indexing. */
    private boolean enabled = true;

    /**
     * Active provider name. Used for logging and conditional bean creation.
     * Supported values: gemini, openai, ollama, mock
     */
    private String provider = "gemini";

    /**
     * Base URL of the OpenAI-compatible embedding endpoint.
     * Gemini: https://generativelanguage.googleapis.com/v1beta/openai
     * OpenAI: https://api.openai.com/v1
     * Ollama:  http://localhost:11434/v1
     */
    private String baseUrl;

    /** API key for the embedding provider. */
    private String apiKey;

    /**
     * Embedding model name to use.
     * Gemini: text-embedding-004
     * OpenAI: text-embedding-3-small
     * Ollama:  nomic-embed-text
     */
    private String model = "text-embedding-004";

    /**
     * Number of vector dimensions produced by the configured model.
     * Gemini text-embedding-004: 768
     * OpenAI text-embedding-3-small: 1536
     * Ollama nomic-embed-text: 768
     */
    private int dimensions = 768;
}
