package com.dinukaly.velo.dto.agent;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class AgentEventDTO {

    private UUID id;
    private UUID runId;
    private long sequence;
    private String eventType;
    private String payloadJson;
    private Instant createdAt;
}
