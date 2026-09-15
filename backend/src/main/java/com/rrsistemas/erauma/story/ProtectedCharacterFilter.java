package com.rrsistemas.erauma.story;

import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;

final class ProtectedCharacterFilter {
    private static final List<Pattern> PROTECTED_TERMS = List.of(
            "homem aranha", "spider ?man", "homem de ferro", "iron ?man", "capitao america", "captain america",
            "vingadores", "avengers", "pantera negra", "black panther", "hulk", "thor", "wolverine", "x men",
            "batman", "superman", "mulher maravilha", "wonder ?woman", "liga da justica", "justice league", "flash", "aquaman",
            "mickey", "minnie", "elsa", "frozen", "moana", "buzz lightyear", "woody", "toy story",
            "mario", "luigi", "pokemon", "pikachu", "sonic",
            "bob esponja", "spongebob", "patrulha canina", "paw patrol",
            "transformers", "my little pony",
            "minions", "shrek",
            "harry potter", "looney tunes",
            "minecraft", "among us", "roblox", "barbie",
            "star wars", "darth vader", "yoda",
            "herois de pijama", "pj masks",
            "galinha pintadinha", "turma da monica",
            "power rangers", "ben 10", "naruto", "dragon ?ball", "goku", "hello kitty", "peppa pig")
            .stream()
            .map(term -> Pattern.compile("(?iu)\\b" + term.replace(" ", "\\s+") + "\\b"))
            .toList();

    private ProtectedCharacterFilter() {}

    static boolean containsProtectedReference(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = stripAccents(text);
        return PROTECTED_TERMS.stream().anyMatch(pattern -> pattern.matcher(normalized).find());
    }

    static String sanitize(String text, String fallback) {
        return containsProtectedReference(text) ? fallback : text;
    }

    private static String stripAccents(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
