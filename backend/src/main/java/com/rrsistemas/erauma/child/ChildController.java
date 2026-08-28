package com.rrsistemas.erauma.child;

import com.rrsistemas.erauma.shared.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ChildController {
    private final ChildService childService;
    private final CurrentUser currentUser;

    public ChildController(ChildService childService, CurrentUser currentUser) {
        this.childService = childService;
        this.currentUser = currentUser;
    }

    @PostMapping("/families/{familyId}/children")
    @ResponseStatus(HttpStatus.CREATED)
    ChildResponse create(@PathVariable UUID familyId, @Valid @RequestBody ChildRequest request) {
        return childService.create(familyId, request, currentUser.get());
    }

    @GetMapping("/families/{familyId}/children")
    List<ChildResponse> list(@PathVariable UUID familyId) {
        return childService.list(familyId, currentUser.get());
    }

    @GetMapping("/children/{childId}")
    ChildResponse get(@PathVariable UUID childId) {
        return childService.get(childId, currentUser.get());
    }

    @PutMapping("/children/{childId}")
    ChildResponse update(@PathVariable UUID childId, @Valid @RequestBody ChildRequest request) {
        return childService.update(childId, request, currentUser.get());
    }
}

