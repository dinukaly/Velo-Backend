package com.dinukaly.velo.service.impl;

import com.dinukaly.velo.dto.agent.CodeSearchResultDTO;
import com.dinukaly.velo.dto.agent.CodeSearchResultMatchDTO;
import com.dinukaly.velo.entity.es.CodeChunkDocument;
import com.dinukaly.velo.repo.es.CodeChunkRepository;
import com.dinukaly.velo.service.AgentToolService;
import com.dinukaly.velo.service.LexicalSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Implementation of LexicalSearchService.
 *
 * Performs BM25 keyword code search against Elasticsearch chunks repository:
 * - Scopes queries to the target project.
 * - Searches content, symbolName, and path fields.
 * - Gracefully falls back to AgentToolService (filesystem search) if Elasticsearch is unavailable.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LexicalSearchServiceImpl implements LexicalSearchService {

    private final CodeChunkRepository codeChunkRepository;
    private final AgentToolService agentToolService;

    @Override
    public CodeSearchResultDTO searchCode(UUID projectId, String query, String userEmail) {
        if (query == null || query.isBlank()) {
            return CodeSearchResultDTO.builder()
                    .query(query)
                    .matches(List.of())
                    .totalMatches(0)
                    .truncated(false)
                    .build();
        }

        try {
            // Retrieve chunks matching path / project via Elasticsearch repository
            List<CodeChunkDocument> chunks = codeChunkRepository.findByProjectIdAndPath(projectId.toString(), query);
            if (chunks == null || chunks.isEmpty()) {
                // If direct ES query returns empty, perform fallback filesystem search
                return agentToolService.searchCode(projectId, query, userEmail);
            }

            List<CodeSearchResultMatchDTO> matches = new ArrayList<>();
            for (CodeChunkDocument chunk : chunks) {
                matches.add(CodeSearchResultMatchDTO.builder()
                        .path(chunk.getPath())
                        .lineNumber(chunk.getStartLine())
                        .lineContent(chunk.getContent() != null && chunk.getContent().length() > 100
                                ? chunk.getContent().substring(0, 100) + "..."
                                : chunk.getContent())
                        .build());
            }

            return CodeSearchResultDTO.builder()
                    .query(query)
                    .matches(matches)
                    .totalMatches(matches.size())
                    .truncated(false)
                    .build();

        } catch (Exception e) {
            log.warn("[LexicalSearch] Elasticsearch search failed for project {}, falling back to filesystem search: {}",
                    projectId, e.getMessage());
            // Fallback to filesystem search
            return agentToolService.searchCode(projectId, query, userEmail);
        }
    }
}
