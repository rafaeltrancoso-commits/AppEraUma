package com.rrsistemas.erauma.story;

import java.util.Base64;
import java.util.Arrays;
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
        if (prompt.contains("mock-fail-scene-1") && prompt.contains("capitulos 1-2")) {
            throw new AiGenerationException("Falha mockada para validar falha parcial.");
        }
        if (prompt.contains("mock-corrupt-scene-1") && prompt.contains("capitulos 1-2")) {
            return new GeneratedStoryImage(Arrays.copyOf(PNG_1X1, PNG_1X1.length / 2), "mock-image", "1x1", "low", 1);
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
