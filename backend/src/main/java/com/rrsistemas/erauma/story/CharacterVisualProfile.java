package com.rrsistemas.erauma.story;

import com.rrsistemas.erauma.child.ChildProfile;
import com.rrsistemas.erauma.child.HairTexture;
import com.rrsistemas.erauma.child.SkinTone;
import com.rrsistemas.erauma.child.VisualPresentation;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

public record CharacterVisualProfile(
        String name,
        Integer age,
        VisualPresentation visualPresentation,
        SkinTone skinTone,
        String hairColor,
        String hairLength,
        HairTexture hairTexture,
        String eyeColor,
        String specialFeatures
) {
    public static CharacterVisualProfile from(Story story) {
        ChildProfile child = story.getChild();
        return new CharacterVisualProfile(
                story.getMainCharacterName(),
                child == null ? null : age(child.getBirthDate()),
                child == null ? null : child.getVisualPresentation(),
                child == null ? null : child.getSkinTone(),
                child == null ? null : child.getHairColor(),
                child == null ? null : child.getHairLength(),
                child == null ? null : child.getHairTexture(),
                child == null ? null : child.getEyeColor(),
                child == null ? null : child.getSpecialFeatures());
    }

    public String toPromptText() {
        List<String> parts = new ArrayList<>();
        parts.add("nome " + clean(name));
        if (age != null) {
            parts.add(age + " anos");
        }
        if (visualPresentation != null && visualPresentation != VisualPresentation.UNSPECIFIED) {
            parts.add(visualPresentation == VisualPresentation.BOY ? "apresentacao visual: menino" : "apresentacao visual: menina");
        }
        if (skinTone != null && skinTone != SkinTone.UNSPECIFIED) {
            parts.add("tom de pele: " + skinToneLabel(skinTone));
        }
        add(parts, "cabelo cor", hairColor);
        add(parts, "cabelo comprimento", hairLength);
        if (hairTexture != null && hairTexture != HairTexture.OTHER_OR_UNSPECIFIED) {
            parts.add("cabelo textura: " + hairTextureLabel(hairTexture));
        }
        add(parts, "olhos", eyeColor);
        add(parts, "detalhes especiais", specialFeatures);
        if (parts.size() == 1) {
            return "Perfil visual do protagonista: " + parts.get(0) + ". Nenhuma caracteristica fisica especifica foi informada pela familia; manter representacao infantil neutra e coerente entre as imagens, sem inventar etnia, genero ou deficiencia.";
        }
        return "Perfil visual do protagonista: " + String.join("; ", parts) + ". Manter exatamente este perfil de forma consistente em todas as imagens, sem transformar caracteristicas fisicas em piada, conflito ou julgamento.";
    }

    private static Integer age(LocalDate birthDate) {
        return birthDate == null ? null : Math.max(0, Period.between(birthDate, LocalDate.now()).getYears());
    }

    private static void add(List<String> parts, String label, String value) {
        if (value != null && !value.isBlank()) {
            parts.add(label + ": " + clean(value));
        }
    }

    private static String skinToneLabel(SkinTone value) {
        return switch (value) {
            case VERY_LIGHT -> "muito claro";
            case LIGHT -> "claro";
            case MEDIUM -> "medio";
            case BROWN -> "moreno";
            case DARK -> "escuro";
            case UNSPECIFIED -> "nao informado";
        };
    }

    private static String hairTextureLabel(HairTexture value) {
        return switch (value) {
            case STRAIGHT -> "liso";
            case WAVY -> "ondulado";
            case CURLY -> "cacheado";
            case COILY -> "crespo";
            case OTHER_OR_UNSPECIFIED -> "nao informado";
        };
    }

    private static String clean(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n\\t]+", " ").trim();
    }
}
