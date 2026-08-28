package com.rrsistemas.erauma.family;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FamilyMemberRepository extends JpaRepository<FamilyMember, UUID> {
    boolean existsByFamily_IdAndUser_IdAndActiveTrue(UUID familyId, UUID userId);

    @Query("select f from Family f join FamilyMember m on m.family = f where m.user.id = :userId and m.active = true and f.active = true")
    List<Family> findFamiliesByUserId(@Param("userId") UUID userId);

    Optional<FamilyMember> findByFamily_IdAndUser_IdAndActiveTrue(UUID familyId, UUID userId);
}
