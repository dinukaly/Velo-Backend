package com.dinukaly.velo.service;

import com.dinukaly.velo.entity.es.CodeChunkDocument;

import java.util.List;
import java.util.UUID;

/**
 * Service for chunking source code files into code-aware document chunks for indexing.
 */
public interface CodeChunkerService {

    /**
     * Chunks a file's content into CodeChunkDocument instances.
     *
     * @param projectId    Project UUID
     * @param relativePath Relative workspace path of the file
     * @param fileContent  Full string content of the file
     * @return List of prepared CodeChunkDocument objects
     */
    List<CodeChunkDocument> chunkFile(UUID projectId, String relativePath, String fileContent);

    /**
     * Computes SHA-256 hex string for a given text payload.
     */
    String computeSha256(String input);
}
