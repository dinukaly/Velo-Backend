package com.dinukaly.velo.dto.agent;

import com.dinukaly.velo.entity.IndexStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO returning current indexing status of a project.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectIndexStatusDTO {
    private UUID projectId;
    private IndexStatus status;
    private long indexGeneration;
    private int indexSchemaVersion;
    private int indexedFileCount;
    private int indexedChunkCount;
    private Instant lastIndexedAt;
    private Instant lastSuccessfulIndexedAt;
    private String lastError;
}
