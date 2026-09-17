package com.rrsistemas.erauma.moment;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

final class ImageUploadValidator {
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
    private static final long MAX_ASPECT_RATIO = 50;

    private ImageUploadValidator() {}

    static Validation validate(byte[] bytes, String declaredContentType, int maxWidth, int maxHeight, long maxPixels) {
        String detected = detect(bytes);
        if (detected == null || !detected.equals(declaredContentType)) {
            throw new InvalidImageException(Failure.TYPE, "content_type_mismatch");
        }
        Dimensions dimensions = switch (detected) {
            case "image/png" -> validatePng(bytes, maxWidth, maxHeight, maxPixels);
            case "image/jpeg" -> validateJpeg(bytes, maxWidth, maxHeight, maxPixels);
            case "image/webp" -> validateWebp(bytes, maxWidth, maxHeight, maxPixels);
            default -> throw new InvalidImageException(Failure.TYPE, "unsupported_type");
        };
        return new Validation(detected, dimensions.width(), dimensions.height());
    }

    private static String detect(byte[] bytes) {
        if (bytes == null) return null;
        if (bytes.length >= PNG_SIGNATURE.length) {
            boolean png = true;
            for (int index = 0; index < PNG_SIGNATURE.length; index++) png &= bytes[index] == PNG_SIGNATURE[index];
            if (png) return "image/png";
        }
        if (bytes.length >= 3 && unsigned(bytes[0]) == 0xff && unsigned(bytes[1]) == 0xd8 && unsigned(bytes[2]) == 0xff) return "image/jpeg";
        if (bytes.length >= 12 && ascii(bytes, 0, "RIFF") && ascii(bytes, 8, "WEBP")) return "image/webp";
        return null;
    }

    private static Dimensions validatePng(byte[] bytes, int maxWidth, int maxHeight, long maxPixels) {
        int offset = 8;
        boolean ihdr = false;
        boolean idat = false;
        boolean iend = false;
        Dimensions dimensions = null;
        while (offset <= bytes.length - 12) {
            long chunkLengthLong = uint32be(bytes, offset);
            if (chunkLengthLong > Integer.MAX_VALUE) invalidContent("png_chunk_too_large");
            int chunkLength = (int) chunkLengthLong;
            long chunkEndLong = (long) offset + 12L + chunkLength;
            if (chunkEndLong > bytes.length) invalidContent("truncated_png_chunk");
            int chunkEnd = (int) chunkEndLong;
            String type = new String(bytes, offset + 4, 4, StandardCharsets.US_ASCII);
            CRC32 crc = new CRC32();
            crc.update(bytes, offset + 4, 4 + chunkLength);
            if (crc.getValue() != uint32be(bytes, offset + 8 + chunkLength)) invalidContent("invalid_png_crc");
            if (!ihdr && (!"IHDR".equals(type) || chunkLength != 13)) invalidContent("missing_png_ihdr");
            if ("IHDR".equals(type)) {
                if (ihdr || chunkLength != 13) invalidContent("invalid_png_ihdr");
                ihdr = true;
                dimensions = checkedDimensions(uint32be(bytes, offset + 8), uint32be(bytes, offset + 12), maxWidth, maxHeight, maxPixels);
            } else if ("IDAT".equals(type)) {
                idat = true;
            } else if ("IEND".equals(type)) {
                if (chunkLength != 0 || chunkEnd != bytes.length) invalidContent("invalid_png_iend");
                iend = true;
                offset = chunkEnd;
                break;
            }
            offset = chunkEnd;
        }
        if (!ihdr || !idat || !iend || offset != bytes.length || dimensions == null) invalidContent("incomplete_png");
        decodeWithImageIo(bytes, dimensions, maxWidth, maxHeight, maxPixels);
        return dimensions;
    }

    private static Dimensions validateJpeg(byte[] bytes, int maxWidth, int maxHeight, long maxPixels) {
        if (bytes.length < 4 || unsigned(bytes[bytes.length - 2]) != 0xff || unsigned(bytes[bytes.length - 1]) != 0xd9) invalidContent("truncated_jpeg");
        return decodeWithImageIo(bytes, null, maxWidth, maxHeight, maxPixels);
    }

    private static Dimensions validateWebp(byte[] bytes, int maxWidth, int maxHeight, long maxPixels) {
        if (bytes.length < 20 || uint32le(bytes, 4) != bytes.length - 8L) invalidContent("invalid_webp_size");
        int offset = 12;
        Dimensions canvasDimensions = null;
        Dimensions payloadDimensions = null;
        while (offset <= bytes.length - 8) {
            String type = new String(bytes, offset, 4, StandardCharsets.US_ASCII);
            long chunkLengthLong = uint32le(bytes, offset + 4);
            if (chunkLengthLong > Integer.MAX_VALUE) invalidContent("webp_chunk_too_large");
            int chunkLength = (int) chunkLengthLong;
            long nextLong = (long) offset + 8L + chunkLength + (chunkLength & 1);
            if (nextLong > bytes.length) invalidContent("truncated_webp_chunk");
            Dimensions candidate = webpDimensions(bytes, offset + 8, chunkLength, type, maxWidth, maxHeight, maxPixels);
            if ("VP8X".equals(type)) canvasDimensions = candidate;
            if ("VP8 ".equals(type) || "VP8L".equals(type)) payloadDimensions = candidate;
            offset = (int) nextLong;
        }
        if (offset != bytes.length || payloadDimensions == null) invalidContent("incomplete_webp");
        if (canvasDimensions != null && !canvasDimensions.equals(payloadDimensions)) invalidContent("webp_dimensions_mismatch");
        return canvasDimensions == null ? payloadDimensions : canvasDimensions;
    }

