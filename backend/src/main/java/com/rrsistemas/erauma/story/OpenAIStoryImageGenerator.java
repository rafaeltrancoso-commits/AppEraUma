package com.rrsistemas.erauma.story;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Service
public class OpenAIStoryImageGenerator implements StoryImageGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenAIStoryImageGenerator.class);
    private static final String IMAGE_URL = "https://api.openai.com/v1/images/generations";
    private final OpenAiProperties openAiProperties;
    private final OpenAiImageProperties imageProperties;
    private final RestTemplateBuilder restTemplateBuilder;
    private final ObjectMapper objectMapper;

    public OpenAIStoryImageGenerator(OpenAiProperties openAiProperties, OpenAiImageProperties imageProperties, RestTemplateBuilder restTemplateBuilder, ObjectMapper objectMapper) {
        this.openAiProperties = openAiProperties;
        this.imageProperties = imageProperties;
        this.restTemplateBuilder = restTemplateBuilder;
        this.objectMapper = objectMapper;
    }

    @Override
    public GeneratedStoryImage generate(String prompt) {
        if (openAiProperties.apiKey() == null || openAiProperties.apiKey().isBlank()) {
            throw new AiConfigurationException("OPENAI_API_KEY não configurada.");
        }
        long startedAt = System.nanoTime();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(openAiProperties.apiKey());
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> payload = Map.of(
                    "model", imageProperties.model(),
                    "prompt", prompt,
                    "size", imageProperties.size(),
                    "quality", imageProperties.quality(),
                    "n", 1,
                    "output_format", "png");
            RestTemplate restTemplate = restTemplateBuilder
                    .setConnectTimeout(Duration.ofSeconds(imageProperties.timeoutSeconds()))
                    .setReadTimeout(Duration.ofSeconds(imageProperties.timeoutSeconds()))
                    .build();
            ResponseEntity<JsonNode> response = restTemplate.exchange(IMAGE_URL, HttpMethod.POST, new HttpEntity<>(payload, headers), JsonNode.class);
            String base64 = response.getBody() == null ? "" : response.getBody().path("data").path(0).path("b64_json").asText("");
            if (base64.isBlank()) {
                throw new AiGenerationException("Resposta de imagem sem b64_json.");
            }
            long durationMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            byte[] bytes = Base64.getDecoder().decode(base64);
            LOGGER.info("openai_image_generated model={} size={} quality={} bytes={} durationMs={}", imageProperties.model(), imageProperties.size(), imageProperties.quality(), bytes.length, durationMs);
            return new GeneratedStoryImage(bytes, imageProperties.model(), imageProperties.size(), imageProperties.quality(), durationMs);
        } catch (ResourceAccessException exception) {
            LOGGER.warn("openai_image_failed status=timeout code= message={}", sanitize(exception.getMessage()));
            throw new AiUnavailableException("Timeout ao gerar imagem na OpenAI.", exception);
        } catch (HttpClientErrorException | HttpServerErrorException exception) {
            int status = exception.getStatusCode().value();
            OpenAiErrorDetails details = parseOpenAiError(exception.getResponseBodyAsString());
            LOGGER.warn("openai_image_failed status={} code={} message={}", status, sanitize(details.code()), sanitize(firstNonBlank(details.message(), exception.getStatusText())));
            if (status == 401 || status == 403) {
                throw new AiConfigurationException("Credenciais OpenAI inválidas.");
            }
            throw new AiGenerationException("Falha HTTP ao gerar imagem na OpenAI.", exception);
        } catch (IllegalArgumentException exception) {
            LOGGER.warn("openai_image_failed status=parse code=invalid_base64 message={}", sanitize(exception.getMessage()));
            throw new AiGenerationException("Imagem retornada em base64 inválido.", exception);
        }
    }

    private String sanitize(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = value.replaceAll("(?i)bearer\\s+[A-Za-z0-9._\\-]+", "Bearer [redacted]")
                .replaceAll("sk-[A-Za-z0-9_\\-]+", "[redacted]")
                .replaceAll("[\\r\\n\\t]+", " ")
                .trim();
        return sanitized.length() > 300 ? sanitized.substring(0, 300) : sanitized;
    }

    private OpenAiErrorDetails parseOpenAiError(String responseBody) {
        try {
            if (responseBody == null || responseBody.isBlank()) {
                return new OpenAiErrorDetails("", "");
            }
            JsonNode error = objectMapper.readTree(responseBody).path("error");
            return new OpenAiErrorDetails(error.path("code").asText(""), error.path("message").asText(""));
        } catch (Exception exception) {
            return new OpenAiErrorDetails("", "Resposta de erro não estava em JSON válido.");
        }
    }

    private String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
    }

    private record OpenAiErrorDetails(String code, String message) {}
}
