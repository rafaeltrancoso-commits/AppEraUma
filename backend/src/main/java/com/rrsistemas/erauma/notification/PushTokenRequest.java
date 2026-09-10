package com.rrsistemas.erauma.notification;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
public record PushTokenRequest(@NotBlank @Size(max=180) String deviceId, @NotBlank @Size(max=300) String expoPushToken, @NotNull DevicePlatform platform) {}
