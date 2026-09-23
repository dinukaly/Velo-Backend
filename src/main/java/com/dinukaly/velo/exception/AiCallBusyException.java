package com.dinukaly.velo.exception;

public class AiCallBusyException extends RuntimeException {
    private final long retrySeconds;

    public AiCallBusyException(long retrySeconds) {
        super("An AI request is already running for this account. Try again shortly.");
        this.retrySeconds = retrySeconds;
    }

    public long getRetrySeconds() { return retrySeconds; }
}
