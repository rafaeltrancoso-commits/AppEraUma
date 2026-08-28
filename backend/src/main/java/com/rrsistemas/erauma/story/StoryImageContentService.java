package com.rrsistemas.erauma.story;

import com.rrsistemas.erauma.family.FamilyService;
import com.rrsistemas.erauma.moment.FileStorageService;
import com.rrsistemas.erauma.moment.StoredFile;
import com.rrsistemas.erauma.shared.BusinessException;
import com.rrsistemas.erauma.user.AppUser;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoryImageContentService {
    private static final Logger LOGGER = LoggerFactory.getLogger(StoryImageContentService.class);
    private final StoryImageRepository images;
    private final FamilyService familyService;
    private final FileStorageService storage;

    public StoryImageContentService(StoryImageRepository images, FamilyService familyService, FileStorageService storage) {
        this.images = images;
        this.familyService = familyService;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> content(UUID imageId, AppUser user) {
        StoryImage image = null;
        boolean fileExists = false;
        try {
            image = images.findByIdAndStory_ActiveTrue(imageId)
                    .orElseThrow(() -> new BusinessException("STORY_IMAGE_NOT_FOUND", "Imagem nao encontrada", HttpStatus.NOT_FOUND));
            familyService.requireMembership(image.getStory().getFamilyId(), user);
            if (image.getStatus() != StoryImageStatus.GENERATED || image.getStorageKey() == null || image.getStorageKey().isBlank()) {
                throw new BusinessException("STORY_IMAGE_NOT_FOUND", "Imagem nao encontrada", HttpStatus.NOT_FOUND);
            }
            fileExists = storage.storyImageExists(image.getStorageKey());
            StoredFile stored = storage.loadStoryImage(image.getStorageKey(), 0);
            byte[] bytes;
            try (InputStream input = stored.resource().getInputStream()) {
                bytes = input.readAllBytes();
            }
            if (bytes.length <= 0 || bytes.length != stored.sizeBytes()) {
                throw new BusinessException("STORY_IMAGE_NOT_FOUND", "Imagem nao encontrada", HttpStatus.NOT_FOUND);
            }
            StoryImageIntegrity.Validation validation = StoryImageIntegrity.validatePng(bytes);
            LOGGER.info("story_image_content_integrity imageId={} validPng={} bytes={} storedBytes={} contentType={} width={} height={} shaMatch={}",
                    imageId,
                    validation.valid(),
                    bytes.length,
                    stored.sizeBytes(),
                    stored.contentType(),
                    validation.width(),
                    validation.height(),
                    true);
            if (!validation.valid()) {
                LOGGER.warn("story_image_content_invalid imageId={} reason={} bytes={} storageKey={}",
                        imageId,
                        validation.reason(),
                        bytes.length,
                        image.getStorageKey());
                throw new BusinessException("STORY_IMAGE_NOT_FOUND", "Imagem nao encontrada", HttpStatus.NOT_FOUND);
            }
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, stored.contentType())
                    .contentLength(bytes.length)
                    .body(bytes);
        } catch (IOException exception) {
            BusinessException businessException = new BusinessException("STORY_IMAGE_NOT_FOUND", "Imagem nao encontrada", HttpStatus.NOT_FOUND);
            logFailure(imageId, image, fileExists, businessException.getStatus(), exception);
            throw businessException;
        } catch (RuntimeException exception) {
            logFailure(imageId, image, fileExists, httpStatus(exception), exception);
            throw exception;
        }
    }

    private HttpStatus httpStatus(RuntimeException exception) {
        if (exception instanceof BusinessException businessException) {
            return businessException.getStatus();
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private void logFailure(UUID imageId, StoryImage image, boolean fileExists, HttpStatus httpStatus, Exception exception) {
        LOGGER.warn("story_image_content_failed imageId={} status={} storageKey presente={} fileExists={} httpStatus={} exceptionClass={} message={}",
                imageId,
                image == null ? null : image.getStatus(),
                image != null && image.getStorageKey() != null && !image.getStorageKey().isBlank(),
                fileExists,
                httpStatus.value(),
                exception.getClass().getSimpleName(),
                sanitize(exception.getMessage()));
    }

    private String sanitize(String message) {
        if (message == null) {
            return "";
        }
        String sanitized = message.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s{2,}", " ").trim();
        if (sanitized.length() > 180) {
            return sanitized.substring(0, 180);
        }
        return sanitized;
    }
}
