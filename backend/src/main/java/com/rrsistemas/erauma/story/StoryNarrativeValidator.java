package com.rrsistemas.erauma.story;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class StoryNarrativeValidator {
    public void validate(GeneratedStory story) {
        if (story == null) {
            reject("STORY_MISSING", "Historia ausente.");
        }
        if (blank(story.title())) {
            reject("TITLE_MISSING", "Titulo ausente.");
        }
        if (story.narrativeArc() == null) {
            reject("NARRATIVE_ARC_MISSING", "Arco narrativo ausente.");
        }
        if (blank(story.narrativeArc().setup())) {
            reject("SETUP_MISSING", "Inicio narrativo ausente.");
        }
        if (blank(story.narrativeArc().centralSituation())) {
            reject("CENTRAL_SITUATION_MISSING", "Situacao central ausente.");
        }
        if (blank(story.narrativeArc().resolution())) {
            reject("RESOLUTION_MISSING", "Resolucao narrativa ausente.");
        }
        List<GeneratedChapter> chapters = story.chapters();
        if (chapters == null || chapters.isEmpty()) {
            reject("CHAPTERS_EMPTY", "Resposta sem capitulos.");
        }
        for (GeneratedChapter chapter : chapters) {
            if (chapter == null || chapter.number() <= 0 || blank(chapter.title()) || blank(chapter.content())) {
                reject("CHAPTER_INVALID", "Capitulo invalido.");
            }
        }
        GeneratedChapter lastChapter = chapters.get(chapters.size() - 1);
        if (blank(lastChapter.content())) {
            reject("LAST_CHAPTER_EMPTY", "Ultimo capitulo vazio.");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private void reject(String reason, String message) {
        throw new StoryNarrativeValidationException(reason, message);
    }
}
