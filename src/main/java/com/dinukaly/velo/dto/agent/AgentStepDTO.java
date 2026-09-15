package com.dinukaly.velo.dto.agent;

import com.dinukaly.velo.entity.AgentStepStatus;
import com.dinukaly.velo.entity.AgentStepType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class AgentStepDTO {

    private UUID id;
    private UUID runId;
    private int sequence;
    private AgentStepType type;
    private AgentStepStatus status;
    private String title;
    private String summary;
    private Instant startedAt;
    private Instant completedAt;
    private Instant createdAt;
}
