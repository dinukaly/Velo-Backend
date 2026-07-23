package com.dinukaly.velo.dto.agent;

import com.dinukaly.velo.entity.AgentRunStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class AgentRunResponseDTO {

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
}
