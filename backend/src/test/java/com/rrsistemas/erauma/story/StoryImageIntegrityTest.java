package com.rrsistemas.erauma.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rrsistemas.erauma.moment.LocalFileStorageService;
import com.rrsistemas.erauma.moment.StoredFile;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StoryImageIntegrityTest {
    @TempDir
    java.nio.file.Path tempDir;

    @Test
    void validatesStoresReadsAndServesIdenticalRealPngBytes() throws Exception {
        byte[] original = png(32, 24);
        StoryImageIntegrity.Validation beforeStorage = StoryImageIntegrity.validatePng(original);

        LocalFileStorageService storage = new LocalFileStorageService(tempDir.toString());
        String storageKey = storage.saveStoryImage(original, UUID.randomUUID().toString(), "cover.png");
        StoredFile stored = storage.loadStoryImage(storageKey, original.length);

        byte[] loaded;
        try (InputStream input = stored.resource().getInputStream()) {
            loaded = input.readAllBytes();
        }
        StoryImageIntegrity.Validation afterStorage = StoryImageIntegrity.validatePng(loaded);

        assertThat(beforeStorage.valid()).isTrue();
        assertThat(afterStorage.valid()).isTrue();
        assertThat(afterStorage.width()).isEqualTo(32);
        assertThat(afterStorage.height()).isEqualTo(24);
        assertThat(loaded).isEqualTo(original);
        assertThat(sha256(loaded)).isEqualTo(sha256(original));
        assertThat(stored.sizeBytes()).isEqualTo(original.length);
        assertThat(stored.contentType()).isEqualTo("image/png");
    }

    @Test
    void rejectsTruncatedPngBeforeItCanBeStoredAsGeneratedImage() throws Exception {
        byte[] original = png(32, 24);
        byte[] truncated = Arrays.copyOf(original, original.length / 2);

        StoryImageIntegrity.Validation validation = StoryImageIntegrity.validatePng(truncated);
        LocalFileStorageService storage = new LocalFileStorageService(tempDir.toString());

        assertThat(validation.valid()).isFalse();
        assertThatThrownBy(() -> storage.saveStoryImage(truncated, UUID.randomUUID().toString(), "scene-1.png"))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("Invalid story image PNG");
    }

    private static byte[] png(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, new Color((x * 7) % 256, (y * 11) % 256, ((x + y) * 5) % 256, 255).getRGB());
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
