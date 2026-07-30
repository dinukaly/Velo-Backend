package com.dinukaly.velo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a single reviewable text edit (hunk) within an AgentProposalFile.
 *
 * A hunk describes one contiguous range of the file to change:
 * - originalStartLine / originalEndLine: the line range in the ORIGINAL file to replace.
 * - newContent: the replacement text to write.
 *
 * The user approves or rejects each hunk independently.
 * Related hunks are grouped by changeGroupKey to enforce dependency ordering
 * (e.g., a function rename hunk and its call-site update hunks form one group).
 */
@Entity
@Table(name = "agent_proposal_hunks", indexes = {
        @Index(name = "idx_proposal_hunks_file", columnList = "proposal_file_id"),
        @Index(name = "idx_proposal_hunks_decision", columnList = "decision"),
        @Index(name = "idx_proposal_hunks_group", columnList = "change_group_key")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentProposalHunk {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Parent proposal file this hunk belongs to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proposal_file_id", nullable = false)
    private AgentProposalFile proposalFile;

    /**
     * Position of this hunk within the file's hunk list (0-indexed).
     * Used to apply hunks in top-to-bottom order during Safe-Apply.
     */
    @Column(nullable = false)
    private int ordinal;

    /**
     * 1-indexed start line in the ORIGINAL file to replace.
     * For INSERT operations: the line after which the new content is inserted.
     */
    @Column(name = "original_start_line", nullable = false)
    private int originalStartLine;

    /**
     * 1-indexed end line in the ORIGINAL file to replace (inclusive).
     * Equal to originalStartLine - 1 for pure INSERTs (no lines deleted).
     */
    @Column(name = "original_end_line", nullable = false)
    private int originalEndLine;

    /**
     * The exact original lines being replaced (for verification during Safe-Apply).
     * Compared against the current on-disk file to detect post-proposal edits by the user.
     */
    @Column(name = "original_content", columnDefinition = "LONGTEXT")
    private String originalContent;

    /**
     * The replacement text to write to disk when this hunk is ACCEPTED.
     * Empty string for DELETE hunks.
     */
    @Column(name = "new_content", columnDefinition = "LONGTEXT")
    private String newContent;

    /**
     * Unified diff snippet for this hunk, shown in the frontend diff viewer.
     * Format: standard unified diff (@@ -x,y +a,b @@ lines).
     */
    @Column(name = "diff_snippet", columnDefinition = "LONGTEXT")
    private String diffSnippet;

    /**
     * Dependency group identifier. Hunks sharing the same changeGroupKey must
     * be applied together — rejecting one automatically skips the others.
     * Null if this hunk has no dependencies.
     */
    @Column(name = "change_group_key", length = 100)
    private String changeGroupKey;

    /**
     * Short human-readable label for this hunk, shown as the hunk header in the diff viewer.
     * Example: "Add null check for input parameter"
     */
    @Column(length = 500)
    private String label;

    /** Current user decision for this hunk. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HunkDecision decision;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** Timestamp when the user made their accept/reject decision. */
    @Column(name = "decided_at")
    private Instant decidedAt;

    @PrePersist
    public void prePersist() {
        createdAt = Instant.now();
        if (decision == null) decision = HunkDecision.PENDING;
    }
}
