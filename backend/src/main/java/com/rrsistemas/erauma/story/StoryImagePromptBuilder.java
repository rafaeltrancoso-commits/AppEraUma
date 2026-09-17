package com.rrsistemas.erauma.story;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class StoryImagePromptBuilder {
    static final String STRATEGY_VERSION = "narrative-safe-v2";
    private static final Set<String> INSTRUCTION_MARKERS = Set.of(
            "ignore as regras", "ignore todas", "instruções anteriores", "instrucoes anteriores",
            "system:", "assistant:", "developer:", "novo prompt", "nova instrucao", "nova instrução",
            "desenhe outra coisa");
    private static final List<String> CENTRAL_OBJECTS = List.of(
            "bicicleta", "foguete", "brinquedo", "escorregador", "bola", "livro", "barco", "avião",
            "aviao", "trem", "carro", "castelo", "dinossauro", "violão", "violao", "pipa", "patins");
    private static final List<String> SETTINGS = List.of(
            "parque", "lua", "espaço", "espaco", "floresta", "praia", "escola", "jardim", "quintal",
            "casa", "cozinha", "quarto", "sala", "fazenda", "montanha", "castelo", "nave", "foguete");

    private final StoryVisualStyle visualStyle;
    private final StoryCharacterReferencePolicy characterPolicy;

    public StoryImagePromptBuilder(StoryVisualStyle visualStyle, StoryCharacterReferencePolicy characterPolicy) {
        this.visualStyle = visualStyle;
        this.characterPolicy = characterPolicy;
    }

    public PromptPlan normalPrompt(Story story, StoryImage image) {
        StorySceneSpecification specification = specification(story, image.getImageType(), image.getChapterStart(), image.getChapterEnd(), image.getSortOrder());
        String prompt = commonPrompt(story, specification, false)
                + formatRules(image.getVisualFormat())
                + "\nCENA ESTRUTURADA:\n" + specificationBlock(specification)
                + "\nrepresentedChapters: " + representedChapters(image.getImageType(), image.getChapterStart(), image.getChapterEnd());
        return plan(prompt, specification);
    }

    public PromptPlan safePrompt(Story story, StoryImageType type, Integer chapterStart, Integer chapterEnd, int sortOrder) {
        StorySceneSpecification specification = specification(story, type, chapterStart, chapterEnd, sortOrder);
        String prompt = """
                Crie uma ilustração infantil totalmente apropriada para todas as idades, em estilo cartoon tradicional, alegre, acolhedor e não realista.
                Preserve os elementos narrativos inofensivos abaixo. Adapte apenas referências externas reconhecidas para a identidade original descrita na bíblia visual.
                Não represente violência gráfica, ferimentos, medo intenso, conteúdo adulto, nudez, marcas, logotipos, assinaturas, texto ou balões de fala.
                """ + commonPrompt(story, specification, true)
                + singleSceneRules()
                + "\nCENA SEGURA ESTRUTURADA:\n" + specificationBlock(specification)
                + "\nrepresentedChapters: " + representedChapters(type, chapterStart, chapterEnd);
        return plan(prompt, specification);
    }

    public StorySceneSpecification specification(Story story, StoryImageType type, Integer chapterStart, Integer chapterEnd, int sortOrder) {
        List<StoryChapter> chapters = story.getChapters().stream()
                .sorted(Comparator.comparingInt(StoryChapter::getChapterNumber)).toList();
        String selectedNarrative = type == StoryImageType.COVER
                ? joinNarrative(chapters, 1, Math.min(4, chapters.size()))
                : joinNarrative(chapters, chapterStart == null ? 1 : chapterStart, chapterEnd == null ? 1 : chapterEnd);
        String adaptedNarrative = safeNarrative(characterPolicy.adaptKnownReferencesInText(selectedNarrative).text(), 760);
        String adaptedTheme = safeNarrative(characterPolicy.adaptKnownReferencesInText(story.getTheme()).text(), 180);
        String setting = setting(story.getPlace(), adaptedNarrative, adaptedTheme);
        String centralObject = centralObject(adaptedTheme + " " + adaptedNarrative);
        String action = type == StoryImageType.COVER
                ? "representar o tema central " + firstNonBlank(adaptedTheme, "da aventura") + " no momento visual mais marcante da narrativa"
                : firstNonBlank(adaptedNarrative, "mostrar a ação concreta correspondente aos capítulos selecionados");
        String characters = canonicalCharacters(story);
        boolean adapted = characterPolicy.containsAdaptedMarker(story.getOtherCharacters())
                || characterPolicy.adaptKnownReferencesInText(selectedNarrative).knownReferenceAdapted();
        List<String> mustInclude = new ArrayList<>();
        mustInclude.add("o local " + setting);
        mustInclude.add("a ação principal descrita");
        if (!centralObject.isBlank()) mustInclude.add("o objeto central " + centralObject);
        mustInclude.add("o mesmo protagonista e o mesmo acompanhante da bíblia visual");
        List<String> mustNotInclude = new ArrayList<>(List.of(
                "personagens adicionais não mencionados", "troca de gênero ou apresentação visual entre cenas",
                "roupas ou cores diferentes da bíblia visual", "marcas, logotipos ou personagens de franquias"));
        String lowerNarrative = (adaptedTheme + " " + adaptedNarrative + " " + setting).toLowerCase(Locale.ROOT);
        if (!containsAny(lowerNarrative, "casa", "sala", "cozinha", "quarto", "lar")) {
            mustNotInclude.add("cena doméstica genérica de leitura, cozinha ou desenho dentro de casa");
        }
        return new StorySceneSpecification(setting, timeOfDay(adaptedNarrative), characters,
                maximumCharacterCount(story), action, centralObject, mood(adaptedNarrative),
                mustInclude, mustNotInclude, adapted);
    }

    private String commonPrompt(Story story, StorySceneSpecification specification, boolean safePrompt) {
        return "\n\n" + visualStyle.cartoonPrompt() + "\n\n"
                + "VERSÃO DA ESTRATÉGIA: " + STRATEGY_VERSION + "\n"
                + "BÍBLIA VISUAL CANÔNICA:\n" + specification.characters() + "\n"
                + "ROUPA FIXA:\n" + outfit(story) + "\n"
                + "CONTINUIDADE OBRIGATÓRIA:\nMantenha exatamente o mesmo protagonista e acompanhante, idade aparente, apresentação visual, cabelo, tom de pele quando informado, rosto, roupa, cores, proporções e acessórios em todas as imagens. Não troque gênero ou apresentação visual. Não adicione irmãos, pais ou figurantes não mencionados. A consistência é textual; a API atual não recebe imagem de referência nem seed.\n"
                + (safePrompt ? "A identidade adaptada é a única permitida; não restaure nomes, roupas, símbolos ou poderes de franquias.\n" : "")
                + "QUANTIDADE MÁXIMA DE PERSONAGENS VISÍVEIS: " + specification.maximumCharacterCount() + ".\n";
    }

    private String specificationBlock(StorySceneSpecification spec) {
        return "setting: " + spec.setting() + "\n"
                + "timeOfDay: " + spec.timeOfDay() + "\n"
                + "characters: " + spec.characters() + "\n"
                + "mainAction: " + spec.mainAction() + "\n"
                + "centralObject: " + firstNonBlank(spec.centralObject(), "nenhum objeto adicional") + "\n"
                + "mood: " + spec.mood() + "\n"
                + "mustInclude: " + String.join("; ", spec.mustInclude()) + "\n"
                + "mustNotInclude: " + String.join("; ", spec.mustNotInclude()) + "\n"
                + "Represente exatamente esta ação e estes elementos; não substitua por uma atividade doméstica genérica.";
    }

    private String representedChapters(StoryImageType type, Integer start, Integer end) {
        if (type == StoryImageType.COVER || start == null || end == null) return "cover-theme";
        return start.equals(end) ? start.toString() : start + "-" + end;
    }

    private String canonicalCharacters(Story story) {
        List<String> descriptions = new ArrayList<>();
        if (story.getCharacters() == null || story.getCharacters().isEmpty()) {
            descriptions.add(safeCharacterDescription(CharacterVisualProfile.from(story).toPromptText()));
        } else {
            story.getCharacters().forEach(item -> descriptions.add("POSIÇÃO " + item.getSelectionOrder() + " (" + item.getRole() + "): "
                    + safeCharacterDescription(item.getVisualDescription())));
        }
        if (story.getOtherCharacters() != null && !story.getOtherCharacters().isBlank()) {
            StoryCharacterReferencePolicy.AdaptedText adapted = characterPolicy.adaptKnownReferencesInText(story.getOtherCharacters());
            descriptions.add("ACOMPANHANTE EXTERNO: " + safeNarrative(adapted.text(), 500)
                    + ". Manter a mesma identidade, apresentação, roupa e paleta em todas as imagens.");
        }
        return String.join("\n", descriptions);
    }

    private String safeCharacterDescription(String value) {
        String safe = safeNarrative(value, 600);
        return safe.isBlank()
                ? "personagem cadastrado, com aparência infantil segura e identidade visual consistente em todas as imagens"
                : safe;
    }

    private int maximumCharacterCount(Story story) {
        int registered = story.getCharacters() == null || story.getCharacters().isEmpty() ? 1 : story.getCharacters().size();
        int additional = story.getOtherCharacters() == null || story.getOtherCharacters().isBlank()
                ? 0 : Math.min(3, story.getOtherCharacters().split("\\s*(?:,|;)\\s*").length);
        return Math.max(1, Math.min(4, registered + additional));
    }

    private String setting(String provided, String narrative, String theme) {
        String safeProvided = safeSimplePhrase(provided, 100);
        if (!safeProvided.isBlank()) return safeProvided;
        String source = (theme + " " + narrative).toLowerCase(Locale.ROOT);
        return SETTINGS.stream().filter(source::contains).findFirst().orElse("local específico descrito nos capítulos");
    }

    private String centralObject(String source) {
        String lower = source.toLowerCase(Locale.ROOT);
        return CENTRAL_OBJECTS.stream().filter(lower::contains).findFirst().orElse("");
    }

    private String timeOfDay(String source) {
        String lower = source.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "noite", "noturno", "luar")) return "noite";
        if (containsAny(lower, "tarde", "pôr do sol", "por do sol")) return "tarde";
        if (containsAny(lower, "manhã", "manha", "amanhecer")) return "manhã";
        return "período do dia coerente com os capítulos";
    }

    private String mood(String source) {
        String lower = source.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "feliz", "alegre", "sorriu", "sorriso")) return "alegria e afeto";
        if (containsAny(lower, "coragem", "coraj", "superou")) return "coragem acolhedora";
        if (containsAny(lower, "curios", "descob")) return "curiosidade e descoberta";
        return "acolhimento e aventura segura";
    }

    private String joinNarrative(List<StoryChapter> chapters, int start, int end) {
        return chapters.stream().filter(chapter -> chapter.getChapterNumber() >= start && chapter.getChapterNumber() <= end)
                .map(chapter -> safeNarrative(chapter.getTitle(), 100) + ": " + safeNarrative(chapter.getContent(), 320))
                .reduce((left, right) -> left + " " + right).orElse("");
    }

    private String safeSimplePhrase(String value, int max) {
        String safe = safeNarrative(value, max);
        if (safe.isBlank() || INSTRUCTION_MARKERS.stream().anyMatch(marker -> safe.toLowerCase(Locale.ROOT).contains(marker))) {
            return "";
        }
        return safe;
    }

    private String safeNarrative(String value, int max) {
        if (value == null) return "";
        String clean = value.replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("(?i)(?:###|>>>|---|```)", " ")
                .replaceAll("(?iu)\\b(?:system|assistant|developer)\\s*:", " ")
                .replaceAll("\\s{2,}", " ").trim();
        for (String marker : INSTRUCTION_MARKERS) {
            if (clean.toLowerCase(Locale.ROOT).contains(marker)) return "";
        }
        return clean.length() <= max ? clean : clean.substring(0, max).trim();
    }

    private String formatRules(StoryImageFormat format) {
        return format == StoryImageFormat.COMIC_THREE_PANELS ? comicRules() : singleSceneRules();
    }

    private String singleSceneRules() {
        return "\nFORMATO SINGLE_SCENE OBRIGATÓRIO: uma única cena ocupando toda a imagem; sem colagem, quadros adicionais, texto, título, letras, números, assinatura ou balões.";
    }

    private String comicRules() {
        return "\nFORMATO COMIC_THREE_PANELS: exatamente três quadros consecutivos, mantendo os mesmos personagens e objetos, sem texto ou personagens extras.";
    }

    private String outfit(Story story) {
        return "roupas práticas e confortáveis em " + colorFor(story.getId())
                + ", adequadas à idade e idênticas em todas as imagens";
    }

    private String colorFor(UUID storyId) {
        String[] colors = {"azul claro", "verde folha", "amarelo suave", "vermelho coral", "lilás suave", "turquesa"};
        return colors[Math.floorMod(storyId.hashCode(), colors.length)];
    }

    private PromptPlan plan(String prompt, StorySceneSpecification specification) {
        if (prompt == null || prompt.isBlank() || prompt.length() < 120) {
            throw new IllegalArgumentException("Prompt de ilustração vazio ou genérico demais.");
        }
        return new PromptPlan(prompt, STRATEGY_VERSION, shortHash(prompt), specification);
    }

    private String shortHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 6);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private boolean containsAny(String value, String... terms) {
        for (String term : terms) if (value.contains(term)) return true;
        return false;
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record PromptPlan(
            String text,
            String strategyVersion,
            String promptHash,
            StorySceneSpecification specification) {
    }
}
