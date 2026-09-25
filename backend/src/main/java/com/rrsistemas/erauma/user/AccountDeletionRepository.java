package com.rrsistemas.erauma.user;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AccountDeletionRepository {
    private static final String FAMILY_SCOPE = """
            select f.id from family f
            where f.created_by_user_id = :userId
               or exists (select 1 from family_member mine where mine.family_id = f.id and mine.user_id = :userId)
            """;
    private final NamedParameterJdbcTemplate jdbc;

    public AccountDeletionRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean hasSharedFamily(UUID userId) {
        String sql = """
                select exists (
                  select 1 from family f
                  where f.id in (%s)
                    and (
                      f.created_by_user_id <> :userId
                      or exists (
                        select 1 from family_member other_member
                        join app_user other_user on other_user.id = other_member.user_id
                        where other_member.family_id = f.id
                          and other_member.user_id <> :userId
                          and other_member.active = true
                          and other_user.active = true
                      )
                    )
                )
                """.formatted(FAMILY_SCOPE);
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, params(userId), Boolean.class));
    }

    public void lockFamilyScope(UUID userId) {
        jdbc.queryForList("select id from family where id in (" + FAMILY_SCOPE + ") for update",
                params(userId), UUID.class);
    }

    public List<String> momentPhotoStorageKeys(UUID userId) {
        return jdbc.queryForList("""
                select mp.storage_key from moment_photo mp
                join moment m on m.id = mp.moment_id
                where m.family_id in (%s)
                """.formatted(FAMILY_SCOPE), params(userId), String.class);
    }

    public List<UUID> storyIds(UUID userId) {
        return jdbc.queryForList("""
                select s.id from story s
                where s.family_id in (%s) or s.created_by_user_id = :userId
                """.formatted(FAMILY_SCOPE), params(userId), UUID.class);
    }

    public void deleteAccountGraph(UUID userId) {
        Map<String, ?> values = params(userId);
        update("delete from ai_image_generation_log where user_id = :userId or family_id in (" + FAMILY_SCOPE + ")", values);
        update("delete from ai_generation_log where user_id = :userId or family_id in (" + FAMILY_SCOPE + ")", values);
        update("delete from story_image where story_id in (select id from story where family_id in (" + FAMILY_SCOPE + ") or created_by_user_id = :userId)", values);
        update("delete from story_chapter where story_id in (select id from story where family_id in (" + FAMILY_SCOPE + ") or created_by_user_id = :userId)", values);
        update("delete from story_character where story_id in (select id from story where family_id in (" + FAMILY_SCOPE + ") or created_by_user_id = :userId)", values);
        update("delete from story where family_id in (" + FAMILY_SCOPE + ") or created_by_user_id = :userId", values);
        update("delete from moment_photo where moment_id in (select id from moment where family_id in (" + FAMILY_SCOPE + "))", values);
        update("delete from moment_child where moment_id in (select id from moment where family_id in (" + FAMILY_SCOPE + "))", values);
        update("delete from moment_participant where moment_id in (select id from moment where family_id in (" + FAMILY_SCOPE + ")) or user_id = :userId", values);
        update("delete from moment where family_id in (" + FAMILY_SCOPE + ") or created_by_user_id = :userId", values);
        update("delete from child_profile where family_id in (" + FAMILY_SCOPE + ")", values);
        update("delete from family_member where family_id in (" + FAMILY_SCOPE + ") or user_id = :userId", values);
        update("delete from family where id in (" + FAMILY_SCOPE + ")", values);
        update("delete from password_reset_token where user_id = :userId", values);
        update("delete from push_device_token where user_id = :userId", values);
        update("delete from app_user where id = :userId", values);
    }

    private void update(String sql, Map<String, ?> values) { jdbc.update(sql, values); }
    private Map<String, ?> params(UUID userId) { return Map.of("userId", userId); }
}
