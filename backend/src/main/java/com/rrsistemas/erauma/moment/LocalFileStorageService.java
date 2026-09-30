package com.rrsistemas.erauma.moment;

import com.rrsistemas.erauma.shared.BusinessException;
import com.rrsistemas.erauma.story.StoryImageIntegrity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LocalFileStorageService implements FileStorageService {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalFileStorageService.class);
    private final Path root;
    private final Path storyRoot;

    public LocalFileStorageService(@Value("${app.storage.local-path:storage}") String localPath, Environment environment) {
        boolean prod = Arrays.stream(environment.getActiveProfiles()).anyMatch("prod"::equalsIgnoreCase);
        Path configured = Path.of(localPath);
        if (prod && !configured.isAbsolute()) {
            // Em producao, um caminho relativo cai no heuristico de "achar a raiz do
            // monorepo" pensado para desenvolvimento local, o que nesse ambiente
            // resolve para um diretorio dentro do proprio container - nao persistente
            // entre deploys/restarts. Falhar aqui na inicializacao (em vez de gravar
            // fotos e ilustracoes silenciosamente num lugar que sera perdido) e
            // intencional: configure APP_STORAGE_ROOT com um caminho absoluto
            // apontando para um volume persistente antes de subir em producao.
            throw new IllegalStateException(
                    "app.storage.local-path (APP_STORAGE_ROOT) precisa ser um caminho absoluto em producao "
                            + "(ex.: /data/storage) apontando para um volume persistente. Valor recebido: '"
                            + localPath + "'. Recusando iniciar para evitar gravar arquivos em um diretorio "
                            + "nao-persistente do container.");
        }
        Path base = resolveStorageRoot(localPath);
        this.root = base.resolve("moments").normalize();
        this.storyRoot = base.resolve("stories").normalize();
        try {
            Files.createDirectories(root);
            Files.createDirectories(storyRoot);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to initialize local storage directories", exception);
        }
        LOGGER.info("local_storage_root path={} absolute={} prodProfile={}", base, base.isAbsolute(), prod);
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
        Path temp = storyDirectory.resolve(PathSafe.filename(filename) + "." + UUID.randomUUID() + ".tmp").normalize();
        if (!temp.startsWith(storyDirectory)) {
            throw new BusinessException("INVALID_FILE", "Arquivo invalido", HttpStatus.BAD_REQUEST);
        }
        try {
            Files.write(temp, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            byte[] stored = Files.readAllBytes(temp);
            StoryImageIntegrity.Validation written = StoryImageIntegrity.validatePng(stored);
            if (stored.length != bytes.length || !received.sha256().equals(written.sha256()) || !written.valid()) {
                throw new IOException("Invalid story image PNG after storage: " + written.reason());
            }
            // temp e target ficam sempre no mesmo diretorio (logo, no mesmo volume/filesystem),
            // entao ATOMIC_MOVE e sempre suportado na pratica (renomear dentro do mesmo
            // filesystem e atomico em qualquer SO real). Deliberadamente NAO ha um fallback para
            // um "move" nao-atomico aqui: um fallback baseado em copia poderia deixar o arquivo
            // definitivo truncado/corrompido no meio de uma falha, exatamente o que a escrita via
            // arquivo temporario existe para evitar. Se por algum motivo excepcional o SO recusar
            // o move atomico, preferimos falhar alto (a imagem antiga, se houver, permanece
            // intocada) a arriscar substituir um arquivo valido por um parcial.
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
        return storyId + "/" + PathSafe.filename(filename);
    }

    @Override
    public String saveStoryAudio(byte[] bytes, String storyId, String filename) throws IOException {
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException("INVALID_FILE", "Arquivo invalido", HttpStatus.BAD_REQUEST);
        }
        Path storyDirectory = storyRoot.resolve(storyId).normalize();
        if (!storyDirectory.startsWith(storyRoot)) {
            throw new BusinessException("INVALID_FILE", "Arquivo invalido", HttpStatus.BAD_REQUEST);
        }
        Files.createDirectories(storyDirectory);
        String safeFilename = PathSafe.filename(filename);
        Path target = storyDirectory.resolve(safeFilename).normalize();
        Path temp = storyDirectory.resolve(safeFilename + "." + UUID.randomUUID() + ".tmp").normalize();
        if (!target.startsWith(storyDirectory) || !temp.startsWith(storyDirectory)) {
            throw new BusinessException("INVALID_FILE", "Arquivo invalido", HttpStatus.BAD_REQUEST);
        }
        try {
            Files.write(temp, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            if (Files.size(temp) != bytes.length) {
                throw new IOException("Story audio size mismatch after storage");
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
        return storyId + "/" + safeFilename;
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
    public StoredFile loadStoryAudio(String storageKey, long sizeBytes) {
        Path target = resolveStorageKey(storyRoot, storageKey, true);
        if (!Files.isRegularFile(target)) {
            throw new BusinessException("STORY_AUDIO_NOT_FOUND", "Áudio não encontrado", HttpStatus.NOT_FOUND);
        }
        try {
            long actualSize = Files.size(target);
            if (actualSize <= 0 || (sizeBytes > 0 && actualSize != sizeBytes)) {
                throw new BusinessException("STORY_AUDIO_NOT_FOUND", "Áudio não encontrado", HttpStatus.NOT_FOUND);
            }
            String contentType = Files.probeContentType(target);
            if (contentType == null || contentType.isBlank()) contentType = "audio/mpeg";
            return new StoredFile(new FileSystemResource(target), contentType, actualSize);
        } catch (IOException exception) {
            throw new BusinessException("STORY_AUDIO_NOT_FOUND", "Áudio não encontrado", HttpStatus.NOT_FOUND);
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

    @Override
    public boolean storyImageConfirmedMissing(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return false;
        }
        Path target = storyRoot.resolve(storageKey).normalize();
        if (!target.startsWith(storyRoot)) {
            return false;
        }
        // Files.notExists(...) so retorna true quando a ausencia pode ser CONFIRMADA; um erro de
        // I/O transitorio ao verificar (permissao, montagem de rede instavel, etc.) faz o metodo
        // devolver false, e nao true - ao contrario de Files.isRegularFile(...), que devolve false
        // tanto para "nao existe" quanto para "nao foi possivel determinar".
        return Files.notExists(target);
    }

    @Override
    public void deleteMomentPhoto(String storageKey) {
        deleteFile(resolveStorageKey(root, storageKey, false));
    }

    @Override
    public void deleteStoryImage(String storageKey) {
        deleteFile(resolveStorageKey(storyRoot, storageKey, true));
    }

    @Override
    public void deleteStoryAudio(String storageKey) {
        deleteFile(resolveStorageKey(storyRoot, storageKey, true));
    }

    @Override
    public void deleteStoryDirectory(UUID storyId) {
        Path target = storyRoot.resolve(storyId.toString()).normalize();
        if (!target.startsWith(storyRoot) || target.equals(storyRoot)) {
            throw new BusinessException("INVALID_FILE", "Arquivo invalido", HttpStatus.BAD_REQUEST);
        }
        if (Files.notExists(target)) return;
        try (var paths = Files.walk(target)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to delete story directory", exception);
        }
    }

    private Path resolveStorageKey(Path allowedRoot, String storageKey, boolean nested) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new BusinessException("INVALID_FILE", "Arquivo invalido", HttpStatus.BAD_REQUEST);
        }
        Path relative;
        try {
            relative = Path.of(storageKey);
        } catch (RuntimeException exception) {
            throw new BusinessException("INVALID_FILE", "Arquivo invalido", HttpStatus.BAD_REQUEST);
        }
        if (relative.isAbsolute() || (!nested && relative.getNameCount() != 1)) {
            throw new BusinessException("INVALID_FILE", "Arquivo invalido", HttpStatus.BAD_REQUEST);
        }
        Path target = allowedRoot.resolve(relative).normalize();
        if (!target.startsWith(allowedRoot) || target.equals(allowedRoot)) {
            throw new BusinessException("INVALID_FILE", "Arquivo invalido", HttpStatus.BAD_REQUEST);
        }
        return target;
    }

    private void deleteFile(Path target) {
        try {
            Files.deleteIfExists(target);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to delete stored file", exception);
        }
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
