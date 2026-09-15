package com.dinukaly.velo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a single file that the agent wants to create, modify, delete, or rename.
 *
 * Each AgentProposalFile belongs to exactly one AgentProposal and contains one or more
 * AgentProposalHunks (the actual text edits).
 */
@Entity
@Table(name = "agent_proposal_files", indexes = {
        @Index(name = "idx_proposal_files_proposal", columnList = "proposal_id"),
        @Index(name = "idx_proposal_files_path", columnList = "file_path")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentProposalFile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The parent proposal this file belongs to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proposal_id", nullable = false)
    private AgentProposal proposal;

    /** Project-relative path of the file being changed (e.g. "src/main/java/Foo.java"). */
    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    /**
     * For RENAME operations: the new file path after renaming.
     * Null for CREATE, MODIFY, DELETE.
     */
    @Column(name = "new_file_path", length = 2000)
    private String newFilePath;

    /** Type of change the agent is making to this file. */
    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 20)
    private FileChangeType changeType;

    /**
     * SHA-256 hash of the file's current on-disk content at the time the proposal was generated.
     * Used during Safe-Apply to detect if the file was modified by the user after the proposal was made.
     */
    @Column(name = "base_file_hash", length = 64)
    private String baseFileHash;

    /**
     * The complete new content for CREATE or full-replace operations.
     * Null for hunk-level MODIFY and DELETE operations.
     */
    @Column(name = "full_content", columnDefinition = "LONGTEXT")
    private String fullContent;

    /**
     * Human-readable explanation of why this file is being changed.
     * Shown in the diff viewer's file-level header.
     */
    @Column(columnDefinition = "TEXT")
    private String rationale;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** All hunks (text edit ranges) within this file. */
    @OneToMany(mappedBy = "proposalFile", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordinal ASC")
    @Builder.Default
    private List<AgentProposalHunk> hunks = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        createdAt = Instant.now();
    }
}
