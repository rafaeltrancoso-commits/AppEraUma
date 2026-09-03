package com.rrsistemas.erauma;

import static org.assertj.core.api.Assertions.assertThat;

import com.rrsistemas.erauma.auth.EmailService;
import com.rrsistemas.erauma.auth.LoggingEmailService;
import com.rrsistemas.erauma.auth.ResendEmailProperties;
import com.rrsistemas.erauma.auth.ResendEmailService;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class ProductionConfigurationTest {
    private final ApplicationContextRunner emailContextRunner = new ApplicationContextRunner()
            .withBean(RestTemplateBuilder.class, RestTemplateBuilder::new)
            .withBean(ResendEmailProperties.class, () -> new ResendEmailProperties(
                    "re_test_key",
                    "EraUma <noreply@erauma.app.br>",
                    "https://erauma.app.br/reset-password",
                    10))
            .withUserConfiguration(LoggingEmailService.class, ResendEmailService.class);

    @Test
    void productionDatasourceUsesRailwayPostgresVariables() throws IOException {
        PropertySource<?> properties = loadProductionProperties();

        assertThat(properties.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://${PGHOST}:${PGPORT}/${PGDATABASE}");
        assertThat(properties.getProperty("spring.datasource.username")).isEqualTo("${PGUSER}");
        assertThat(properties.getProperty("spring.datasource.password")).isEqualTo("${PGPASSWORD}");
    }

    @Test
    void productionConfigurationDoesNotEmbedLocalOrSecretValues() throws IOException {
        PropertySource<?> properties = loadProductionProperties();

        assertThat(properties.getProperty("app.cors.allowed-origin-patterns")).isEqualTo("${APP_CORS_ALLOWED_ORIGINS:}");
        assertThat(properties.getProperty("spring.datasource.url").toString())
                .doesNotContain("localhost")
                .doesNotContain("192.168.");
    }

    @Test
    void localAndTestProfilesUseOnlyLoggingEmailService() {
        emailContextRunner.withPropertyValues("spring.profiles.active=local")
                .run(context -> {
                    assertThat(context).hasSingleBean(EmailService.class);
                    assertThat(context).hasSingleBean(LoggingEmailService.class);
                    assertThat(context).doesNotHaveBean(ResendEmailService.class);
                });

        emailContextRunner.withPropertyValues("spring.profiles.active=test")
                .run(context -> {
                    assertThat(context).hasSingleBean(EmailService.class);
                    assertThat(context).hasSingleBean(LoggingEmailService.class);
                    assertThat(context).doesNotHaveBean(ResendEmailService.class);
                });
    }

    @Test
    void prodProfileUsesOnlyResendEmailService() {
        emailContextRunner.withPropertyValues("spring.profiles.active=prod")
                .run(context -> {
                    assertThat(context).hasSingleBean(EmailService.class);
                    assertThat(context).hasSingleBean(ResendEmailService.class);
                    assertThat(context).doesNotHaveBean(LoggingEmailService.class);
                });
    }

    private PropertySource<?> loadProductionProperties() throws IOException {
        return new YamlPropertySourceLoader()
                .load("application-prod", new ClassPathResource("application-prod.yml"))
                .get(0);
    }
}
