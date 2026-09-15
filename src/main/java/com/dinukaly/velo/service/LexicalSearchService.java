package com.dinukaly.velo.service;

import com.dinukaly.velo.dto.agent.CodeSearchResultDTO;

import java.util.UUID;

/**
 * Service for executing Elasticsearch BM25 lexical code search with fallback handling.
 */
public interface LexicalSearchService {

    /**
     * Executes BM25 lexical search for code chunks in Elasticsearch.
     * Falls back to filesystem search if Elasticsearch is unreachable.
     *
     * @param projectId Project UUID
     * @param query     Keyword query string
     * @param userEmail Authenticated user email
     * @return CodeSearchResultDTO containing matches
     */
    CodeSearchResultDTO searchCode(UUID projectId, String query, String userEmail);
}
