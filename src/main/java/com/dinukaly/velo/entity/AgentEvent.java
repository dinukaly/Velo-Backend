package com.dinukaly.velo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_events", indexes = {
        @Index(name = "idx_agent_events_run", columnList = "run_id"),
        @Index(name = "idx_agent_events_run_seq", columnList = "run_id, sequence")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    private AgentRun run;

    // Monotonically increasing per run — used as SSE id for Last-Event-ID reconnect
    @Column(nullable = false)
    private long sequence;

    // SSE event type string (e.g., "step.created", "run.status", "run.completed")
    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    // JSON payload delivered to the browser in the SSE data field
    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = Instant.now();
    }
}
