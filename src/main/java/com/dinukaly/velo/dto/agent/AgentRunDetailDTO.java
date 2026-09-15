package com.dinukaly.velo.dto.agent;

import com.dinukaly.velo.entity.AgentRunStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full detail response for GET /api/v1/ai/agent/runs/{runId}.
 * Includes the run, all steps, and events (without raw payloads for brevity).
 */
@Data
@Builder
public class AgentRunDetailDTO {

    private UUID id;
    private UUID projectId;
    private String message;
    private AgentRunStatus status;
    private String currentPath;
    private String summary;
    private String errorCode;
    private String errorMessage;
    private int runVersion;
    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;
    private Instant updatedAt;

    private List<AgentStepDTO> steps;
}
