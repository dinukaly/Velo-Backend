package com.dinukaly.velo.service;

import com.dinukaly.velo.dto.agent.CodeSearchResultDTO;

import java.util.UUID;

/**
 * Hybrid search service that combines BM25 lexical search and
 * dense-vector semantic similarity via Reciprocal Rank Fusion (RRF).
 *
 * When the embedding provider is unavailable, automatically falls
 * back to pure BM25 search so queries always succeed.
 */
public interface HybridSearchService {

    /**
     * Executes hybrid search over code chunks for the given project.
     *
     * @param projectId Project UUID
     * @param query     Natural language or keyword query
     * @param topK      Maximum number of results to return
     * @param userEmail Authenticated user email
     * @return Fused and ranked CodeSearchResultDTO
     */
    CodeSearchResultDTO search(UUID projectId, String query, int topK, String userEmail);
}
