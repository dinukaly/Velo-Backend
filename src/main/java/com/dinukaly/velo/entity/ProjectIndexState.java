package com.dinukaly.velo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Tracks the indexing state, statistics, and generation metadata of a project in MySQL.
 *
 * Ensures indexing status is tracked separately from agent run execution state.
 */
@Entity
@Table(name = "project_index_states", indexes = {
        @Index(name = "idx_project_index_state_project", columnList = "project_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectIndexState {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false, unique = true)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IndexStatus status;

    /** Monotonically increasing index generation counter. */
    @Column(name = "index_generation", nullable = false)
    private long indexGeneration;

    /** Schema version of the Elasticsearch index mapping. */
    @Column(name = "index_schema_version", nullable = false)
    private int indexSchemaVersion;

    /** Total count of files successfully indexed. */
    @Column(name = "indexed_file_count", nullable = false)
    private int indexedFileCount;

    /** Total count of code chunks stored in Elasticsearch. */
    @Column(name = "indexed_chunk_count", nullable = false)
    private int indexedChunkCount;

    /** Timestamp when indexing was last initiated. */
    @Column(name = "last_indexed_at")
    private Instant lastIndexedAt;

    /** Timestamp of the last fully successful index run. */
    @Column(name = "last_successful_indexed_at")
    private Instant lastSuccessfulIndexedAt;

    /** Error message if the last index operation failed. */
    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (status == null) status = IndexStatus.NOT_INDEXED;
        if (indexSchemaVersion == 0) indexSchemaVersion = 1;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