    private static Dimensions webpDimensions(byte[] bytes, int data, int length, String type, int maxWidth, int maxHeight, long maxPixels) {
        if ("VP8X".equals(type) && length >= 10) {
            return checkedDimensions(1L + uint24le(bytes, data + 4), 1L + uint24le(bytes, data + 7), maxWidth, maxHeight, maxPixels);
        }
        if ("VP8L".equals(type) && length >= 5 && unsigned(bytes[data]) == 0x2f) {
            long width = 1L + unsigned(bytes[data + 1]) + ((long) (unsigned(bytes[data + 2]) & 0x3f) << 8);
            long height = 1L + ((unsigned(bytes[data + 2]) & 0xc0) >> 6) + ((long) unsigned(bytes[data + 3]) << 2)
                    + ((long) (unsigned(bytes[data + 4]) & 0x0f) << 10);
            return checkedDimensions(width, height, maxWidth, maxHeight, maxPixels);
        }
        if ("VP8 ".equals(type) && length >= 10 && unsigned(bytes[data + 3]) == 0x9d
                && unsigned(bytes[data + 4]) == 0x01 && unsigned(bytes[data + 5]) == 0x2a) {
            long width = (unsigned(bytes[data + 6]) | ((long) unsigned(bytes[data + 7]) << 8)) & 0x3fff;
            long height = (unsigned(bytes[data + 8]) | ((long) unsigned(bytes[data + 9]) << 8)) & 0x3fff;
            return checkedDimensions(width, height, maxWidth, maxHeight, maxPixels);
        }
        return null;
    }

    private static Dimensions decodeWithImageIo(byte[] bytes, Dimensions expected, int maxWidth, int maxHeight, long maxPixels) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) invalidContent("image_stream_unavailable");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) invalidContent("image_reader_unavailable");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, false, false);
                Dimensions dimensions = checkedDimensions(reader.getWidth(0), reader.getHeight(0), maxWidth, maxHeight, maxPixels);
                if (expected != null && !expected.equals(dimensions)) invalidContent("image_dimensions_mismatch");
                BufferedImage decoded = reader.read(0);
                if (decoded == null || decoded.getWidth() != dimensions.width() || decoded.getHeight() != dimensions.height()) invalidContent("image_decode_failed");
                return dimensions;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof InvalidImageException invalid) throw invalid;
            throw new InvalidImageException(Failure.CONTENT, "image_decode_failed", exception);
        }
    }

    private static Dimensions checkedDimensions(long width, long height, int maxWidth, int maxHeight, long maxPixels) {
        if (width <= 0 || height <= 0 || width > maxWidth || height > maxHeight || width > maxPixels / height
                || width > height * MAX_ASPECT_RATIO || height > width * MAX_ASPECT_RATIO) {
            throw new InvalidImageException(Failure.DIMENSIONS, "invalid_image_dimensions");
        }
        return new Dimensions((int) width, (int) height);
    }

    private static void invalidContent(String reason) { throw new InvalidImageException(Failure.CONTENT, reason); }
    private static boolean ascii(byte[] bytes, int offset, String value) {
        for (int index = 0; index < value.length(); index++) if (bytes[offset + index] != value.charAt(index)) return false;
        return true;
    }
    private static int unsigned(byte value) { return value & 0xff; }
    private static long uint32be(byte[] bytes, int offset) {
        return ((long) unsigned(bytes[offset]) << 24) | ((long) unsigned(bytes[offset + 1]) << 16)
                | ((long) unsigned(bytes[offset + 2]) << 8) | unsigned(bytes[offset + 3]);
    }
    private static long uint32le(byte[] bytes, int offset) {
        return unsigned(bytes[offset]) | ((long) unsigned(bytes[offset + 1]) << 8)
                | ((long) unsigned(bytes[offset + 2]) << 16) | ((long) unsigned(bytes[offset + 3]) << 24);
    }
    private static long uint24le(byte[] bytes, int offset) {
        return unsigned(bytes[offset]) | ((long) unsigned(bytes[offset + 1]) << 8) | ((long) unsigned(bytes[offset + 2]) << 16);
    }

    record Validation(String contentType, int width, int height) {}
    private record Dimensions(int width, int height) {}
    enum Failure { TYPE, CONTENT, DIMENSIONS }
    static final class InvalidImageException extends RuntimeException {
        private final Failure failure;
        InvalidImageException(Failure failure, String message) { super(message); this.failure = failure; }
        InvalidImageException(Failure failure, String message, Throwable cause) { super(message, cause); this.failure = failure; }
        Failure failure() { return failure; }
    }
}
