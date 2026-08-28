package com.rrsistemas.erauma.family;

import com.rrsistemas.erauma.shared.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/families")
public class FamilyController {
    private final FamilyService familyService;
    private final CurrentUser currentUser;

    public FamilyController(FamilyService familyService, CurrentUser currentUser) {
        this.familyService = familyService;
        this.currentUser = currentUser;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    FamilyResponse create(@Valid @RequestBody CreateFamilyRequest request) {
        return familyService.create(request, currentUser.get());
    }

    @GetMapping("/me")
    List<FamilyResponse> mine() {
        return familyService.mine(currentUser.get());
    }
}

