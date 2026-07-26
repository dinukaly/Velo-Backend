package com.dinukaly.velo.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.dinukaly.velo.dto.agent.CodeSearchResultDTO;
import com.dinukaly.velo.dto.agent.CodeSearchResultMatchDTO;
import com.dinukaly.velo.entity.es.CodeChunkDocument;
import com.dinukaly.velo.service.AgentToolService;
import com.dinukaly.velo.service.EmbeddingProviderService;
import com.dinukaly.velo.service.HybridSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Implementation of HybridSearchService.
 *
 * Combines two result sets using Reciprocal Rank Fusion (RRF):
 * <ol>
 *   <li>BM25 lexical search via Elasticsearch match query on the 'content' field.</li>
 *   <li>k-NN dense-vector similarity search using the query's embedding vector.</li>
 * </ol>
 *
 * RRF formula: RRF(d) = Σ 1 / (k + rank(d))   where k=60 (standard constant).
 *
 * Fallback hierarchy:
 * <ol>
 *   <li>Hybrid (BM25 + vector) — when both ES and embedding are available.</li>
 *   <li>BM25 only — when ES is available but embedding provider fails.</li>
 *   <li>Filesystem search — when Elasticsearch itself is unavailable.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HybridSearchServiceImpl implements HybridSearchService {

    /** RRF ranking constant. 60 is the recommended default value. */
    private static final int RRF_K = 60;

    /** Maximum candidate results fetched per individual BM25 or vector search. */
    private static final int CANDIDATE_POOL_SIZE = 20;

    /** Name of the Elasticsearch index containing code chunks. */
    private static final String INDEX_NAME = "velo-code-chunks-v1";

    private final ElasticsearchClient elasticsearchClient;
    private final EmbeddingProviderService embeddingProviderService;
    private final AgentToolService agentToolService;

    @Override
    public CodeSearchResultDTO search(UUID projectId, String query, int topK, String userEmail) {
        if (query == null || query.isBlank()) {
            return emptyResult(query);
        }

        try {
            // Step 1: Execute BM25 lexical search
            List<ScoredChunk> bm25Results = runBm25Search(projectId.toString(), query);

            // Step 2: Optionally execute vector search if embedding is available
            List<ScoredChunk> vectorResults = List.of();
            Optional<float[]> queryVector = embeddingProviderService.embed(query);
            if (queryVector.isPresent()) {
                vectorResults = runVectorSearch(projectId.toString(), queryVector.get(), CANDIDATE_POOL_SIZE);
            } else {
                log.debug("[HybridSearch] Embedding unavailable — using BM25 only for project {}", projectId);
            }

            // Step 3: Fuse results via RRF
            List<ScoredChunk> fused = reciprocalRankFusion(bm25Results, vectorResults, topK);

            // Step 4: Map to response DTO
            List<CodeSearchResultMatchDTO> matches = fused.stream()
                    .map(sc -> CodeSearchResultMatchDTO.builder()
                            .path(sc.doc.getPath())
                            .lineNumber(sc.doc.getStartLine())
                            .lineContent(truncateContent(sc.doc.getContent(), 120))
                            .build())
                    .toList();

            return CodeSearchResultDTO.builder()
                    .query(query)
                    .matches(matches)
                    .totalMatches(matches.size())
                    .truncated(matches.size() >= topK)
                    .build();

        } catch (Exception e) {
            log.warn("[HybridSearch] Elasticsearch unavailable, falling back to filesystem search: {}", e.getMessage());
            // Final fallback: filesystem keyword search
            return agentToolService.searchCode(projectId, query, userEmail);
        }
    }

    // -------------------------------------------------------------------------
    // BM25 Lexical Search
    // -------------------------------------------------------------------------

    /**
     * Runs an Elasticsearch BM25 match query on content, symbolName, and path fields.
     */
    private List<ScoredChunk> runBm25Search(String projectId, String query) {
        try {
            SearchResponse<CodeChunkDocument> response = elasticsearchClient.search(s -> s
                    .index(INDEX_NAME)
                    .size(CANDIDATE_POOL_SIZE)
                    .query(q -> q
                            .bool(b -> b
                                    .must(m -> m.term(t -> t.field("projectId").value(projectId)))
                                    .should(sh -> sh.match(ma -> ma.field("content").query(query).boost(1.5f)))
                                    .should(sh -> sh.match(ma -> ma.field("symbolName").query(query).boost(2.0f)))
                                    .should(sh -> sh.match(ma -> ma.field("path").query(query).boost(1.0f)))
                                    .minimumShouldMatch("1")
                            )
                    ),
                    CodeChunkDocument.class
            );

            List<ScoredChunk> results = new ArrayList<>();
            for (Hit<CodeChunkDocument> hit : response.hits().hits()) {
                if (hit.source() != null) {
                    results.add(new ScoredChunk(hit.source(), hit.score() != null ? hit.score() : 0.0));
                }
            }
            return results;

        } catch (Exception e) {
            log.warn("[HybridSearch] BM25 search failed: {}", e.getMessage());
            return List.of();
        }
    }

    // -------------------------------------------------------------------------
    // Vector Similarity Search (k-NN)
    // -------------------------------------------------------------------------

    /**
     * Runs Elasticsearch k-NN approximate nearest-neighbour search on the embedding field.
     */
    private List<ScoredChunk> runVectorSearch(String projectId, float[] queryVector, int k) {
        try {
            SearchResponse<CodeChunkDocument> response = elasticsearchClient.search(s -> s
                    .index(INDEX_NAME)
                    .size(k)
                    .knn(knn -> knn
                            .field("embedding")
                            .queryVector(toFloatList(queryVector))
                            .k(k)
                            .numCandidates(k * 3)
                            .filter(f -> f.term(t -> t.field("projectId").value(projectId)))
                    ),
                    CodeChunkDocument.class
            );

            List<ScoredChunk> results = new ArrayList<>();
            for (Hit<CodeChunkDocument> hit : response.hits().hits()) {
                if (hit.source() != null) {
                    results.add(new ScoredChunk(hit.source(), hit.score() != null ? hit.score() : 0.0));
                }
            }
            return results;

        } catch (Exception e) {
            log.warn("[HybridSearch] Vector (kNN) search failed: {}", e.getMessage());
            return List.of();
        }
    }

    // -------------------------------------------------------------------------
    // Reciprocal Rank Fusion
    // -------------------------------------------------------------------------

    /**
     * Fuses BM25 and vector result lists into a single ranked list using RRF.
     *
     * RRF(d) = Σ 1 / (k + rank(d)) for each result set where d appears.
     */
    private List<ScoredChunk> reciprocalRankFusion(
            List<ScoredChunk> bm25Results,
            List<ScoredChunk> vectorResults,
            int topK) {

        Map<String, Double> rrfScores = new LinkedHashMap<>();
        Map<String, CodeChunkDocument> docById = new LinkedHashMap<>();

        // Score BM25 hits (rank is 1-indexed)
        for (int i = 0; i < bm25Results.size(); i++) {
            ScoredChunk sc = bm25Results.get(i);
            String docId = sc.doc.getId();
            double rrfScore = 1.0 / (RRF_K + i + 1);
            rrfScores.merge(docId, rrfScore, Double::sum);
            docById.putIfAbsent(docId, sc.doc);
        }

        // Score vector hits and add to same map
        for (int i = 0; i < vectorResults.size(); i++) {
            ScoredChunk sc = vectorResults.get(i);
            String docId = sc.doc.getId();
            double rrfScore = 1.0 / (RRF_K + i + 1);
            rrfScores.merge(docId, rrfScore, Double::sum);
            docById.putIfAbsent(docId, sc.doc);
        }

        // Sort by descending RRF score, take topK
        return rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> new ScoredChunk(docById.get(e.getKey()), e.getValue()))
                .toList();
    }

    // -------------------------------------------------------------------------
    // Private Helpers
    // -------------------------------------------------------------------------

    private List<Float> toFloatList(float[] arr) {
        List<Float> list = new ArrayList<>(arr.length);
        for (float f : arr) list.add(f);
        return list;
    }

    private String truncateContent(String content, int maxLen) {
        if (content == null) return "";
        return content.length() > maxLen ? content.substring(0, maxLen) + "…" : content;
    }

    private CodeSearchResultDTO emptyResult(String query) {
        return CodeSearchResultDTO.builder()
                .query(query)
                .matches(List.of())
                .totalMatches(0)
                .truncated(false)
                .build();
    }

    /** Lightweight tuple holding a document and its score for internal ranking logic. */
    private record ScoredChunk(CodeChunkDocument doc, double score) {}
}
