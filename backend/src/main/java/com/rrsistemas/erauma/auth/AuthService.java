package com.rrsistemas.erauma.auth;

import com.rrsistemas.erauma.shared.BusinessException;
import com.rrsistemas.erauma.user.AppUser;
import com.rrsistemas.erauma.user.AppUserRepository;
import com.rrsistemas.erauma.user.UserResponse;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final AppUserRepository users;
    private final PasswordResetTokenRepository passwordResetTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final boolean exposeResetToken;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            AppUserRepository users,
            PasswordResetTokenRepository passwordResetTokens,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            EmailService emailService,
            @Value("${app.auth.password-reset.expose-token:false}") boolean exposeResetToken) {
        this.users = users;
        this.passwordResetTokens = passwordResetTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailService = emailService;
        this.exposeResetToken = exposeResetToken;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = AppUser.normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new BusinessException("EMAIL_ALREADY_EXISTS", "Email já cadastrado", HttpStatus.CONFLICT);
        }
        AppUser user = users.save(new AppUser(request.name().trim(), email, passwordEncoder.encode(request.password())));
        return UserResponse.from(user);
    }

    public AuthResponse login(LoginRequest request) {
        AppUser user = users.findByEmailAndActiveTrue(AppUser.normalizeEmail(request.email()))
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        return new AuthResponse(jwtService.generate(user), "Bearer", UserResponse.from(user));
    }

    @Transactional
    public PasswordResetResponse forgotPassword(ForgotPasswordRequest request) {
        String email = AppUser.normalizeEmail(request.email());
        String exposedToken = null;
        Optional<AppUser> user = users.findByEmailAndActiveTrue(email);
        if (user.isPresent()) {
            passwordResetTokens.invalidateActiveTokens(user.get().getId());
            String token = generateToken();
            Instant expiresAt = Instant.now().plus(Duration.ofMinutes(30));
            passwordResetTokens.save(new PasswordResetToken(user.get(), hashToken(token), expiresAt));
            emailService.sendPasswordReset(user.get(), token, expiresAt);
            exposedToken = exposeResetToken ? token : null;
        }
        return PasswordResetResponse.neutral(exposedToken);
    }

    @Transactional
    public ResetPasswordResponse resetPassword(ResetPasswordRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BusinessException("PASSWORD_CONFIRMATION_MISMATCH", "As senhas não coincidem.", HttpStatus.BAD_REQUEST);
        }
        PasswordResetToken token = passwordResetTokens.findByTokenHash(hashToken(request.token()))
                .orElseThrow(() -> new BusinessException("PASSWORD_RESET_TOKEN_INVALID", "Token de redefinição inválido ou expirado.", HttpStatus.BAD_REQUEST));
        if (!token.isUsable(Instant.now())) {
            throw new BusinessException("PASSWORD_RESET_TOKEN_INVALID", "Token de redefinição inválido ou expirado.", HttpStatus.BAD_REQUEST);
        }
        AppUser user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        token.markUsed();
        return new ResetPasswordResponse("Senha alterada com sucesso.");
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponível", exception);
        }
        StringBuilder builder = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }
}
