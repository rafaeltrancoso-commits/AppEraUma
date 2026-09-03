package com.rrsistemas.erauma.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.SocketTimeoutException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

class OpenAIStoryGeneratorTest {
    private static final String RESPONSES_URL = "https://api.openai.com/v1/responses";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesValidResponsesOutputTextWithoutRealApiCall() throws Exception {
        TestClient client = client();
        client.server.expect(requestTo(RESPONSES_URL))
                .andExpect(content().string(containsString("Super Nando")))
                .andExpect(content().string(containsString("Luna")))
                .andExpect(content().string(containsString("frases bem curtas")))
                .andExpect(content().string(containsString("3 a 7 anos")))
                .andExpect(content().string(containsString("Apresentacao")))
                .andExpect(content().string(containsString("Desenvolvimento")))
                .andExpect(content().string(containsString("Encerramento")))
                .andExpect(content().string(containsString("resolution")))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(responseWithText(validStoryJson("Nando encontrou uma luz tranquila. Ele ajudou a luz a voltar para casa. Depois, voltou feliz."))));

        GeneratedStory story = client.generator.generate(request(4, StoryLength.SHORT));

        assertThat(story.generationType()).isEqualTo(GenerationType.AI);
        assertThat(story.provider()).isEqualTo("openai");
        assertThat(story.narrativeArc().resolution()).contains("volta feliz");
        assertThat(story.chapters()).hasSize(2);
        assertThat(story.inputTokens()).isEqualTo(10);
        assertThat(story.outputTokens()).isEqualTo(20);
        client.server.verify();
    }

    @Test
    void parsesStructuredOutputEscapedNewlinesAsRealTextWithoutDoubleEscape() throws Exception {
        TestClient client = client();
        client.server.expect(requestTo(RESPONSES_URL))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(responseWithText(validStoryJson("O sol brilhava.\\\\n\\\\nDe repente, chegou uma onda. Nando devolveu a concha ao mar e sorriu."))));

        GeneratedStory story = client.generator.generate(request(4, StoryLength.SHORT));

        assertThat(story.summary()).isEqualTo("Resumo com\nlinha.");
        assertThat(story.chapters().get(0).content()).isEqualTo("O sol brilhava.\n\nDe repente, chegou uma onda. Nando devolveu a concha ao mar e sorriu.");
        assertThat(story.chapters().get(0).content()).doesNotContain("\\n");
        client.server.verify();
    }

    @Test
    void omitsAnimalFromPayloadWhenNotProvidedWithoutRealApiCall() throws Exception {
        TestClient client = client();
        client.server.expect(requestTo(RESPONSES_URL))
                .andExpect(content().string(not(containsString("favoriteAnimal"))))
                .andExpect(content().string(not(containsString("Animal: null"))))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(responseWithText(validStoryJson("Super Nando encontrou uma luz tranquila e ajudou a luz a voltar. A aventura terminou com tudo em paz."))));

        GeneratedStory story = client.generator.generate(requestWithoutAnimal());

        assertThat(story.generationType()).isEqualTo(GenerationType.AI);
        client.server.verify();
    }

    @Test
    void ageFourUsesThreeToFourGuidanceWithoutRealApiCall() throws Exception {
        TestClient client = client();
        client.server.expect(requestTo(RESPONSES_URL))
                .andExpect(content().string(containsString("uma ideia por frase")))
                .andExpect(content().string(containsString("sequencia linear")))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(responseWithText(validStoryJson())));

        client.generator.generate(request(4, StoryLength.SHORT));

        client.server.verify();
    }

    @Test
    void ageSixUsesFiveToSevenGuidanceWithoutRealApiCall() throws Exception {
        TestClient client = client();
        client.server.expect(requestTo(RESPONSES_URL))
                .andExpect(content().string(containsString("pequenos misterios")))
                .andExpect(content().string(containsString("causa e consequencia")))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(responseWithText(validStoryJson("Nando encontrou uma luz tranquila. Ele ajudou a luz a voltar para casa. Depois, voltou feliz.", 4))));

        client.generator.generate(request(6, StoryLength.MEDIUM));

        client.server.verify();
    }

    @Test
    void structuredOutputRequiresResolutionInSchemaWithoutRealApiCall() throws Exception {
        TestClient client = client();
        client.server.expect(requestTo(RESPONSES_URL))
                .andExpect(content().string(containsString("\"narrativeArc\"")))
                .andExpect(content().string(containsString("\"required\":[\"setup\",\"centralSituation\",\"protagonistAction\",\"resolution\",\"closingScene\"]")))
                .andExpect(content().string(containsString("\"maxItems\":6")))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(responseWithText(validStoryJson())));

        client.generator.generate(request(4, StoryLength.SHORT));

        client.server.verify();
    }

    @Test
    void sendsTokenLimitsAndChapterCountsForEveryLengthWithoutRealApiCall() throws Exception {
        TestClient client = client();
        client.server.expect(requestTo(RESPONSES_URL))
                .andExpect(content().string(containsString("\"max_output_tokens\":1400")))
                .andExpect(content().string(containsString("Gere exatamente 2 capitulos")))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(responseWithText(validStoryJson("Final completo.", 2))));
        client.server.expect(requestTo(RESPONSES_URL))
                .andExpect(content().string(containsString("\"max_output_tokens\":2500")))
                .andExpect(content().string(containsString("Gere exatamente 4 capitulos")))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(responseWithText(validStoryJson("Final completo.", 4))));
        client.server.expect(requestTo(RESPONSES_URL))
                .andExpect(content().string(containsString("\"max_output_tokens\":4000")))
                .andExpect(content().string(containsString("Gere exatamente 6 capitulos")))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(responseWithText(validStoryJson("Final completo.", 6))));

        assertThat(client.generator.generate(request(4, StoryLength.SHORT)).chapters()).hasSize(2);
        assertThat(client.generator.generate(request(4, StoryLength.MEDIUM)).chapters()).hasSize(4);
        assertThat(client.generator.generate(request(4, StoryLength.LONG)).chapters()).hasSize(6);

        client.server.verify();
    }

    @Test
    void retriesWhenChapterCountDoesNotMatchLengthWithoutRealApiCall() throws Exception {
        TestClient client = client();
        client.server.expect(requestTo(RESPONSES_URL))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(responseWithText(validStoryJson("Texto curto.", 2))));
        client.server.expect(requestTo(RESPONSES_URL))
                .andExpect(content().string(containsString("quantidade exata de capitulos")))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(responseWithText(validStoryJson("Texto corrigido.", 4))));

        GeneratedStory story = client.generator.generate(request(4, StoryLength.MEDIUM));

        assertThat(story.chapters()).hasSize(4);
        client.server.verify();
    }

    @Test
    void retriesWhenClosingSceneIsMissingWithoutRealApiCall() throws Exception {
        TestClient client = client();
        client.server.expect(requestTo(RESPONSES_URL))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(responseWithText(storyJsonWithoutClosingScene())));
        client.server.expect(requestTo(RESPONSES_URL))
                .andExpect(content().string(containsString("cena final")))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(responseWithText(validStoryJson())));

        GeneratedStory story = client.generator.generate(request(4, StoryLength.SHORT));

        assertThat(story.narrativeArc().closingScene()).isNotBlank();
        client.server.verify();
    }

    @Test
    void rejectsEmptyResolutionAndRetriesQualityOnceWithoutRealApiCall() throws Exception {
        TestClient client = client();
        client.server.expect(requestTo(RESPONSES_URL))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(responseWithText(storyJsonWithArc("", "Situacao", "Capitulo com texto."))));
        client.server.expect(requestTo(RESPONSES_URL))
                .andExpect(content().string(containsString("tentativa anterior")))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(responseWithText(storyJsonWithArc("", "Situacao", "Capitulo com texto."))));

        assertThatThrownBy(() -> client.generator.generate(request(4, StoryLength.SHORT)))
                .isInstanceOf(StoryNarrativeValidationException.class)
                .hasMessageContaining("resolution");

        client.server.verify();
    }

    @Test
    void rejectsEmptyChaptersAndRetriesQualityOnceWithoutRealApiCall() throws Exception {
        TestClient client = client();
        client.server.expect(ExpectedCount.times(2), requestTo(RESPONSES_URL))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(responseWithText("""
                                {"title":"Nando","summary":"Resumo","narrativeArc":{"setup":"Inicio","centralSituation":"Situacao","protagonistAction":"Nando ajudou.","resolution":"Resolucao","closingScene":"Cena final."},"chapters":[]}
                                """)));

        assertThatThrownBy(() -> client.generator.generate(request(4, StoryLength.SHORT)))
                .isInstanceOf(StoryNarrativeValidationException.class)
                .hasMessageContaining("cap");

        client.server.verify();
    }

    @Test
    void rejectsEmptyLastChapterAndRetriesQualityOnceWithoutRealApiCall() throws Exception {
        TestClient client = client();
        client.server.expect(ExpectedCount.times(2), requestTo(RESPONSES_URL))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(responseWithText(storyJsonWithArc("Resolucao", "Situacao", " "))));

        assertThatThrownBy(() -> client.generator.generate(request(4, StoryLength.SHORT)))
                .isInstanceOf(StoryNarrativeValidationException.class)
                .hasMessageContaining("Cap");

        client.server.verify();
    }

    @Test
    void validStoryDoesNotQualityRetryWithoutRealApiCall() throws Exception {
        TestClient client = client();
        client.server.expect(ExpectedCount.once(), requestTo(RESPONSES_URL))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(responseWithText(validStoryJson())));

        client.generator.generate(request(4, StoryLength.SHORT));

        client.server.verify();
    }

    @Test
    void storyNarrativeValidatorRejectsMissingPieces() {
        StoryNarrativeValidator validator = new StoryNarrativeValidator();
        GeneratedStory missingResolution = new GeneratedStory(
                "Titulo",
                "Resumo",
                new NarrativeArc("Inicio", "Situacao", ""),
                List.of(new GeneratedChapter(1, "Capitulo", "Texto final.")),
                GenerationType.AI,
                "openai",
                "gpt-test",
                null,
                null,
                0);

        assertThatThrownBy(() -> validator.validate(missingResolution))
                .isInstanceOf(StoryNarrativeValidationException.class)
                .hasMessageContaining("Resolucao");
    }

    @Test
    void identifiesEmptyOutputWithoutRealApiCall() {
        TestClient client = client();
        client.server.expect(ExpectedCount.times(2), requestTo(RESPONSES_URL))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"status":"completed","output":[]}
                                """));

        assertThatThrownBy(() -> client.generator.generate(request(4, StoryLength.SHORT)))
                .isInstanceOf(AiGenerationException.class)
                .hasMessageContaining("output");

        client.server.verify();
    }

    @Test
    void identifiesContentWithoutOutputTextWithoutRealApiCall() {
        TestClient client = client();
        client.server.expect(ExpectedCount.times(2), requestTo(RESPONSES_URL))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"status":"completed","output":[{"type":"message","content":[{"type":"summary_text","text":"ok"}]}]}
                                """));

        assertThatThrownBy(() -> client.generator.generate(request(4, StoryLength.SHORT)))
                .isInstanceOf(AiGenerationException.class)
                .hasMessageContaining("output_text");

        client.server.verify();
    }

    @Test
    void identifiesInvalidJsonWithoutRealApiCall() throws Exception {
        TestClient client = client();
        client.server.expect(requestTo(RESPONSES_URL))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(responseWithText("nao e json")));

        assertThatThrownBy(() -> client.generator.generate(request(4, StoryLength.SHORT)))
                .isInstanceOf(AiGenerationException.class)
                .hasMessageContaining("JSON");

        client.server.verify();
    }

    @Test
    void identifiesRefusalWithoutRealApiCall() {
        TestClient client = client();
        client.server.expect(ExpectedCount.times(2), requestTo(RESPONSES_URL))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"status":"completed","output":[{"type":"message","content":[{"type":"refusal","refusal":"Nao posso ajudar."}]}]}
                                """));

        assertThatThrownBy(() -> client.generator.generate(request(4, StoryLength.SHORT)))
                .isInstanceOf(AiGenerationException.class)
                .hasMessageContaining("recusada");

        client.server.verify();
    }

    @Test
    void identifiesIncompleteWithoutRealApiCall() {
        TestClient client = client();
        client.server.expect(ExpectedCount.times(2), requestTo(RESPONSES_URL))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"status":"incomplete","incomplete_details":{"reason":"max_output_tokens"},"output":[]}
                                """));

        assertThatThrownBy(() -> client.generator.generate(request(4, StoryLength.SHORT)))
                .isInstanceOf(AiGenerationException.class)
                .hasMessageContaining("output");

        client.server.verify();
    }

    @Test
    void mapsHttp400ToGenerationExceptionWithoutRealApiCall() {
        TestClient client = client();
        client.server.expect(requestTo(RESPONSES_URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(openAiError("invalid_request_error", "text.format.schema", "Unsupported schema field sk-test-secret")));

        assertThatThrownBy(() -> client.generator.generate(request(4, StoryLength.SHORT)))
                .isInstanceOf(AiGenerationException.class);

        client.server.verify();
    }

    @Test
    void mapsHttp401ToConfigurationExceptionWithoutRealApiCall() {
        TestClient client = client();
        client.server.expect(requestTo(RESPONSES_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(openAiError("invalid_api_key", null, "Incorrect API key provided")));

        assertThatThrownBy(() -> client.generator.generate(request(4, StoryLength.SHORT)))
                .isInstanceOf(AiConfigurationException.class);

        client.server.verify();
    }

    @Test
    void mapsHttp403ToConfigurationExceptionWithoutRealApiCall() {
        TestClient client = client();
        client.server.expect(requestTo(RESPONSES_URL)).andRespond(withStatus(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body(openAiError("insufficient_permissions", null, "Project does not have access")));

        assertThatThrownBy(() -> client.generator.generate(request(4, StoryLength.SHORT)))
                .isInstanceOf(AiConfigurationException.class);

        client.server.verify();
    }

    @Test
    void retriesHttp429AndThenMapsToUnavailableWithoutRealApiCall() {
        TestClient client = client();
        client.server.expect(ExpectedCount.times(3), requestTo(RESPONSES_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .body(openAiError("rate_limit_exceeded", null, "Rate limit reached")));

        assertThatThrownBy(() -> client.generator.generate(request(4, StoryLength.SHORT)))
                .isInstanceOf(AiUnavailableException.class);

        client.server.verify();
    }

    @Test
    void retriesHttp500AndThenMapsToUnavailableWithoutRealApiCall() {
        TestClient client = client();
        client.server.expect(ExpectedCount.times(3), requestTo(RESPONSES_URL)).andRespond(withServerError()
                .contentType(MediaType.APPLICATION_JSON)
                .body(openAiError("server_error", null, "Server unavailable")));

        assertThatThrownBy(() -> client.generator.generate(request(4, StoryLength.SHORT)))
                .isInstanceOf(AiUnavailableException.class);

        client.server.verify();
    }

    @Test
    void retriesTimeoutAndThenMapsToUnavailableWithoutRealApiCall() {
        TestClient client = client();
        client.server.expect(ExpectedCount.times(3), requestTo(RESPONSES_URL))
                .andRespond(request -> { throw new ResourceAccessException("Read timed out", new SocketTimeoutException("timeout")); });

        assertThatThrownBy(() -> client.generator.generate(request(4, StoryLength.SHORT)))
                .isInstanceOf(AiUnavailableException.class);

        client.server.verify();
    }

    private TestClient client() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        RestTemplateBuilder builder = new TestRestTemplateBuilder(restTemplate);
        OpenAIStoryGenerator generator = new OpenAIStoryGenerator(
                new OpenAiProperties("sk-test-secret", "gpt-test", 1),
                objectMapper,
                builder,
                new StoryPromptGuidance(),
                new StoryNarrativeValidator());
        return new TestClient(generator, server);
    }

    private StoryGenerationRequest request(int age, StoryLength length) {
        return new StoryGenerationRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Nando Teste",
                LocalDate.now().minusYears(age),
                "Super Nando",
                "Luna",
                null,
                null,
                null,
                null,
                "Medo do escuro",
                "Floresta",
                "Dinossauro",
                StoryStyle.BEDTIME,
                length);
    }

    private StoryGenerationRequest requestWithoutAnimal() {
        return new StoryGenerationRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Nando Teste",
                LocalDate.now().minusYears(4),
                "Super Nando",
                null,
                null,
                null,
                null,
                null,
                "Medo do escuro",
                "Floresta",
                null,
                StoryStyle.BEDTIME,
                StoryLength.SHORT);
    }

    private String validStoryJson() {
        return validStoryJson("Nando encontrou uma luz tranquila. Ele ajudou a luz a voltar para casa. Depois, voltou feliz.");
    }

    private String validStoryJson(String content) {
        return validStoryJson(content, 2);
    }

    private String validStoryJson(String content, int chapters) {
        return """
                {"title":"Nando e a luz amiga","summary":"Resumo com\\\\nlinha.","narrativeArc":{"setup":"Nando esta na floresta.","centralSituation":"Ele encontra uma luz perdida.","protagonistAction":"Nando ajuda a luz a escolher o caminho.","resolution":"A luz encontra o caminho e Nando volta feliz.","closingScene":"Nando volta para casa calmo e conta a aventura."},"chapters":[%s]}
                """.formatted(chapters(content, chapters));
    }

    private String storyJsonWithArc(String resolution, String centralSituation, String content) {
        return """
                {"title":"Nando","summary":"Resumo","narrativeArc":{"setup":"Inicio","centralSituation":"%s","protagonistAction":"Nando tentou ajudar.","resolution":"%s","closingScene":"Nando descansou feliz."},"chapters":[%s]}
                """.formatted(centralSituation, resolution, chapters(content, 2));
    }

    private String storyJsonWithoutClosingScene() {
        return """
                {"title":"Nando","summary":"Resumo","narrativeArc":{"setup":"Inicio","centralSituation":"Situacao","protagonistAction":"Nando tentou ajudar.","resolution":"Resolucao","closingScene":""},"chapters":[%s]}
                """.formatted(chapters("Capitulo com texto.", 2));
    }

    private String chapters(String content, int chapters) {
        StringBuilder builder = new StringBuilder();
        for (int number = 1; number <= chapters; number++) {
            if (number > 1) {
                builder.append(",");
            }
            builder.append("""
                    {"number":%s,"title":"Capitulo %s","content":"%s"}
                    """.formatted(number, number, content.replace("\"", "\\\"").trim()));
        }
        return builder.toString();
    }

    private String openAiError(String code, String param, String message) {
        return """
                {"error":{"message":"%s","type":"invalid_request_error","param":%s,"code":"%s"}}
                """.formatted(message, param == null ? "null" : "\"" + param + "\"", code);
    }

    private String responseWithText(String text) throws Exception {
        return """
                {"status":"completed","output":[{"type":"message","content":[{"type":"output_text","text":%s}]}],"usage":{"input_tokens":10,"output_tokens":20}}
                """.formatted(objectMapper.writeValueAsString(text.trim()));
    }

    private record TestClient(OpenAIStoryGenerator generator, MockRestServiceServer server) {}

    private static class TestRestTemplateBuilder extends RestTemplateBuilder {
        private final RestTemplate restTemplate;

        TestRestTemplateBuilder(RestTemplate restTemplate) {
            this.restTemplate = restTemplate;
        }

        @Override
        public RestTemplateBuilder setConnectTimeout(java.time.Duration connectTimeout) {
            return this;
        }

        @Override
        public RestTemplateBuilder setReadTimeout(java.time.Duration readTimeout) {
            return this;
        }

        @Override
        public RestTemplate build() {
            return restTemplate;
        }
    }
}
