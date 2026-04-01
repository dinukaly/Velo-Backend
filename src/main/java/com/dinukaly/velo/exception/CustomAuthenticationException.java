package com.dinukaly.velo.exception;

public class CustomAuthenticationException extends RuntimeException {
    public CustomAuthenticationException(String message) {
        super(message);
    }
}
