package com.rrsistemas.erauma.story;

import static org.assertj.core.api.Assertions.assertThat;

import com.rrsistemas.erauma.child.ChildProfile;
import com.rrsistemas.erauma.child.ChildRequest;
import com.rrsistemas.erauma.family.Family;
import com.rrsistemas.erauma.user.AppUser;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class StoryImagePromptBuilderTest {
    private final StoryCharacterReferencePolicy policy = new StoryCharacterReferencePolicy();
    private final StoryImagePromptBuilder builder = new StoryImagePromptBuilder(new StoryVisualStyle(), policy);

    @Test
    void normalAndSafePromptsKeepSettingActionObjectAndCharacterBible() {
        Story story = story("Homem-Aranha");
        StoryImage scene = image(story, StoryImageType.SCENE, 1, 2, 1);

        var normal = builder.normalPrompt(story, scene);
        var safe = builder.safePrompt(story, StoryImageType.SCENE, 1, 2, 1);

        assertThat(normal.specification().setting()).isEqualTo("Parque");
        assertThat(normal.specification().centralObject()).isEqualTo("bicicleta");
        assertThat(normal.specification().mainAction()).contains("bicicleta", "parque");
        assertThat(safe.specification().setting()).isEqualTo(normal.specification().setting());
        assertThat(safe.specification().centralObject()).isEqualTo(normal.specification().centralObject());
        assertThat(safe.specification().characters()).isEqualTo(normal.specification().characters());
        assertThat(safe.text()).contains("Parque", "bicicleta", "Nando", "personagem original")
                .doesNotContain("ambiente domestico acolhedor e generico")
                .doesNotContainIgnoringCase("Homem-Aranha");
    }

    @Test
    void sceneRangesUseChaptersOneTwoThenThreeFour() {
        Story story = story(null);
        var first = builder.specification(story, StoryImageType.SCENE, 1, 2, 1);
        var second = builder.specification(story, StoryImageType.SCENE, 3, 4, 2);

        assertThat(first.mainAction()).contains("bicicleta", "ponte").doesNotContain("foguete");
        assertThat(second.mainAction()).contains("foguete", "lua").doesNotContain("bicicleta");
    }

    @Test
    void coverAndScenesReuseExactlyTheSameCanonicalCharacters() {
        Story story = story("Homem-Aranha");
        var cover = builder.specification(story, StoryImageType.COVER, null, null, 0);
        var first = builder.specification(story, StoryImageType.SCENE, 1, 2, 1);
        var second = builder.specification(story, StoryImageType.SCENE, 3, 4, 2);

        assertThat(cover.characters()).isEqualTo(first.characters()).isEqualTo(second.characters());
        assertThat(cover.maximumCharacterCount()).isEqualTo(2);
        assertThat(cover.knownReferenceAdapted()).isTrue();
        assertThat(cover.mainAction()).contains("bicicleta", "lua");
    }

    @Test
    void worksWithoutAdditionalCompanion() {
        Story story = story(null);
        var prompt = builder.normalPrompt(story, image(story, StoryImageType.COVER, null, null, 0));

        assertThat(prompt.specification().maximumCharacterCount()).isEqualTo(1);
        assertThat(prompt.text()).contains("Nando").doesNotContain("ACOMPANHANTE EXTERNO");
    }

    private Story story(String externalCharacter) {
        AppUser user = new AppUser("Mãe", "mae-prompt@example.com", "hash");
        Family family = new Family("Família", user);
        ChildProfile child = new ChildProfile(family, new ChildRequest("Nando Teste", LocalDate.now().minusYears(7), "Nando", null, null, null, null, "castanho", "curto", null, "castanhos", "sorriso grande"));
        String safeExternal = policy.normalize(externalCharacter).text();
        StoryGenerateRequest request = new StoryGenerateRequest(child.getId(), null, "Nando", safeExternal,
                "Uma bicicleta mágica chega à lua", "Parque", null, StoryStyle.ADVENTURE, StoryLength.MEDIUM,
                StoryGenerationMode.ILLUSTRATED, List.of(child.getId()), safeExternal, "prompt-test");
        GeneratedStory generated = new GeneratedStory("A aventura", "Nando explora", List.of(
                new GeneratedChapter(1, "A bicicleta", "Nando pedala a bicicleta no parque e encontra uma ponte."),
                new GeneratedChapter(2, "A travessia", "Nando atravessa a ponte de bicicleta com coragem."),
                new GeneratedChapter(3, "O foguete", "Nando entra no foguete e parte rumo à lua."),
                new GeneratedChapter(4, "Na lua", "Nando pousa o foguete na lua e celebra a descoberta.")));
        Story story = new Story(family, child, null, request, generated, user);
        story.addCharacter(child, 1, CharacterVisualProfile.from(child).toPromptText());
        return story;
    }

    private StoryImage image(Story story, StoryImageType type, Integer start, Integer end, int order) {
        StoryChapter chapter = start == null ? null : story.getChapters().stream()
                .filter(item -> item.getChapterNumber() == start).findFirst().orElseThrow();
        StoryImage image = new StoryImage(story, chapter, type, "gpt-image-2", "1024x1024", "medium", order, start, end, null);
        image.setVisualFormat(StoryImageFormat.SINGLE_SCENE);
        return image;
    }
}
