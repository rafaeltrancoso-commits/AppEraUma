package com.rrsistemas.erauma.shared;

import com.rrsistemas.erauma.user.AppUser;
import com.rrsistemas.erauma.user.AppUserRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {
    private final AppUserRepository users;

    public CurrentUser(AppUserRepository users) {
        this.users = users;
    }

    public AppUser get() {
        String subject = SecurityContextHolder.getContext().getAuthentication().getName();
        UUID userId;
        try {
            userId = UUID.fromString(subject);
        } catch (RuntimeException exception) {
            throw new BusinessException("AUTHENTICATION_INVALID", "Autenticação inválida", HttpStatus.UNAUTHORIZED);
        }
        return users.findById(userId)
                .filter(AppUser::isActive)
                .orElseThrow(() -> new BusinessException("AUTHENTICATION_INVALID", "Autenticação inválida", HttpStatus.UNAUTHORIZED));
    }
}
