package com.rrsistemas.erauma.story;

import com.rrsistemas.erauma.family.FamilyService;
import com.rrsistemas.erauma.moment.FileStorageService;
import com.rrsistemas.erauma.moment.StoredFile;
import com.rrsistemas.erauma.shared.BusinessException;
import com.rrsistemas.erauma.user.AppUser;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoryAudioContentService {
    private final StoryAudioRepository audios;
    private final FamilyService families;
    private final FileStorageService storage;

    public StoryAudioContentService(StoryAudioRepository audios, FamilyService families, FileStorageService storage) {
        this.audios = audios;
        this.families = families;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> content(UUID id, AppUser user) {
        StoryAudio audio = audios.findById(id).orElseThrow(this::notFound);
        if (!audio.getStory().isActive() || audio.getStatus() != StoryAudioStatus.COMPLETED || audio.getStorageKey() == null) throw notFound();
        families.requireMembership(audio.getStory().getFamilyId(), user);
        StoredFile file = storage.loadStoryAudio(audio.getStorageKey(), audio.getSizeBytes() == null ? 0 : audio.getSizeBytes());
        try (InputStream input = file.resource().getInputStream()) {
            byte[] bytes = input.readAllBytes();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, file.contentType())
                    .cacheControl(CacheControl.noStore())
                    .contentLength(bytes.length)
                    .body(bytes);
        } catch (IOException exception) { throw notFound(); }
    }

    private BusinessException notFound() { return new BusinessException("STORY_AUDIO_NOT_FOUND", "Áudio não encontrado", HttpStatus.NOT_FOUND); }
}
