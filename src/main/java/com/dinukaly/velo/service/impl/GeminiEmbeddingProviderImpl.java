package com.dinukaly.velo.service.impl;

import com.dinukaly.velo.config.EmbeddingProperties;
import com.dinukaly.velo.service.EmbeddingProviderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Implementation of EmbeddingProviderService using the Google Gemini embedding API.
 *
 * Gemini provides an OpenAI-compatible REST endpoint, so we reuse Spring AI's
 * {@link OpenAiEmbeddingModel} configured to point at the Gemini base URL.
 *
 * Configuration (application.properties):
 * <pre>
 *   velo.ai.embedding.base-url=https://generativelanguage.googleapis.com/v1beta/openai
 *   velo.ai.embedding.api-key=YOUR_GEMINI_API_KEY
 *   velo.ai.embedding.model=text-embedding-004
 *   velo.ai.embedding.dimensions=768
 * </pre>
 *
 * On any failure (network error, invalid API key, rate limit), the method returns
 * {@link Optional#empty()} so the indexer can continue without breaking.
 */
@Service
@Slf4j
public class GeminiEmbeddingProviderImpl implements EmbeddingProviderService {

    private final EmbeddingProperties properties;

    /**
     * Lazily created Spring AI embedding model client.
     * Volatile ensures visibility across threads on first init.
     */
    private volatile OpenAiEmbeddingModel embeddingModel;

    /** Tracks whether a fatal config error occurred, to skip repeated retries. */
    private volatile boolean providerFailed = false;

    public GeminiEmbeddingProviderImpl(EmbeddingProperties properties) {
        this.properties = properties;
    }

    /**
     * Generates an embedding vector for the given text via the Gemini embedding endpoint.
     * Returns {@link Optional#empty()} if embedding is disabled, the provider has failed,
     * or a transient API error occurs.
     */
    @Override
    public Optional<float[]> embed(String text) {
        if (!properties.isEnabled() || providerFailed) {
            return Optional.empty();
        }

        if (text == null || text.isBlank()) {
            return Optional.empty();
        }

        try {
            OpenAiEmbeddingModel model = getOrCreateModel();
            var response = model.embedForResponse(java.util.List.of(text));

            if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
                log.warn("[Embedding] Empty response from Gemini embedding API");
                return Optional.empty();
            }

            float[] vector = response.getResults().get(0).getOutput();
            return Optional.of(vector);

        } catch (Exception e) {
            log.warn("[Embedding] Embedding call failed, falling back to BM25-only indexing: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public int getDimensions() {
        return properties.getDimensions();
    }

    @Override
    public boolean isEnabled() {
        return properties.isEnabled() && !providerFailed;
    }

    /**
     * Lazily initialises the Spring AI OpenAiEmbeddingModel pointing at the
     * Gemini endpoint. Uses double-checked locking for thread safety.
     *
     * Spring AI 2.0.0-M1 constructor signature:
     * {@code OpenAiEmbeddingModel(OpenAiApi, MetadataMode, OpenAiEmbeddingOptions)}
     */
    private OpenAiEmbeddingModel getOrCreateModel() {
        if (embeddingModel == null) {
            synchronized (this) {
                if (embeddingModel == null) {
                    try {
                        log.info("[Embedding] Initialising '{}' embedding model via provider '{}' at {}",
                                properties.getModel(), properties.getProvider(), properties.getBaseUrl());

                        // Build the OpenAI-compatible API client pointed at Gemini's endpoint
                        var openAiApi = OpenAiApi.builder()
                                .baseUrl(properties.getBaseUrl())
                                .apiKey(properties.getApiKey())
                                .build();

                        // Build the embedding options (model name + output dimensions)
                        var options = OpenAiEmbeddingOptions.builder()
                                .model(properties.getModel())
                                .dimensions(properties.getDimensions())
                                .build();

                        // MetadataMode.EMBED tells Spring AI to include content in the embedding request
                        embeddingModel = new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED, options);

                    } catch (Exception e) {
                        log.error("[Embedding] Failed to initialise embedding model — vector search disabled: {}", e.getMessage());
                        providerFailed = true;
                        throw e;
                    }
                }
            }
        }
        return embeddingModel;
    }
}
