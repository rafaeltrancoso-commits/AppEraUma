package com.rrsistemas.erauma.moment;

import java.io.IOException;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    String save(MultipartFile file) throws IOException;
    String saveStoryImage(byte[] bytes, String storyId, String filename) throws IOException;
    StoredFile load(String storageKey, String contentType, long sizeBytes);
    StoredFile loadStoryImage(String storageKey, long sizeBytes);
    boolean storyImageExists(String storageKey);

    /**
     * Verifica, de forma conservadora, se o arquivo esta CONFIRMADAMENTE ausente (usado para
     * decidir se uma imagem GENERATED deve ser reconciliada para FAILED). Ao contrario de
     * {@link #storyImageExists(String)} (que usa Files.isRegularFile e pode devolver false tanto
     * para "nao existe" quanto para "nao foi possivel determinar" por um erro de I/O transitorio),
     * este metodo so deve retornar true quando a ausencia puder ser afirmada com confianca.
     */
    boolean storyImageConfirmedMissing(String storageKey);
}
