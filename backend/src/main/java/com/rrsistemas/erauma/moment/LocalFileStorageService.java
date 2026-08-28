package com.rrsistemas.erauma.moment;

import com.rrsistemas.erauma.shared.BusinessException;
import com.rrsistemas.erauma.story.StoryImageIntegrity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LocalFileStorageService implements FileStorageService {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalFileStorageService.class);
    private final Path root;
    private final Path storyRoot;

    public LocalFileStorageService(@Value("${app.storage.local-path:storage}") String localPath) {
        Path base = resolveStorageRoot(localPath);
        this.root = base.resolve("moments").normalize();
        this.storyRoot = base.resolve("stories").normalize();
        try {
            Files.createDirectories(root);
            Files.createDirectories(storyRoot);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to initialize local storage directories", exception);
        }
        LOGGER.info("local_storage_root path={}", base);
    }

    @Override
    public String save(MultipartFile file) throws IOException {
        String storageKey = UUID.randomUUID().toString();
        Path target = root.resolve(storageKey).normalize();
        if (!target.startsWith(root)) {
            throw new BusinessException("INVALID_FILE", "Arquivo invalido", HttpStatus.BAD_REQUEST);
        }
        file.transferTo(target);
        return storageKey;
    }

    @Override
    public String saveStoryImage(byte[] bytes, String storyId, String filename) throws IOException {
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException("INVALID_FILE", "Arquivo invalido", HttpStatus.BAD_REQUEST);
        }
        StoryImageIntegrity.Validation received = StoryImageIntegrity.validatePng(bytes);
        if (!received.valid()) {
            throw new IOException("Invalid story image PNG before storage: " + received.reason());
        }
        Path storyDirectory = storyRoot.resolve(storyId).normalize();
        if (!storyDirectory.startsWith(storyRoot)) {
            throw new BusinessException("INVALID_FILE", "Arquivo invalido", HttpStatus.BAD_REQUEST);
        }
        Files.createDirectories(storyDirectory);
        Path target = storyDirectory.resolve(PathSafe.filename(filename)).normalize();
        if (!target.startsWith(storyDirectory)) {
            throw new BusinessException("INVALID_FILE", "Arquivo invalido", HttpStatus.BAD_REQUEST);
        }
        Files.write(target, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        byte[] stored = Files.readAllBytes(target);
        StoryImageIntegrity.Validation written = StoryImageIntegrity.validatePng(stored);
        if (stored.length != bytes.length || !received.sha256().equals(written.sha256()) || !written.valid()) {
            Files.deleteIfExists(target);
            throw new IOException("Invalid story image PNG after storage: " + written.reason());
        }
        return storyId + "/" + PathSafe.filename(filename);
    }

    @Override
    public StoredFile load(String storageKey, String contentType, long sizeBytes) {
        Path target = root.resolve(storageKey).normalize();
        if (!target.startsWith(root) || !Files.exists(target)) {
            throw new BusinessException("PHOTO_NOT_FOUND", "Foto nao encontrada", HttpStatus.NOT_FOUND);
        }
        try {
            return new StoredFile(new FileSystemResource(target), contentType, Files.size(target));
        } catch (IOException exception) {
            throw new BusinessException("PHOTO_NOT_FOUND", "Foto nao encontrada", HttpStatus.NOT_FOUND);
        }
    }

    @Override
    public StoredFile loadStoryImage(String storageKey, long sizeBytes) {
        Path target = storyRoot.resolve(storageKey).normalize();
        if (!target.startsWith(storyRoot) || !Files.isRegularFile(target)) {
            throw new BusinessException("STORY_IMAGE_NOT_FOUND", "Imagem nao encontrada", HttpStatus.NOT_FOUND);
        }
        try {
            long actualSizeBytes = Files.size(target);
            if (actualSizeBytes <= 0) {
                throw new BusinessException("STORY_IMAGE_NOT_FOUND", "Imagem nao encontrada", HttpStatus.NOT_FOUND);
            }
            String contentType = Files.probeContentType(target);
            if (contentType == null || contentType.isBlank()) {
                contentType = "image/png";
            }
            return new StoredFile(new FileSystemResource(target), contentType, actualSizeBytes);
        } catch (IOException exception) {
            throw new BusinessException("STORY_IMAGE_NOT_FOUND", "Imagem nao encontrada", HttpStatus.NOT_FOUND);
        }
    }

    @Override
    public boolean storyImageExists(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return false;
        }
        Path target = storyRoot.resolve(storageKey).normalize();
        return target.startsWith(storyRoot) && Files.isRegularFile(target);
    }

    private Path resolveStorageRoot(String localPath) {
        Path configured = Path.of(localPath);
        if (configured.isAbsolute()) {
            return configured.normalize();
        }
        return findProjectRoot(Path.of("").toAbsolutePath().normalize()).resolve(configured).normalize();
    }

    private Path findProjectRoot(Path start) {
        Path current = start;
        while (current != null) {
            if (Files.exists(current.resolve("backend")) && Files.exists(current.resolve("mobile"))) {
                return current;
            }
            current = current.getParent();
        }
        Path cwd = Path.of("").toAbsolutePath().normalize();
        Path fileName = cwd.getFileName();
        if (fileName != null && "backend".equalsIgnoreCase(fileName.toString()) && cwd.getParent() != null) {
            return cwd.getParent();
        }
        return cwd;
    }
}
