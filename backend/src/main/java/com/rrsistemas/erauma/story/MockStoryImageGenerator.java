package com.rrsistemas.erauma.story;

import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Service;

@Service
public class MockStoryImageGenerator implements StoryImageGenerator {
    private static final byte[] PNG_1X1 = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=");
    private final List<String> prompts = new CopyOnWriteArrayList<>();

    @Override
    public GeneratedStoryImage generate(String prompt) {
        prompts.add(prompt);
        if (prompt.contains("mock-fail-scene-1") && prompt.contains("Cena do capitulo 1")) {
            throw new AiGenerationException("Falha mockada para validar falha parcial.");
        }
        return new GeneratedStoryImage(PNG_1X1, "mock-image", "1x1", "low", 1);
    }

    public List<String> prompts() {
        return List.copyOf(prompts);
    }

    public void clearPrompts() {
        prompts.clear();
    }
}
