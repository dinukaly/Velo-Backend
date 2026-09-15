package com.dinukaly.velo.service;

import com.dinukaly.velo.dto.agent.ProjectIndexStatusDTO;

import java.util.UUID;

/**
 * Service for managing Elasticsearch index lifecycle, project indexing, and status tracking.
 */
public interface IndexManagementService {

    /**
     * Triggers a full, incremental, or repair index operation for a project.
     *
     * @param projectId Project UUID
     * @param mode      Indexing mode: "FULL", "INCREMENTAL", or "REPAIR"
     * @param userEmail Authenticated user email
     * @return Updated project index status DTO
     */
    ProjectIndexStatusDTO indexProject(UUID projectId, String mode, String userEmail);

    /**
     * Incrementally updates chunks in Elasticsearch for a single saved file.
     *
     * @param projectId    Project UUID
     * @param relativePath Relative path of the updated file
     * @param userEmail    Authenticated user email
     */
    void indexSingleFile(UUID projectId, String relativePath, String userEmail);

    /**
     * Retrieves current indexing status and statistics for a project.
     *
     * @param projectId Project UUID
     * @param userEmail Authenticated user email
     * @return ProjectIndexStatusDTO containing status, file count, and chunk count
     */
    ProjectIndexStatusDTO getIndexStatus(UUID projectId, String userEmail);
}
