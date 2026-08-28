package com.rrsistemas.erauma.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 6) String newPassword,
        @NotBlank String confirmPassword
) {}
