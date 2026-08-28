package com.rrsistemas.erauma.family;

import com.rrsistemas.erauma.shared.BusinessException;
import com.rrsistemas.erauma.user.AppUser;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FamilyService {
    private final FamilyRepository families;
    private final FamilyMemberRepository members;

    public FamilyService(FamilyRepository families, FamilyMemberRepository members) {
        this.families = families;
        this.members = members;
    }

    @Transactional
    public FamilyResponse create(CreateFamilyRequest request, AppUser user) {
        Family family = families.save(new Family(request.name().trim(), user));
        members.save(new FamilyMember(family, user, FamilyMemberRole.OWNER));
        return FamilyResponse.from(family);
    }

    public List<FamilyResponse> mine(AppUser user) {
        return members.findFamiliesByUserId(user.getId()).stream().map(FamilyResponse::from).toList();
    }

    public Family requireMembership(UUID familyId, AppUser user) {
        if (!members.existsByFamily_IdAndUser_IdAndActiveTrue(familyId, user.getId())) {
            throw new BusinessException("FAMILY_NOT_FOUND", "Família não encontrada", HttpStatus.NOT_FOUND);
        }
        return families.findById(familyId).filter(Family::isActive)
                .orElseThrow(() -> new BusinessException("FAMILY_NOT_FOUND", "Família não encontrada", HttpStatus.NOT_FOUND));
    }
}
