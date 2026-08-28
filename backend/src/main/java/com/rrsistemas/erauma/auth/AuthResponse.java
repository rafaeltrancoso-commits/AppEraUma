package com.rrsistemas.erauma.auth;

import com.rrsistemas.erauma.user.UserResponse;

public record AuthResponse(String accessToken, String tokenType, UserResponse user) {}

