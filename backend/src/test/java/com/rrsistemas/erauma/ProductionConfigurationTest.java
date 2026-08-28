package com.rrsistemas.erauma;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class ProductionConfigurationTest {
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

    private PropertySource<?> loadProductionProperties() throws IOException {
        return new YamlPropertySourceLoader()
                .load("application-prod", new ClassPathResource("application-prod.yml"))
                .get(0);
    }
}
