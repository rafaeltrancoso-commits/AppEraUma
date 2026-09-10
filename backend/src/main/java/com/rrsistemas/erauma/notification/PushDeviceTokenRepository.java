package com.rrsistemas.erauma.notification;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PushDeviceTokenRepository extends JpaRepository<PushDeviceToken, UUID> {
    Optional<PushDeviceToken> findByUser_IdAndDeviceId(UUID userId, String deviceId);
    Optional<PushDeviceToken> findByExpoPushToken(String expoPushToken);
    List<PushDeviceToken> findByUser_IdAndActiveTrue(UUID userId);
}
