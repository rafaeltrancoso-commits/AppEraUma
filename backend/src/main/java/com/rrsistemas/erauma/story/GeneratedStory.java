package com.rrsistemas.erauma.story;

import java.util.List;

public record GeneratedStory(
        String title,
        String summary,
        NarrativeArc narrativeArc,
        List<GeneratedChapter> chapters,
        GenerationType generationType,
        String provider,
        String model,
        Integer inputTokens,
        Integer outputTokens,
        long durationMs
) {
    public GeneratedStory(String title, String summary, List<GeneratedChapter> chapters) {
        this(title, summary, new NarrativeArc(
                "O personagem e o lugar sao apresentados rapidamente.",
                "Uma situacao simples move a aventura.",
                "A situacao principal e resolvida com fechamento acolhedor."), chapters, GenerationType.MOCK, "mock", "mock", null, null, 0);
    }

    public String content() {
        return chapters.stream()
                .map(chapter -> "Capítulo " + chapter.number() + "\n" + chapter.title() + "\n\n" + chapter.content())
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
    }
}
