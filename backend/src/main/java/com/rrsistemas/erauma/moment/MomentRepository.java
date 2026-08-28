package com.rrsistemas.erauma.moment;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MomentRepository extends JpaRepository<Moment, UUID>, JpaSpecificationExecutor<Moment> {
    Optional<Moment> findByIdAndActiveTrue(UUID id);

    @Query(value = """
            select cast(m.occurred_at as date) as date, count(distinct m.id) as count
            from moment m
            left join moment_child mc on mc.moment_id = m.id
            where m.family_id = :familyId
              and m.active = true
              and (:childId is null or mc.child_id = :childId)
              and m.occurred_at >= :from
              and m.occurred_at < :to
            group by cast(m.occurred_at as date)
            order by cast(m.occurred_at as date)
            """, nativeQuery = true)
    java.util.List<MomentCalendarProjection> calendar(@Param("familyId") UUID familyId, @Param("childId") UUID childId, @Param("from") java.time.LocalDateTime from, @Param("to") java.time.LocalDateTime to);
}
