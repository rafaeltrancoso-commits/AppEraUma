package com.rrsistemas.erauma.auth;

public record PasswordResetResponse(String message, String resetToken) {
    public static PasswordResetResponse neutral(String resetToken) {
        return new PasswordResetResponse("Se este e-mail estiver cadastrado, enviaremos as instruções para redefinir sua senha.", resetToken);
    }
}
