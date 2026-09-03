package com.rrsistemas.erauma.auth;

public class PasswordResetEmailException extends RuntimeException {
    public PasswordResetEmailException(String message, Throwable cause) {
        super(message, cause);
    }
}
