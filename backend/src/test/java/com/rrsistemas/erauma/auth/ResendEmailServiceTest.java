package com.rrsistemas.erauma.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.rrsistemas.erauma.user.AppUser;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

@ExtendWith(OutputCaptureExtension.class)
class ResendEmailServiceTest {
    @Test
    void sendsPasswordResetEmailWithEncodedToken() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        ResendEmailService service = new ResendEmailService(
                new ResendEmailProperties("re_test_key", "EraUma <contato@erauma.app.br>", "https://erauma.app.br/reset-password", 7),
                new FixedRestTemplateBuilder(restTemplate));
        AppUser user = new AppUser("Rafael", "rafael@email.com", "hash");

        server.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer re_test_key"))
                .andExpect(content().string(containsString("EraUma <contato@erauma.app.br>")))
                .andExpect(content().string(containsString("rafael@email.com")))
                .andExpect(content().string(containsString("Redefinicao de senha do EraUma")))
                .andExpect(content().string(containsString("https://erauma.app.br/reset-password?token=abc%2B%2F%3D")))
                .andExpect(content().string(containsString("ignore esta mensagem")))
                .andRespond(withSuccess("{\"id\":\"email_123\"}", MediaType.APPLICATION_JSON));

        service.sendPasswordReset(user, "abc+/=", Instant.parse("2026-09-02T13:00:00Z"));

        server.verify();
    }

    @Test
    void failsFastWhenProductionConfigurationIsMissing() {
        assertThatThrownBy(() -> new ResendEmailService(
                new ResendEmailProperties("", "EraUma <contato@erauma.app.br>", "https://erauma.app.br/reset-password", 10),
                new FixedRestTemplateBuilder(new RestTemplate())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RESEND_API_KEY");
    }

    @Test
    void resendFailureDoesNotLogApiKeyOrResetToken(CapturedOutput output) {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        ResendEmailService service = new ResendEmailService(
                new ResendEmailProperties("re_secret_key", "EraUma <contato@erauma.app.br>", "https://erauma.app.br/reset-password", 7),
                new FixedRestTemplateBuilder(restTemplate));
        AppUser user = new AppUser("Rafael", "rafael@email.com", "hash");

        server.expect(requestTo("https://api.resend.com/emails"))
                .andRespond(withServerError().body("{\"message\":\"bad token=recovery-token re_secret_key\"}"));

        assertThatThrownBy(() -> service.sendPasswordReset(user, "recovery-token", Instant.now()))
                .isInstanceOf(PasswordResetEmailException.class);

        assertThat(output).doesNotContain("re_secret_key").doesNotContain("recovery-token");
    }

    private static class FixedRestTemplateBuilder extends RestTemplateBuilder {
        private final RestTemplate restTemplate;
        private Duration connectTimeout;
        private Duration readTimeout;

        FixedRestTemplateBuilder(RestTemplate restTemplate) {
            this.restTemplate = restTemplate;
        }

        @Override
        public RestTemplateBuilder setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        @Override
        public RestTemplateBuilder setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
            return this;
        }

        @Override
        public RestTemplate build() {
            assertThat(connectTimeout).isEqualTo(Duration.ofSeconds(7));
            assertThat(readTimeout).isEqualTo(Duration.ofSeconds(7));
            return restTemplate;
        }
    }
}
