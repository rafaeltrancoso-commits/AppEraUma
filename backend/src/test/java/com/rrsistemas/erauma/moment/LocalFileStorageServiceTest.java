package com.rrsistemas.erauma.moment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rrsistemas.erauma.shared.BusinessException;
import com.rrsistemas.erauma.story.StoryImageIntegrity;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;
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

    @Test
    void saveStoryImageLeavesNoTemporaryFileAfterASuccessfulWrite() throws Exception {
        LocalFileStorageService storage = new LocalFileStorageService(tempDir.toString(), testEnvironment());
        String storyId = UUID.randomUUID().toString();

        storage.saveStoryImage(png(4), storyId, "cover.png");

        try (Stream<Path> files = Files.list(tempDir.resolve("stories").resolve(storyId))) {
            assertThat(files.map(path -> path.getFileName().toString())).containsExactly("cover.png");
        }
    }

    @Test
    void saveStoryImageAtomicallyReplacesAnExistingValidImageWithTheNewOne() throws Exception {
        LocalFileStorageService storage = new LocalFileStorageService(tempDir.toString(), testEnvironment());
        String storyId = UUID.randomUUID().toString();
        byte[] first = png(4);
        byte[] second = png(8);

        storage.saveStoryImage(first, storyId, "cover.png");
        storage.saveStoryImage(second, storyId, "cover.png");

        Path target = tempDir.resolve("stories").resolve(storyId).resolve("cover.png");
        byte[] onDisk = Files.readAllBytes(target);
        assertThat(onDisk).isEqualTo(second);
        StoryImageIntegrity.Validation validation = StoryImageIntegrity.validatePng(onDisk);
        assertThat(validation.valid()).isTrue();
        assertThat(validation.width()).isEqualTo(8);
        try (Stream<Path> files = Files.list(tempDir.resolve("stories").resolve(storyId))) {
            assertThat(files.map(path -> path.getFileName().toString())).containsExactly("cover.png");
        }
    }

    @Test
    void saveStoryImageDoesNotOverwriteAnExistingValidImageWhenNewBytesAreInvalid() throws Exception {
        LocalFileStorageService storage = new LocalFileStorageService(tempDir.toString(), testEnvironment());
        String storyId = UUID.randomUUID().toString();
        byte[] validImage = png(4);
        storage.saveStoryImage(validImage, storyId, "cover.png");

        assertThatThrownBy(() -> storage.saveStoryImage(new byte[] {1, 2, 3}, storyId, "cover.png"))
                .isInstanceOf(IOException.class);

        Path target = tempDir.resolve("stories").resolve(storyId).resolve("cover.png");
        assertThat(Files.readAllBytes(target)).isEqualTo(validImage);
        try (Stream<Path> files = Files.list(tempDir.resolve("stories").resolve(storyId))) {
            assertThat(files.map(path -> path.getFileName().toString())).containsExactly("cover.png");
        }
    }

    @Test
    void storyImageConfirmedMissingIsTrueOnlyWhenTheFileGenuinelyDoesNotExist() throws Exception {
        LocalFileStorageService storage = new LocalFileStorageService(tempDir.toString(), testEnvironment());
        String storyId = UUID.randomUUID().toString();
        storage.saveStoryImage(png(4), storyId, "cover.png");

        assertThat(storage.storyImageConfirmedMissing(storyId + "/cover.png")).isFalse();
        assertThat(storage.storyImageConfirmedMissing(storyId + "/never-written.png")).isTrue();
    }

    @Test
    void storyImageConfirmedMissingRejectsPathTraversalWithoutConfirmingAbsence() {
        LocalFileStorageService storage = new LocalFileStorageService(tempDir.toString(), testEnvironment());

        assertThat(storage.storyImageConfirmedMissing("../outside-story-root.png")).isFalse();
        assertThat(storage.storyImageConfirmedMissing(null)).isFalse();
        assertThat(storage.storyImageConfirmedMissing("")).isFalse();
    }

    private static byte[] png() throws Exception {
        return png(4);
    }

    private static byte[] png(int size) throws Exception {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
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
