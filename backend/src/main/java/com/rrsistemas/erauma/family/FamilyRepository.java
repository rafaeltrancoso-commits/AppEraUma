package com.rrsistemas.erauma.family;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FamilyRepository extends JpaRepository<Family, UUID> {}

