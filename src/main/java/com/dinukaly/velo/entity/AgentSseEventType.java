package com.dinukaly.velo.entity;

/**
 * SSE event type strings sent over the /events stream.
 * These are the values used in the SSE "event:" field.
 */
public enum AgentSseEventType {
    RUN_STATUS("run.status"),
    STEP_CREATED("step.created"),
    STEP_UPDATED("step.updated"),
    PROPOSAL_CREATED("proposal.created"),
    PROPOSAL_UPDATED("proposal.updated"),
    HUNK_UPDATED("hunk.updated"),
    WARNING("warning"),
    VERIFICATION_STARTED("verification.started"),
    VERIFICATION_OUTPUT("verification.output"),
    VERIFICATION_COMPLETED("verification.completed"),
    RUN_COMPLETED("run.completed"),
    RUN_FAILED("run.failed"),
    RUN_CONFLICTED("run.conflicted");

    private final String value;

    AgentSseEventType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
