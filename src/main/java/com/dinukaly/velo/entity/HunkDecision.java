package com.dinukaly.velo.entity;

/**
 * Decision state of a single AgentProposalHunk.
 */
public enum HunkDecision {
    /** Hunk is awaiting a user decision. */
    PENDING,

    /** User accepted this hunk — it will be written to disk during Apply. */
    ACCEPTED,

    /** User rejected this hunk — it will be skipped during Apply. */
    REJECTED,

    /** Hunk was skipped automatically because a dependent hunk was rejected. */
    SKIPPED
}
