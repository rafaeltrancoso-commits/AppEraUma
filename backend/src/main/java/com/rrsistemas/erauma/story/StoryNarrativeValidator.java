package com.rrsistemas.erauma.story;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class StoryNarrativeValidator {
    private static final Pattern DUPLICATED_WORD = Pattern.compile("(?iu)(?<![\\p{L}\\p{N}])(\\p{L}{2,})\\s+\\1(?![\\p{L}\\p{N}])");
    private static final Pattern COMPOUND_NAMES_WITH_SINGULAR_VERB = Pattern.compile("(?u)\\b\\p{Lu}[\\p{L}'’-]*\\s+e\\s+\\p{Lu}[\\p{L}'’-]*\\s+(?:estava|ficou|foi|correu|chegou)\\b");
    private static final Pattern PLURAL_SUBJECT_WITH_SINGULAR_VERB = Pattern.compile("(?iu)\\b(?:as|os)\\s+[\\p{L}'’-]+s\\s+(?:estava|ficou|foi|correu|chegou)\\b");
    private static final Pattern MALFORMED_CHARACTER_INTRODUCTION = Pattern.compile("(?iu)\\b(?:a amiga|o amigo|a menina|o menino|sua amiga|seu amigo)\\s+chama\\s+\\p{L}+[,.]?\\s+(?:chegou|estava|estavam|ficou|ficaram|foi|foram|correu|correram)\\b");
    private static final Pattern OBVIOUS_TEMPORAL_MISMATCH = Pattern.compile("(?iu)\\bamanha\\s+(?:abriu|encontrou|fez|foi|pegou|voltou)\\b");

    public void validate(GeneratedStory story) {
        validate(story, null);
    }

    public void validate(GeneratedStory story, StoryLength length) {
        if (story == null) {
            reject("STORY_MISSING", "Historia ausente.");
        }
        if (blank(story.title())) {
            reject("TITLE_MISSING", "Titulo ausente.");
        }
        validateLanguageAnomalies(story.title());
        validateLanguageAnomalies(story.summary());
        if (story.narrativeArc() == null) {
            reject("NARRATIVE_ARC_MISSING", "Arco narrativo ausente.");
        }
        if (blank(story.narrativeArc().setup())) {
            reject("SETUP_MISSING", "Inicio narrativo ausente.");
        }
        if (blank(story.narrativeArc().centralSituation())) {
            reject("CENTRAL_SITUATION_MISSING", "Situacao central ausente.");
        }
        if (blank(story.narrativeArc().protagonistAction())) {
            reject("PROTAGONIST_ACTION_MISSING", "Acao do protagonista ausente.");
        }
        if (blank(story.narrativeArc().resolution())) {
            reject("RESOLUTION_MISSING", "Resolucao narrativa ausente.");
        }
        if (blank(story.narrativeArc().closingScene())) {
            reject("CLOSING_SCENE_MISSING", "Cena final ausente.");
        }
        List<GeneratedChapter> chapters = story.chapters();
        if (chapters == null || chapters.isEmpty()) {
            reject("CHAPTERS_EMPTY", "Resposta sem capitulos.");
        }
        if (length != null && chapters.size() != StoryLengthSpec.of(length).expectedChapters()) {
            reject("CHAPTER_COUNT_INVALID", "Quantidade de capitulos invalida.");
        }
        for (GeneratedChapter chapter : chapters) {
            if (chapter == null || chapter.number() <= 0 || blank(chapter.title()) || blank(chapter.content())) {
                reject("CHAPTER_INVALID", "Capitulo invalido.");
            }
            validateCompleteSentence(chapter.content());
            validateLanguageAnomalies(chapter.content());
        }
        GeneratedChapter lastChapter = chapters.get(chapters.size() - 1);
        if (blank(lastChapter.content())) {
            reject("LAST_CHAPTER_EMPTY", "Ultimo capitulo vazio.");
        }
        validateCompleteEnding(lastChapter.content());
    }

    public void validateCharacters(GeneratedStory story, List<StoryCharacterPrompt> characters) {
        if (characters == null || characters.isEmpty()) return;
        String fullText = (story.title() + " " + story.summary() + " "
                + story.chapters().stream().map(GeneratedChapter::content).reduce("", (left, right) -> left + " " + right))
                .toLowerCase(Locale.ROOT);
        for (StoryCharacterPrompt character : characters) {
            String displayName = character.nickname() == null || character.nickname().isBlank() ? character.name() : character.nickname();
            String firstName = displayName == null ? "" : displayName.trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
            if (!firstName.isBlank() && !fullText.matches("(?s).*\\b" + java.util.regex.Pattern.quote(firstName) + "\\b.*")) {
                reject("CHARACTER_MISSING", "Personagem selecionado ausente da narrativa.");
            }
        }
    }

    private void validateCompleteEnding(String content) {
        String text = content == null ? "" : content.trim();
        if (text.isBlank()) {
            reject("LAST_BLOCK_EMPTY", "Ultimo bloco vazio.");
        }
        if (!text.matches("(?s).*[.!?…]$")) {
            reject("STORY_ENDING_WITHOUT_FINAL_PUNCTUATION", "Historia termina sem pontuacao final.");
        }
        String normalized = text.toLowerCase()
                .replaceAll("[.!?…]+$", "")
                .replaceAll("\\s+", " ")
                .trim();
        String[] danglingEndings = {
                " com o", " com a", " para o", " para a", " porque", " e entao", " e então",
                " quando", " enquanto", " mas", " pois", " que", " de", " da", " do", " das", " dos",
                " em", " no", " na", " nos", " nas", " por", " pelo", " pela"
        };
        for (String ending : danglingEndings) {
            if (normalized.endsWith(ending)) {
                reject("STORY_ENDING_DANGLING_PHRASE", "Historia termina no meio de uma frase.");
            }
        }
    }

    private void validateCompleteSentence(String content) {
        String text = content == null ? "" : content.trim();
        if (!text.matches("(?s).*[.!?…][\\\"'’)]*$")) {
            reject("INCOMPLETE_SENTENCE", "Bloco narrativo termina com frase incompleta.");
        }
    }

    private void validateLanguageAnomalies(String content) {
        if (blank(content)) return;
        String normalized = content.replace('ã', 'a').replace('Ã', 'A');
        if (DUPLICATED_WORD.matcher(content).find()) {
            reject("ACCIDENTAL_WORD_REPETITION", "Texto contem palavra duplicada por acidente.");
        }
        if (COMPOUND_NAMES_WITH_SINGULAR_VERB.matcher(content).find()
                || PLURAL_SUBJECT_WITH_SINGULAR_VERB.matcher(content).find()) {
            reject("OBVIOUS_SUBJECT_VERB_DISAGREEMENT", "Texto contem discordancia evidente entre sujeito e verbo.");
        }
        if (MALFORMED_CHARACTER_INTRODUCTION.matcher(content).find()) {
            reject("MALFORMED_CHARACTER_INTRODUCTION", "Texto contem apresentacao malformada de personagem.");
        }
        if (OBVIOUS_TEMPORAL_MISMATCH.matcher(normalized).find()) {
            reject("OBVIOUS_TEMPORAL_MISMATCH", "Texto contem incoerencia temporal evidente.");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private void reject(String reason, String message) {
        throw new StoryNarrativeValidationException(reason, message);
    }
}
