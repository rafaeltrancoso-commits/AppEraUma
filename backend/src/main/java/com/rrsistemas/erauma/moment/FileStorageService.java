package com.rrsistemas.erauma.moment;

import java.io.IOException;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    String save(MultipartFile file) throws IOException;
    String saveStoryImage(byte[] bytes, String storyId, String filename) throws IOException;
    StoredFile load(String storageKey, String contentType, long sizeBytes);
    StoredFile loadStoryImage(String storageKey, long sizeBytes);
    boolean storyImageExists(String storageKey);
}
