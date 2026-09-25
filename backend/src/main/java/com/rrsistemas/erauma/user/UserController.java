package com.rrsistemas.erauma.user;

import com.rrsistemas.erauma.shared.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final AccountDeletionService accountDeletionService;
    private final CurrentUser currentUser;

    public UserController(AccountDeletionService accountDeletionService, CurrentUser currentUser) {
        this.accountDeletionService = accountDeletionService;
        this.currentUser = currentUser;
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteMe(@Valid @RequestBody DeleteAccountRequest request) {
        accountDeletionService.delete(currentUser.get().getId(), request.password());
    }
}
