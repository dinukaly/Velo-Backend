package com.dinukaly.velo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "agent_runs", indexes = {
        @Index(name = "idx_agent_runs_project", columnList = "project_id"),
        @Index(name = "idx_agent_runs_user", columnList = "user_id"),
        @Index(name = "idx_agent_runs_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // The user's original instruction message
    @Column(nullable = false, length = 4000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AgentRunStatus status;

    // Editor context at time of run creation
    @Column(name = "current_path", length = 2000)
    private String currentPath;

    @Column(name = "selected_text", columnDefinition = "TEXT")
    private String selectedText;

    // JSON: open files, dirty files, cursor position
    @Column(name = "editor_context_json", columnDefinition = "TEXT")
    private String editorContextJson;

    // Short user-facing summary of what the agent did (populated after completion)
    @Column(columnDefinition = "TEXT")
    private String summary;

    // JSON array of warning objects
    @Column(name = "warnings_json", columnDefinition = "TEXT")
    private String warningsJson;

    // Structured error code (e.g., DIRTY_FILES_PRESENT, MODEL_PROVIDER_UNAVAILABLE)
    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    // Optimistic-lock version used in approval/apply endpoints
    @Column(name = "run_version", nullable = false)
    private int runVersion;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column
    private Instant startedAt;

    @Column
    private Instant completedAt;

    @Column(nullable = false)
    private Instant updatedAt;

    // child relationships
    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequence ASC")
    @Builder.Default
    private List<AgentStep> steps = new ArrayList<>();

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequence ASC")
    @Builder.Default
    private List<AgentEvent> events = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (status == null) status = AgentRunStatus.QUEUED;
        if (runVersion == 0) runVersion = 1;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
