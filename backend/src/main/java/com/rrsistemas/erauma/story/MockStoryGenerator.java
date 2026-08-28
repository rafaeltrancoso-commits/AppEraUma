package com.rrsistemas.erauma.story;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MockStoryGenerator implements StoryGenerator {
    @Override
    public GeneratedStory generate(StoryGenerationRequest request) {
        String protagonist = safe(request.mainCharacterName(), safe(request.childName(), "A criança"));
        String secondCharacter = safe(request.secondCharacterName(), "");
        String companion = secondCharacter.isBlank() ? "" : " e " + secondCharacter;
        String animal = safe(request.favoriteAnimal(), "");
        String place = safe(request.place(), safe(request.sourceMomentLocation(), "jardim encantado"));
        String theme = safe(request.theme(), "uma descoberta importante");
        String ageText = ageText(request.childBirthDate());
        String title = title(protagonist, companion, animal, theme, request);
        String animalSummary = animal.isBlank() ? "" : ", " + animal.toLowerCase();
        String summary = "Uma história " + styleLabel(request.style()) + " sobre " + protagonist + companion + animalSummary + " e " + theme.toLowerCase() + ".";
        int chapters = switch (request.length()) {
            case SHORT -> 1;
            case MEDIUM -> 2;
            case LONG -> 3;
        };
        List<GeneratedChapter> generatedChapters = new ArrayList<>();
        for (int number = 1; number <= chapters; number++) {
            generatedChapters.add(new GeneratedChapter(number, chapterTitle(number, request.style(), place), chapterContent(number, chapters, protagonist, companion, ageText, animal, place, theme, request)));
        }
        return new GeneratedStory(title, summary, generatedChapters);
    }

    private String title(String protagonist, String companion, String animal, String theme, StoryGenerationRequest request) {
        if (request.sourceMomentTitle() != null && !request.sourceMomentTitle().isBlank()) {
            return protagonist + companion + " e a aventura de " + request.sourceMomentTitle().trim().toLowerCase();
        }
        if (animal.isBlank()) {
            return protagonist + companion + " e a descoberta sobre " + theme;
        }
        return protagonist + companion + " e o " + animal + " que aprendeu sobre " + theme;
    }

    private String chapterTitle(int number, StoryStyle style, String place) {
        return switch (number) {
            case 1 -> "Um convite em " + place;
            case 2 -> style == StoryStyle.BEDTIME ? "A luz pequenina" : "O plano brilhante";
            default -> "A grande lembrança";
        };
    }

    private String chapterContent(int number, int totalChapters, String protagonist, String companion, String ageText, String animal, String place, String theme, StoryGenerationRequest request) {
        String source = request.sourceMomentDescription() == null || request.sourceMomentDescription().isBlank()
                ? ""
                : " Tudo começou com uma lembrança real: " + request.sourceMomentDescription().trim();
        String tone = switch (request.style()) {
            case ADVENTURE -> "com coragem no bolso e curiosidade no olhar";
            case FUNNY -> "dando risadinhas até das pequenas trapalhadas";
            case EDUCATIONAL -> "observando, perguntando e aprendendo uma coisa nova de cada vez";
            case FANTASY -> "seguindo brilhos dourados que dançavam pelo caminho";
            case BEDTIME -> "falando baixinho, como quem conversa com a lua";
        };
        if (number == 1) {
            if (totalChapters == 1) {
                String friend = animal.isBlank() ? "uma luz pequena" : "um " + animal.toLowerCase() + " gentil";
                return protagonist + companion + ageText + " chegou a " + place + " " + tone + ".\n\n"
                        + "De repente, " + friend + " pediu ajuda para encontrar o caminho de volta.\n\n"
                        + protagonist + " olhou com calma, pensou um pouquinho e seguiu as pegadas brilhantes pelo chao.\n\n"
                        + "No fim do caminho, todos encontraram a resposta para " + theme.toLowerCase() + ". O lugar ficou tranquilo, " + protagonist + " sorriu e voltou para casa com o coracao leve.";
            }
            if (animal.isBlank()) {
                return protagonist + companion + ageText + " chegou a " + place + " " + tone + ". Ali, percebeu que " + theme.toLowerCase() + " podia virar uma aventura segura e bonita." + source;
            }
            return protagonist + companion + ageText + " chegou a " + place + " " + tone + ". Ao lado de um " + animal.toLowerCase() + " gentil, percebeu que " + theme.toLowerCase() + " podia virar uma aventura segura e bonita." + source;
        }
        if (number == 2) {
            if (totalChapters == 2) {
                if (animal.isBlank()) {
                    return protagonist + " seguiu uma pista pequena, tentou uma ideia simples e descobriu o que precisava fazer. Quando resolveu " + theme.toLowerCase() + ", respirou feliz.\n\n"
                            + "A aventura terminou ali mesmo, com tudo no lugar e uma lembranca boa para contar depois.";
                }
                return "O " + animal.toLowerCase() + " mostrou uma pista pequena para " + protagonist + ". Juntos, eles tentaram uma ideia simples e descobriram o que precisava ser feito.\n\n"
                        + "Quando resolveram " + theme.toLowerCase() + ", a aventura terminou com um sorriso, tudo no lugar e uma lembranca boa para contar depois.";
            }
            if (animal.isBlank()) {
                return protagonist + " descobriu que toda emoção tem um tamanho, uma cor e um jeito de ser cuidada. Com calma, respirou fundo, deu nome ao que sentia e encontrou uma ideia simples para seguir em frente.";
            }
            return "O " + animal.toLowerCase() + " mostrou para " + protagonist + " que toda emoção tem um tamanho, uma cor e um jeito de ser cuidada. Juntos, eles respiraram fundo, deram nome ao que sentiam e encontraram uma ideia simples para seguir em frente.";
        }
        return "Quando o dia terminou, " + protagonist + " voltou para casa com uma lembrança guardada no coração: histórias nascem quando a família presta atenção aos pequenos momentos. E " + theme.toLowerCase() + " já não parecia tão difícil assim.";
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String ageText(LocalDate birthDate) {
        if (birthDate == null) {
            return "";
        }
        int years = Period.between(birthDate, LocalDate.now()).getYears();
        return years > 0 ? ", com " + years + " anos," : "";
    }

    private String styleLabel(StoryStyle style) {
        return switch (style) {
            case ADVENTURE -> "de aventura";
            case FUNNY -> "engraçada";
            case EDUCATIONAL -> "educativa";
            case FANTASY -> "de fantasia";
            case BEDTIME -> "para dormir";
        };
    }
}
