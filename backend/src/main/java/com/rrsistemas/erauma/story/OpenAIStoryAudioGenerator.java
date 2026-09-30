package com.rrsistemas.erauma.story;

import java.time.Duration;
import java.util.LinkedHashMap;
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
public class OpenAIStoryAudioGenerator implements StoryAudioGenerator {
    private static final String SPEECH_URL = "https://api.openai.com/v1/audio/speech";
    private static final Logger log = LoggerFactory.getLogger(OpenAIStoryAudioGenerator.class);
    private final OpenAiProperties openAi;
    private final OpenAiAudioProperties audio;
    private final RestTemplateBuilder restTemplates;

    public OpenAIStoryAudioGenerator(OpenAiProperties openAi, OpenAiAudioProperties audio,
            RestTemplateBuilder restTemplates) {
        this.openAi = openAi;
        this.audio = audio;
        this.restTemplates = restTemplates;
    }

    @Override
    public byte[] generate(String text) {
        if (openAi.apiKey() == null || openAi.apiKey().isBlank()) {
            throw new AiConfigurationException("OPENAI_API_KEY não configurada.");
        }
        if (text == null || text.isBlank() || text.length() > 4096) {
            throw new AiGenerationException("Trecho de narração inválido.");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(openAi.apiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", audio.model());
        payload.put("voice", audio.voice());
        payload.put("input", text);
        payload.put("response_format", audio.format());
        if (supportsInstructions(audio.model())) {
            String style = audio.instructions() == null ? "" : audio.instructions().trim();
            payload.put("instructions", (style + "\nRegra obrigatória: leia somente o texto de entrada, na mesma ordem e com as mesmas palavras; não acrescente, remova, resuma, traduza nem reescreva conteúdo.").trim());
        }
        RestTemplate client = restTemplates
                .setConnectTimeout(Duration.ofSeconds(audio.timeoutSeconds()))
                .setReadTimeout(Duration.ofSeconds(audio.timeoutSeconds()))
                .build();
        long startedAt = System.nanoTime();
        try {
            ResponseEntity<byte[]> response = client.exchange(SPEECH_URL, HttpMethod.POST,
                    new HttpEntity<>(payload, headers), byte[].class);
            byte[] bytes = response.getBody();
            if (bytes == null || bytes.length == 0) throw new AiGenerationException("Resposta de áudio vazia.");
            long durationMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            log.info("openai_audio_generated model={} voice={} format={} inputCharacters={} bytes={} durationMs={} requestId={} providerProcessingMs={}",
                    audio.model(), audio.voice(), audio.format(), text.length(), bytes.length, durationMs,
                    response.getHeaders().getFirst("x-request-id"),
                    response.getHeaders().getFirst("openai-processing-ms"));
            return bytes;
        } catch (ResourceAccessException exception) {
            throw new AiUnavailableException("Timeout ao gerar áudio na OpenAI.", exception);
        } catch (HttpClientErrorException | HttpServerErrorException exception) {
            if (exception.getStatusCode().value() == 401 || exception.getStatusCode().value() == 403) {
                throw new AiConfigurationException("Credenciais OpenAI inválidas.");
            }
            throw new AiGenerationException("Falha HTTP ao gerar áudio na OpenAI.", exception);
        }
    }

    private boolean supportsInstructions(String model) {
        return model != null && !model.equalsIgnoreCase("tts-1") && !model.equalsIgnoreCase("tts-1-hd");
    }
}
