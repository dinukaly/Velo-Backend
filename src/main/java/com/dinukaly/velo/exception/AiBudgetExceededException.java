package com.dinukaly.velo.exception;

public class AiBudgetExceededException extends RuntimeException {
    public AiBudgetExceededException() {
        super("AI input exceeds the configured budget. Reduce the request, history, selection, or file context.");
    }
}
