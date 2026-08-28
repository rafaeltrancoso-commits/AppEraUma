package com.rrsistemas.erauma.story;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

public final class StoryImageIntegrity {
    private static final byte[] PNG_SIGNATURE = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private StoryImageIntegrity() {
    }

    public static Validation validatePng(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return Validation.invalid(0, "", "empty");
        }
        String sha256 = sha256(bytes);
        if (bytes.length < PNG_SIGNATURE.length || !Arrays.equals(Arrays.copyOf(bytes, PNG_SIGNATURE.length), PNG_SIGNATURE)) {
            return Validation.invalid(bytes.length, sha256, "invalid_png_signature");
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                return Validation.invalid(bytes.length, sha256, "image_input_stream_unavailable");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return Validation.invalid(bytes.length, sha256, "png_reader_unavailable");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                BufferedImage image = reader.read(0);
                if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                    return Validation.invalid(bytes.length, sha256, "png_decode_empty");
                }
                return new Validation(true, bytes.length, sha256, image.getWidth(), image.getHeight(), "image/png", "");
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException exception) {
            return Validation.invalid(bytes.length, sha256, sanitizeReason(exception));
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String sanitizeReason(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s{2,}", " ").trim();
    }

    public record Validation(
            boolean valid,
            int bytes,
            String sha256,
            int width,
            int height,
            String contentType,
            String reason) {
        private static Validation invalid(int bytes, String sha256, String reason) {
            return new Validation(false, bytes, sha256, 0, 0, "image/png", reason);
        }
    }
}
