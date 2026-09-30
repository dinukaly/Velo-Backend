package com.dinukaly.velo.exception;

public class AgentSseCapacityException extends RuntimeException {
    public AgentSseCapacityException() {
        super("Agent event stream capacity reached. Retry shortly.");
    }
}
