package com.rrsistemas.erauma.notification;
import com.rrsistemas.erauma.user.AppUser;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="push_device_token")
public class PushDeviceToken {
    @Id private UUID id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="user_id") private AppUser user;
    @Column(name="device_id") private String deviceId;
    @Column(name="expo_push_token") private String expoPushToken;
    @Enumerated(EnumType.STRING) private DevicePlatform platform;
    private boolean active = true;
    @Column(name="last_error") private String lastError;
    @Column(name="created_at") private Instant createdAt;
    @Column(name="updated_at") private Instant updatedAt;
    protected PushDeviceToken() {}
    PushDeviceToken(AppUser user, PushTokenRequest request) { id=UUID.randomUUID(); this.user=user; apply(request); createdAt=Instant.now(); }
    void assignTo(AppUser user) { this.user = user; }
    void apply(PushTokenRequest request) { deviceId=request.deviceId().trim(); expoPushToken=request.expoPushToken().trim(); platform=request.platform(); active=true; lastError=null; updatedAt=Instant.now(); }
    void invalidate(String error) { active=false; lastError=error; updatedAt=Instant.now(); }
    public UUID getId(){return id;} public String getExpoPushToken(){return expoPushToken;}
    public String getDeviceId(){return deviceId;}
}
