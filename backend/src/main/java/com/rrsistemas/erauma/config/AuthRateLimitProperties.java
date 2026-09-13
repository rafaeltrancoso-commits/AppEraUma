package com.rrsistemas.erauma.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracao do limitador de tentativas por IP em login/registro/esqueci-minha-senha.
 *
 * <p>maxRequests vale para login; registerMaxRequests e forgotPasswordMaxRequests
 * permitem limites diferentes (por padrao mais restritivos, ja que cadastro em massa
 * e envio de e-mail tem custo/abuso maiores que uma tentativa de login). Todos
 * compartilham a mesma janela (windowSeconds).</p>
 *
 * <p>maxTrackedKeys limita o crescimento da estrutura em memoria: acima desse numero
 * de chaves (rota+IP) distintas observadas dentro da janela, novas chaves deixam de
 * ser rastreadas (fail-open) em vez de crescer indefinidamente.</p>
 *
 * <p>Importante: este limitador e somente em memoria, por instancia do processo.
 * Ele NAO e distribuido entre multiplas instancias do backend (cada instancia tem
 * seus proprios contadores) e perde todo o estado a cada reinicio/redeploy. Isso e
 * aceitavel como mitigacao basica de forca bruta/abuso, mas nao substitui um
 * limitador centralizado (ex.: Redis) caso o backend passe a rodar com mais de uma
 * instancia simultanea.</p>
 */
@ConfigurationProperties(prefix = "app.auth.rate-limit")
public record AuthRateLimitProperties(
        boolean enabled,
        int maxRequests,
        int windowSeconds,
        int registerMaxRequests,
        int forgotPasswordMaxRequests,
        int maxTrackedKeys
) {}
