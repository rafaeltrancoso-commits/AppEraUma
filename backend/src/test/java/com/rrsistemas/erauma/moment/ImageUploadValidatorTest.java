package com.rrsistemas.erauma.moment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ImageUploadValidatorTest {
    @Test
    void fullyDecodesValidPngAndJpeg() throws Exception {
        assertThat(ImageUploadValidator.validate(image("png", 120, 80), "image/png", 1000, 1000, 1_000_000))
                .extracting("contentType", "width", "height").containsExactly("image/png", 120, 80);
        assertThat(ImageUploadValidator.validate(image("jpg", 120, 80), "image/jpeg", 1000, 1000, 1_000_000))
                .extracting("contentType", "width", "height").containsExactly("image/jpeg", 120, 80);
    }

    @Test
    void rejectsTruncatedImageEvenWhenSignatureIsValid() throws Exception {
        byte[] png = image("png", 32, 32);
        byte[] jpeg = image("jpg", 32, 32);
        assertThatThrownBy(() -> ImageUploadValidator.validate(Arrays.copyOf(png, png.length - 8), "image/png", 1000, 1000, 1_000_000))
                .isInstanceOf(ImageUploadValidator.InvalidImageException.class);
        assertThatThrownBy(() -> ImageUploadValidator.validate(Arrays.copyOf(jpeg, jpeg.length - 2), "image/jpeg", 1000, 1000, 1_000_000))
                .isInstanceOf(ImageUploadValidator.InvalidImageException.class);
    }

    @Test
    void rejectsMismatchedMimeAndUnsafeDimensionsBeforeDecodingPixels() throws Exception {
        assertThatThrownBy(() -> ImageUploadValidator.validate(image("png", 32, 32), "image/jpeg", 1000, 1000, 1_000_000))
                .isInstanceOf(ImageUploadValidator.InvalidImageException.class)
                .extracting("failure").isEqualTo(ImageUploadValidator.Failure.TYPE);
        assertThatThrownBy(() -> ImageUploadValidator.validate(image("png", 1200, 1), "image/png", 1000, 1000, 1_000_000))
                .isInstanceOf(ImageUploadValidator.InvalidImageException.class)
                .extracting("failure").isEqualTo(ImageUploadValidator.Failure.DIMENSIONS);
    }

    @Test
    void structurallyValidatesWebpContainerAndDimensionsWithoutRemovingSupport() {
        byte[] webp = minimalLosslessWebp(64, 48);
        assertThat(ImageUploadValidator.validate(webp, "image/webp", 1000, 1000, 1_000_000))
                .extracting("contentType", "width", "height").containsExactly("image/webp", 64, 48);
        assertThatThrownBy(() -> ImageUploadValidator.validate(Arrays.copyOf(webp, webp.length - 1), "image/webp", 1000, 1000, 1_000_000))
                .isInstanceOf(ImageUploadValidator.InvalidImageException.class);
    }

    private byte[] image(String format, int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, format, output)).isTrue();
        return output.toByteArray();
    }

    private byte[] minimalLosslessWebp(int width, int height) {
        int widthBits = width - 1;
        int heightBits = height - 1;
        byte[] payload = {
                0x2f,
                (byte) widthBits,
                (byte) (((widthBits >> 8) & 0x3f) | ((heightBits & 0x03) << 6)),
                (byte) (heightBits >> 2),
                (byte) (heightBits >> 10)
        };
        ByteBuffer buffer = ByteBuffer.allocate(26).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        buffer.putInt(18);
        buffer.put("WEBPVP8L".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        buffer.putInt(payload.length);
        buffer.put(payload);
        buffer.put((byte) 0);
        return buffer.array();
    }
}
