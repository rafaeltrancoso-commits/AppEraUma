package com.rrsistemas.erauma.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.rrsistemas.erauma.user.AppUser;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Profile;
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
@Profile("prod")
public class ResendEmailService implements EmailService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResendEmailService.class);
    private static final String RESEND_EMAILS_URL = "https://api.resend.com/emails";
    private static final DateTimeFormatter EXPIRATION_FORMATTER = DateTimeFormatter
            .ofPattern("dd/MM/yyyy 'as' HH:mm")
            .withZone(ZoneId.of("America/Sao_Paulo"));

    private final ResendEmailProperties properties;
    private final RestTemplate restTemplate;

    public ResendEmailService(ResendEmailProperties properties, RestTemplateBuilder restTemplateBuilder) {
        this.properties = properties;
        validateRequired(properties);
        Duration timeout = Duration.ofSeconds(properties.timeoutSeconds() > 0 ? properties.timeoutSeconds() : 10);
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(timeout)
                .setReadTimeout(timeout)
                .build();
    }

    @Override
    public void sendPasswordReset(AppUser user, String token, Instant expiresAt) {
        String resetUrl = buildResetUrl(token);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(properties.resendApiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> payload = Map.of(
                "from", properties.from(),
                "to", List.of(user.getEmail()),
                "subject", "Redefinicao de senha do EraUma",
                "html", htmlBody(user, resetUrl, expiresAt),
                "text", textBody(user, resetUrl, expiresAt));

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    URI.create(RESEND_EMAILS_URL),
                    HttpMethod.POST,
                    new HttpEntity<>(payload, headers),
                    JsonNode.class);
            String id = response.getBody() == null ? "" : response.getBody().path("id").asText("");
            LOGGER.info("password_reset_email_sent provider=resend userId={} status={} resendIdPresent={}",
                    user.getId(), response.getStatusCode().value(), !id.isBlank());
        } catch (ResourceAccessException exception) {
            LOGGER.warn("password_reset_email_failed provider=resend userId={} status=timeout message={}",
                    user.getId(), sanitize(exception.getMessage()));
            throw new PasswordResetEmailException("Falha ao enviar e-mail de recuperacao.", exception);
        } catch (HttpClientErrorException | HttpServerErrorException exception) {
            LOGGER.warn("password_reset_email_failed provider=resend userId={} status={} message={}",
                    user.getId(), exception.getStatusCode().value(), sanitize(firstNonBlank(exception.getStatusText(), exception.getMessage())));
            throw new PasswordResetEmailException("Falha ao enviar e-mail de recuperacao.", exception);
        }
    }

    private String buildResetUrl(String token) {
        String separator = properties.passwordResetUrl().contains("?") ? "&" : "?";
        return properties.passwordResetUrl() + separator + "token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    private String htmlBody(AppUser user, String resetUrl, Instant expiresAt) {
        String safeName = escapeHtml(firstNonBlank(user.getName(), "familia EraUma"));
        String safeUrl = escapeHtml(resetUrl);
        String expiration = escapeHtml(EXPIRATION_FORMATTER.format(expiresAt));
        return """
                <!doctype html>
                <html lang="pt-BR">
                  <body style="margin:0;background:#fff8ec;font-family:Arial,Helvetica,sans-serif;color:#243047;">
                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#fff8ec;padding:24px 0;">
                      <tr>
                        <td align="center">
                          <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:560px;background:#ffffff;border-radius:12px;padding:32px;border:1px solid #f0dfc4;">
                            <tr><td style="font-size:28px;font-weight:800;color:#7c4d1d;">EraUma</td></tr>
                            <tr><td style="font-size:18px;font-weight:700;padding-top:24px;">Redefinicao de senha</td></tr>
                            <tr><td style="font-size:15px;line-height:1.6;padding-top:12px;">Ola, %s. Recebemos uma solicitacao para redefinir a senha da sua conta EraUma.</td></tr>
                            <tr><td align="center" style="padding:28px 0;"><a href="%s" style="background:#7c4d1d;color:#ffffff;text-decoration:none;padding:14px 22px;border-radius:8px;font-weight:700;display:inline-block;">Redefinir senha</a></td></tr>
                            <tr><td style="font-size:14px;line-height:1.6;">Este link expira em 30 minutos, ate %s. Se voce nao solicitou a redefinicao, ignore esta mensagem.</td></tr>
                            <tr><td style="font-size:12px;line-height:1.5;color:#6b7280;padding-top:24px;">Se o botao nao funcionar, copie e cole este link no navegador:<br><span style="word-break:break-all;">%s</span></td></tr>
                          </table>
                        </td>
                      </tr>
                    </table>
                  </body>
                </html>
                """.formatted(safeName, safeUrl, expiration, safeUrl);
    }

    private String textBody(AppUser user, String resetUrl, Instant expiresAt) {
        return """
                EraUma - Redefinicao de senha

                Ola, %s.

                Recebemos uma solicitacao para redefinir a senha da sua conta EraUma.

                Acesse o link abaixo para escolher uma nova senha:
                %s

                Este link expira em 30 minutos, ate %s.

                Se voce nao solicitou a redefinicao, ignore esta mensagem.
                """.formatted(firstNonBlank(user.getName(), "familia EraUma"), resetUrl, EXPIRATION_FORMATTER.format(expiresAt));
    }

    private void validateRequired(ResendEmailProperties properties) {
        if (isBlank(properties.resendApiKey())) {
            throw new IllegalStateException("RESEND_API_KEY obrigatoria no perfil prod.");
        }
        if (isBlank(properties.from())) {
            throw new IllegalStateException("APP_EMAIL_FROM obrigatoria no perfil prod.");
        }
        if (isBlank(properties.passwordResetUrl())) {
            throw new IllegalStateException("APP_PASSWORD_RESET_URL obrigatoria no perfil prod.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String sanitize(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = value
                .replaceAll("(?i)bearer\\s+[A-Za-z0-9._\\-]+", "Bearer [redacted]")
                .replaceAll("re_[A-Za-z0-9._\\-]+", "[redacted]")
                .replaceAll("token=[^\\s&]+", "token=[redacted]")
                .replaceAll("[\\r\\n\\t]+", " ")
                .trim();
        return sanitized.length() > 300 ? sanitized.substring(0, 300) : sanitized;
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
    }
}
