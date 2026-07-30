package com.dinukaly.velo.entity;

/**
 * Overall status of an AgentProposal.
 */
public enum ProposalStatus {
    /** Proposal has been generated and is awaiting user review. */
    PENDING_REVIEW,

    /** User approved the proposal; Safe-Apply is in progress. */
    APPLYING,

    /** All approved hunks have been written to disk successfully. */
    APPLIED,

    /** User explicitly rejected the entire proposal. */
    REJECTED,

    /** Apply operation failed due to a conflict or filesystem error. */
    FAILED,

    /** Proposal expired before user acted on it. */
    EXPIRED
}
