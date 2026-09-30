package com.rrsistemas.erauma.story;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

final class StoryAudioText {
    private StoryAudioText() {}

    static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    static List<String> chunks(String value, int max) {
        String remaining = StoryTextNormalizer.normalizeStoryText(value).trim();
        List<String> result = new ArrayList<>();
        while (remaining.length() > max) {
            int boundary = naturalBoundary(remaining, max);
            result.add(remaining.substring(0, boundary).trim());
            remaining = remaining.substring(boundary).trim();
        }
        if (!remaining.isBlank()) result.add(remaining);
        return result;
    }

    private static int naturalBoundary(String text, int max) {
        for (int index = max; index >= Math.max(1, max / 2); index--) {
            char current = text.charAt(index - 1);
            if ((current == '.' || current == '!' || current == '?' || current == ';')
                    && index < text.length() && Character.isWhitespace(text.charAt(index))) return index;
        }
        int paragraph = text.lastIndexOf('\n', max);
        if (paragraph > 0) return paragraph;
        int space = text.lastIndexOf(' ', max);
        return space > 0 ? space : max;
    }
}
