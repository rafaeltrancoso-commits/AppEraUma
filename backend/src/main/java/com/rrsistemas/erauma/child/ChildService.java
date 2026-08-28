package com.rrsistemas.erauma.child;

import com.rrsistemas.erauma.family.Family;
import com.rrsistemas.erauma.family.FamilyService;
import com.rrsistemas.erauma.shared.BusinessException;
import com.rrsistemas.erauma.user.AppUser;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChildService {
    private final ChildProfileRepository children;
    private final FamilyService familyService;

    public ChildService(ChildProfileRepository children, FamilyService familyService) {
        this.children = children;
        this.familyService = familyService;
    }

    @Transactional
    public ChildResponse create(UUID familyId, ChildRequest request, AppUser user) {
        Family family = familyService.requireMembership(familyId, user);
        return ChildResponse.from(children.save(new ChildProfile(family, request)));
    }

    public List<ChildResponse> list(UUID familyId, AppUser user) {
        familyService.requireMembership(familyId, user);
        return children.findByFamily_IdAndActiveTrue(familyId).stream().map(ChildResponse::from).toList();
    }

    public ChildResponse get(UUID childId, AppUser user) {
        ChildProfile child = findAllowed(childId, user);
        return ChildResponse.from(child);
    }

    @Transactional
    public ChildResponse update(UUID childId, ChildRequest request, AppUser user) {
        ChildProfile child = findAllowed(childId, user);
        child.apply(request);
        return ChildResponse.from(child);
    }

    private ChildProfile findAllowed(UUID childId, AppUser user) {
        ChildProfile child = children.findByIdAndActiveTrue(childId)
                .orElseThrow(() -> new BusinessException("CHILD_NOT_FOUND", "Criança não encontrada", HttpStatus.NOT_FOUND));
        familyService.requireMembership(child.getFamilyId(), user);
        return child;
    }
}
