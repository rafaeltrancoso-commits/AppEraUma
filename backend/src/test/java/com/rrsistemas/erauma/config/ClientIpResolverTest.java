package com.rrsistemas.erauma.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Cobre somente a regra local de parsing de {@link ClientIpResolver} (pegar o
 * ultimo valor de X-Forwarded-For). Nao comprova, e nao pode comprovar sem um
 * teste contra o ambiente real, que o proxy da Railway de fato anexa o IP
 * observado dessa forma em producao - essa premissa continua nao validada.
 */
class ClientIpResolverTest {
    @Test
    void usesRemoteAddrWhenHeaderIsAbsent() {
        assertThat(ClientIpResolver.resolve(null, "203.0.113.9")).isEqualTo("203.0.113.9");
        assertThat(ClientIpResolver.resolve("", "203.0.113.9")).isEqualTo("203.0.113.9");
        assertThat(ClientIpResolver.resolve("   ", "203.0.113.9")).isEqualTo("203.0.113.9");
    }

    @Test
    void usesTheOnlyValueWhenHeaderHasASingleHop() {
        // Cenario tipico atras de um unico proxy confiavel (Railway): o proxy
        // sobrescreve ou define o header com apenas o IP real do cliente.
        assertThat(ClientIpResolver.resolve("198.51.100.7", "10.0.0.5")).isEqualTo("198.51.100.7");
    }

    @Test
    void trustsTheLastHopAppendedByTheProxyNotTheClientSuppliedFirstValue() {
        // Se o cliente original enviar seu proprio X-Forwarded-For, o proxy confiavel
        // recebe essa requisicao e ANEXA ao final o IP que ele mesmo observou. So o
        // ultimo valor foi escrito pelo proxy; o primeiro pode ter sido forjado pelo
        // cliente para burlar o limite de tentativas.
        String forged = "1.2.3.4, 198.51.100.7";

        assertThat(ClientIpResolver.resolve(forged, "10.0.0.5")).isEqualTo("198.51.100.7");
    }

    @Test
    void trimsWhitespaceAroundTheLastHop() {
        assertThat(ClientIpResolver.resolve("1.2.3.4,  198.51.100.7  ", "10.0.0.5")).isEqualTo("198.51.100.7");
    }

    @Test
    void fallsBackToRemoteAddrWhenLastHopIsBlank() {
        assertThat(ClientIpResolver.resolve("198.51.100.7, ", "10.0.0.5")).isEqualTo("10.0.0.5");
    }
}
