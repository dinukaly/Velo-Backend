package com.dinukaly.velo.exception;

public class AiCallUnavailableException extends RuntimeException {
    public AiCallUnavailableException() {
        super("AI request admission is temporarily unavailable. Try again shortly.");
    }
}
