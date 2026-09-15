package com.dinukaly.velo.entity;

public enum AgentRunStatus {
    QUEUED,
    RUNNING,
    WAITING_FOR_APPROVAL,
    APPLYING,
    VERIFYING,
    DONE,
    FAILED,
    CANCELED,
    REJECTED,
    CONFLICTED
}
