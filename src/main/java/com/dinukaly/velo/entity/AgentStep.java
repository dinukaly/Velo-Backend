package com.dinukaly.velo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_steps", indexes = {
        @Index(name = "idx_agent_steps_run", columnList = "run_id"),
        @Index(name = "idx_agent_steps_run_seq", columnList = "run_id, sequence")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentStep {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    private AgentRun run;

    // Monotonically increasing within a run (1, 2, 3 …)
    @Column(nullable = false)
    private int sequence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AgentStepType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AgentStepStatus status;

    // Short display title shown in the UI (e.g., "Searching project files")
    @Column(length = 255)
    private String title;

    // Longer user-facing explanation of what this step did / found
    @Column(columnDefinition = "TEXT")
    private String summary;

    // Arbitrary structured metadata for this step type (files read, query used, etc.)
    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column
    private Instant startedAt;

    @Column
    private Instant completedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = Instant.now();
        if (status == null) status = AgentStepStatus.PENDING;
    }
}
