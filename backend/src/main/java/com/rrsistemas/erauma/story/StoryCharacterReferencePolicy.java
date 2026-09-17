package com.rrsistemas.erauma.story;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class StoryCharacterReferencePolicy {
    private static final String ADAPTED_MARKER = "personagem original";
    private static final Set<String> FAMILY_RELATIONS = Set.of(
            "pai", "papai", "mae", "mamae", "avo", "vovo", "tio", "tia",
            "irmao", "irma", "irmão", "irmã", "primo", "prima", "amigo", "amiga");
    private static final Map<String, Pattern> KNOWN_REFERENCES = knownReferences();
    private static final String[] ORIGINAL_NAMES = {
            "Guardião Aurora", "Guardião Celeste", "Guardião do Horizonte", "Guardião Estelar",
            "Capitão Brisa", "Viajante Solar", "Protetor do Amanhã", "Sentinela do Céu"
    };
    private static final String[][] ORIGINAL_PALETTES = {
            {"azul-petróleo", "coral"}, {"turquesa", "amarelo suave"}, {"verde folha", "azul claro"},
            {"lilás", "dourado suave"}, {"laranja", "azul-marinho"}, {"violeta", "prata"}
    };

    public NormalizedCharacters normalize(String value) {
        if (value == null || value.isBlank()) {
            return new NormalizedCharacters(null, false, List.of());
        }
        String compact = normalizeSpacing(value);
        List<AdditionalCharacter> characters = new ArrayList<>();
        boolean adapted = false;
        for (String raw : compact.split("\\s*(?:,|;|\\be\\b)\\s*")) {
            if (raw.isBlank()) {
                continue;
            }
            String display = normalizeDisplay(raw);
            String key = canonicalKey(display);
            String referenceKey = knownReferenceKey(display);
            if (referenceKey != null) {
                characters.add(adaptedCharacter(referenceKey));
                adapted = true;
            } else {
                AdditionalCharacterKind kind = FAMILY_RELATIONS.contains(key)
                        ? AdditionalCharacterKind.FAMILIAR
                        : AdditionalCharacterKind.FREE_FICTIONAL;
                characters.add(new AdditionalCharacter(display, display, kind, null));
            }
        }
        String normalized = characters.stream().map(AdditionalCharacter::safeDisplayName)
                .reduce((left, right) -> left + ", " + right).orElse(null);
        return new NormalizedCharacters(normalized, adapted, List.copyOf(characters));
    }

    public AdaptedText adaptKnownReferencesInText(String value) {
        String adapted = normalizeSpacing(value);
        boolean changed = false;
        for (Map.Entry<String, Pattern> entry : KNOWN_REFERENCES.entrySet()) {
            var matcher = entry.getValue().matcher(adapted);
            if (matcher.find()) {
                adapted = matcher.replaceAll(java.util.regex.Matcher.quoteReplacement(adaptedCharacter(entry.getKey()).safeDisplayName()));
                changed = true;
            }
        }
        return new AdaptedText(adapted, changed || containsAdaptedMarker(adapted));
    }

    public boolean containsAdaptedMarker(String value) {
        return value != null && canonicalKey(value).contains(canonicalKey(ADAPTED_MARKER));
    }

    private AdditionalCharacter adaptedCharacter(String key) {
        int hash = Math.floorMod(key.hashCode(), Integer.MAX_VALUE);
        String name = ORIGINAL_NAMES[hash % ORIGINAL_NAMES.length];
        String[] palette = ORIGINAL_PALETTES[hash % ORIGINAL_PALETTES.length];
        String description = name + " - personagem original heroico e amigável com traje próprio em "
                + palette[0] + " e " + palette[1] + " sem marcas nem logotipos ou símbolos de franquias";
        return new AdditionalCharacter(name, description, AdditionalCharacterKind.KNOWN_REFERENCE_ADAPTED, description);
    }

    private String knownReferenceKey(String value) {
        return KNOWN_REFERENCES.entrySet().stream()
                .filter(entry -> entry.getValue().matcher(value).find())
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private String normalizeDisplay(String value) {
        String display = normalizeSpacing(value).replaceAll("\\s*-\\s*", "-");
        String key = canonicalKey(display);
        if (key.equals("superman")) {
            return "Superman";
        }
        if (key.equals("homemaranha") || key.equals("spiderman")) {
            return "Homem-Aranha";
        }
        return display;
    }

    private String canonicalKey(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceFirst("^(?:o|a|os|as)\\s+", "")
                .replaceAll("[^a-z0-9]+", "");
        return normalized;
    }

    private String normalizeSpacing(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s{2,}", " ").trim();
    }

    private static Map<String, Pattern> knownReferences() {
        Map<String, Pattern> references = new LinkedHashMap<>();
        references.put("homemaranha", Pattern.compile("(?iu)\\b(?:homem\\s*-?\\s*aranha|spider\\s*-?\\s*man)\\b"));
        references.put("superman", Pattern.compile("(?iu)\\bsuper\\s*-?\\s*man\\b"));
        references.put("batman", Pattern.compile("(?iu)\\b(?:batman|homem\\s*-?\\s*morcego)\\b"));
        references.put("mulhermaravilha", Pattern.compile("(?iu)\\b(?:mulher\\s*-?\\s*maravilha|wonder\\s+woman)\\b"));
        references.put("capitaoamerica", Pattern.compile("(?iu)\\b(?:capit[aã]o\\s+am[eé]rica|captain\\s+america)\\b"));
        references.put("hulk", Pattern.compile("(?iu)\\bhulk\\b"));
        references.put("thor", Pattern.compile("(?iu)\\bthor\\b"));
        references.put("mickey", Pattern.compile("(?iu)\\b(?:mickey|minnie)\\b"));
        references.put("elsa", Pattern.compile("(?iu)\\b(?:elsa|frozen)\\b"));
        references.put("barbie", Pattern.compile("(?iu)\\bbarbie\\b"));
        references.put("pokemon", Pattern.compile("(?iu)\\b(?:pok[eé]mon|pikachu)\\b"));
        references.put("harrypotter", Pattern.compile("(?iu)\\bharry\\s+potter\\b"));
        return Map.copyOf(references);
    }

    public enum AdditionalCharacterKind {
        FAMILIAR,
        FREE_FICTIONAL,
        KNOWN_REFERENCE_ADAPTED
    }

    public record AdditionalCharacter(
            String normalizedInput,
            String safeDisplayName,
            AdditionalCharacterKind kind,
            String visualDescription) {
    }

    public record NormalizedCharacters(
            String text,
            boolean knownReferenceAdapted,
            List<AdditionalCharacter> characters) {
    }

    public record AdaptedText(String text, boolean knownReferenceAdapted) {
    }
}
