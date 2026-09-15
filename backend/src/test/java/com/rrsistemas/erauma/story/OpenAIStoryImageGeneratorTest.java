package com.rrsistemas.erauma.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class OpenAIStoryImageGeneratorTest {
    private static final String IMAGE_URL = "https://api.openai.com/v1/images/generations";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mapsModerationBlockedHttp400ToContentModerationExceptionWithoutRealApiCall() {
        TestClient client = client();
        client.server.expect(requestTo(IMAGE_URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(openAiError("moderation_blocked", "Your request was rejected by the safety system.")));

        assertThatThrownBy(() -> client.generator.generate("prompt com termo problematico"))
                .isInstanceOf(AiContentModerationException.class);

        client.server.verify();
    }

    @Test
    void doesNotMapOtherHttp400ErrorsToContentModerationException() {
        TestClient client = client();
        client.server.expect(requestTo(IMAGE_URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(openAiError("invalid_request_error", "Invalid size parameter.")));

        assertThatThrownBy(() -> client.generator.generate("prompt qualquer"))
                .isInstanceOf(AiGenerationException.class)
                .isNotInstanceOf(AiContentModerationException.class);

        client.server.verify();
    }

    @Test
    void doesNotMapHttp429ToContentModerationException() {
        TestClient client = client();
        client.server.expect(requestTo(IMAGE_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .body(openAiError("rate_limit_exceeded", "Rate limit reached")));

        assertThatThrownBy(() -> client.generator.generate("prompt qualquer"))
                .isInstanceOf(AiGenerationException.class)
                .isNotInstanceOf(AiContentModerationException.class);

        client.server.verify();
    }

    @Test
    void mapsHttp401ToConfigurationExceptionWithoutRealApiCall() {
        TestClient client = client();
        client.server.expect(requestTo(IMAGE_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(openAiError("invalid_api_key", "Incorrect API key provided")));

        assertThatThrownBy(() -> client.generator.generate("prompt qualquer"))
                .isInstanceOf(AiConfigurationException.class);

        client.server.verify();
    }

    @Test
    void parsesSuccessfulImageResponseWithoutRealApiCall() {
        TestClient client = client();
        byte[] pngBytes = {(byte) 0x89, 'P', 'N', 'G'};
        client.server.expect(requestTo(IMAGE_URL)).andRespond(withStatus(HttpStatus.OK)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"data\":[{\"b64_json\":\"" + Base64.getEncoder().encodeToString(pngBytes) + "\"}]}"));

        GeneratedStoryImage generated = client.generator.generate("prompt seguro");

        assertThat(generated.pngBytes()).isEqualTo(pngBytes);
        assertThat(generated.model()).isEqualTo("gpt-image-2");
        client.server.verify();
    }

    private TestClient client() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        RestTemplateBuilder builder = new TestRestTemplateBuilder(restTemplate);
        OpenAIStoryImageGenerator generator = new OpenAIStoryImageGenerator(
                new OpenAiProperties("sk-test-secret", "gpt-test", 1),
                new OpenAiImageProperties("gpt-image-2", "1024x1024", "medium", 1),
                builder,
                objectMapper);
        return new TestClient(generator, server);
    }

    private String openAiError(String code, String message) {
        return """
                {"error":{"message":"%s","type":"invalid_request_error","param":null,"code":"%s"}}
                """.formatted(message, code);
    }

    private record TestClient(OpenAIStoryImageGenerator generator, MockRestServiceServer server) {}

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
