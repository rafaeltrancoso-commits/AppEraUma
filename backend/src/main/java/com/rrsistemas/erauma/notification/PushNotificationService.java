package com.rrsistemas.erauma.notification;
import com.fasterxml.jackson.databind.JsonNode;
import com.rrsistemas.erauma.story.StoryGenerationStatus;
import com.rrsistemas.erauma.user.AppUser;
import com.rrsistemas.erauma.shared.BusinessException;
import java.util.*;
import java.time.Duration;
import org.slf4j.*;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

@Service
public class PushNotificationService {
    private static final Logger LOGGER=LoggerFactory.getLogger(PushNotificationService.class);
    private static final String EXPO_URL="https://exp.host/--/api/v2/push/send";
    private final PushDeviceTokenRepository tokens;
    private final RestTemplateBuilder http;
    public PushNotificationService(PushDeviceTokenRepository tokens, RestTemplateBuilder http){this.tokens=tokens;this.http=http;}
    @Transactional
    public void register(AppUser user, PushTokenRequest request) {
        if (!request.expoPushToken().matches("^(ExponentPushToken|ExpoPushToken)\\[[^\\]]+\\]$"))
            throw new BusinessException("PUSH_TOKEN_INVALID", "Token de notificação inválido.", HttpStatus.BAD_REQUEST);
        Optional<PushDeviceToken> byToken=tokens.findByExpoPushToken(request.expoPushToken().trim());
        Optional<PushDeviceToken> byDevice=tokens.findByUser_IdAndDeviceId(user.getId(), request.deviceId().trim());
        if (byToken.isPresent() && byDevice.isPresent() && !byToken.get().getId().equals(byDevice.get().getId())) {
            tokens.delete(byDevice.get());
            tokens.flush();
        }
        PushDeviceToken token=byToken
                .or(() -> byDevice)
                .orElseGet(() -> new PushDeviceToken(user, request));
        token.assignTo(user);
        token.apply(request); tokens.save(token);
    }
    @Transactional
    public void remove(AppUser user, String deviceId) {
        tokens.findByUser_IdAndDeviceId(user.getId(), deviceId).ifPresent(token -> token.invalidate("REMOVED_BY_DEVICE"));
    }
    public void storyReady(UUID userId, UUID storyId, StoryGenerationStatus status) {
        String body=status==StoryGenerationStatus.CONCLUIDA_COM_FALHAS
                ?"Sua história está pronta, mas uma das imagens precisa de uma nova tentativa."
                :"Sua história está pronta! Toque para começar a aventura.";
        for(PushDeviceToken token:tokens.findByUser_IdAndActiveTrue(userId)) send(token, storyId, body);
    }
    private void send(PushDeviceToken token, UUID storyId, String body) {
        try {
            Map<String,Object> payload=Map.of("to",token.getExpoPushToken(),"title","EraUma","body",body,"sound","default","data",Map.of("storyId",storyId.toString()));
            JsonNode response=http.setConnectTimeout(Duration.ofSeconds(10)).setReadTimeout(Duration.ofSeconds(10))
                    .build().postForObject(EXPO_URL, payload, JsonNode.class);
            JsonNode data=response==null?null:response.path("data");
            String error=data==null?"":data.path("details").path("error").asText("");
            if ("DeviceNotRegistered".equals(error)) tokens.findById(token.getId()).ifPresent(value->{value.invalidate(error);tokens.save(value);});
        } catch (RestClientException exception) {
            LOGGER.warn("expo_push_failed tokenId={} reason={}",token.getId(),exception.getClass().getSimpleName());
        }
    }
}
