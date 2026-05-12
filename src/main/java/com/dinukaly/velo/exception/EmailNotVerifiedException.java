package com.dinukaly.velo.exception;

import lombok.Getter;

@Getter
public class EmailNotVerifiedException extends RuntimeException {
    private final String email;

    public EmailNotVerifiedException(String message, String email) {
        super(message);
        this.email = email;
    }
}
