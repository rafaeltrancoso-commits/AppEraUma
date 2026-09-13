package com.rrsistemas.erauma.moment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rrsistemas.erauma.shared.BusinessException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

class LocalFileStorageServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void prodProfileAcceptsAnAbsoluteStorageRoot() {
        // Mirrors production's "/data/storage" shape (absolute path pointing at a
        // persistent volume) without ever touching the real filesystem location.
        Path absoluteRoot = tempDir.resolve("data").resolve("storage");

        LocalFileStorageService storage = new LocalFileStorageService(absoluteRoot.toString(), prodEnvironment());

        assertThat(storage).isNotNull();
        assertThat(Files.isDirectory(absoluteRoot.resolve("moments"))).isTrue();
        assertThat(Files.isDirectory(absoluteRoot.resolve("stories"))).isTrue();
    }

    @Test
    void prodProfileRejectsARelativeStorageRoot() {
        assertThatThrownBy(() -> new LocalFileStorageService("storage", prodEnvironment()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_STORAGE_ROOT")
                .hasMessageContaining("storage");
    }

    @Test
    void failureMessageNamesTheVariableWithoutLeakingSensitiveData() {
        String configuredRelativePath = "relative-path-example";

        assertThatThrownBy(() -> new LocalFileStorageService(configuredRelativePath, prodEnvironment()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_STORAGE_ROOT")
                .hasMessageContaining("caminho absoluto")
                .hasMessageContaining(configuredRelativePath)
                .hasMessageNotContainingAny("password", "senha", "token", "secret", "key");
    }

    @Test
    void testProfileAcceptsTheTemporaryDirectoryUsedByTests() {
        LocalFileStorageService storage = new LocalFileStorageService(tempDir.toString(), testEnvironment());

        assertThat(storage).isNotNull();
        assertThat(Files.isDirectory(tempDir.resolve("moments"))).isTrue();
        assertThat(Files.isDirectory(tempDir.resolve("stories"))).isTrue();
    }

    @Test
    void loadRejectsPathTraversalEscapingTheMomentsRoot() throws Exception {
        LocalFileStorageService storage = new LocalFileStorageService(tempDir.toString(), testEnvironment());
        Path secret = tempDir.resolve("secret-outside-moments-root.txt");
        Files.writeString(secret, "top-secret");

        assertThatThrownBy(() -> storage.load("../secret-outside-moments-root.txt", "text/plain", secret.toFile().length()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void loadStoryImageRejectsPathTraversalEscapingTheStoryRoot() throws Exception {
        LocalFileStorageService storage = new LocalFileStorageService(tempDir.toString(), testEnvironment());
        Path secret = tempDir.resolve("secret-outside-story-root.txt");
        Files.writeString(secret, "top-secret");

        assertThatThrownBy(() -> storage.loadStoryImage("../secret-outside-story-root.txt", secret.toFile().length()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void saveStoryImageRejectsPathTraversalInTheStoryId() throws Exception {
        LocalFileStorageService storage = new LocalFileStorageService(tempDir.toString(), testEnvironment());
        byte[] validPng = png();

        assertThatThrownBy(() -> storage.saveStoryImage(validPng, "../../" + UUID.randomUUID(), "cover.png"))
                .isInstanceOf(BusinessException.class);
    }

    private static byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private MockEnvironment prodEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        return environment;
    }

    private MockEnvironment testEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        return environment;
    }
}
