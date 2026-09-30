package com.rrsistemas.erauma.story;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoryAudioRepository extends JpaRepository<StoryAudio, UUID> {
    List<StoryAudio> findByStory_IdOrderByChapter_ChapterNumberAscChunkIndexAsc(UUID storyId);
    List<StoryAudio> findByStatus(StoryAudioStatus status);
    List<StoryAudio> findByStatusAndUpdatedAtBefore(StoryAudioStatus status, Instant before);
    Optional<StoryAudio> findByChapter_IdAndTextHashAndVoiceAndChunkIndex(UUID chapterId, String hash, String voice, int chunkIndex);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select audio from StoryAudio audio where audio.id = :id")
    Optional<StoryAudio> findForUpdate(@Param("id") UUID id);
}
