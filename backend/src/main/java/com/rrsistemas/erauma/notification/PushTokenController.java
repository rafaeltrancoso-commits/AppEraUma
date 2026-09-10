package com.rrsistemas.erauma.notification;
import com.rrsistemas.erauma.shared.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/push-tokens")
public class PushTokenController {
    private final PushNotificationService service; private final CurrentUser currentUser;
    public PushTokenController(PushNotificationService service,CurrentUser currentUser){this.service=service;this.currentUser=currentUser;}
    @PutMapping @ResponseStatus(HttpStatus.NO_CONTENT)
    void register(@Valid @RequestBody PushTokenRequest request){service.register(currentUser.get(),request);}
    @DeleteMapping("/{deviceId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@PathVariable String deviceId){service.remove(currentUser.get(),deviceId);}
}
