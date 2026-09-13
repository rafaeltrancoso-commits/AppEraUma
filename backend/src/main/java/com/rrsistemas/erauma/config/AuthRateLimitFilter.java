package com.rrsistemas.erauma.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrsistemas.erauma.shared.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limitador simples de tentativas por IP nos endpoints sensiveis de autenticacao
 * (login, registro e esqueci-minha-senha), para reduzir forca bruta e abuso de
 * envio de e-mail. Janela deslizante em memoria, sem dependencia externa nova.
 *
 * <p>Limitacoes conhecidas, aceitas conscientemente:</p>
 * <ul>
 *   <li>Somente em memoria: nao e distribuido entre multiplas instancias do
 *       backend (cada instancia conta separadamente) e perde todo o estado a
 *       cada reinicio/redeploy. Atualmente ha somente uma replica em producao,
 *       entao isso nao e um problema hoje; se o backend passar a rodar com mais
 *       de uma instancia simultanea, este limitador perde eficacia proporcional
 *       ao numero de replicas e uma solucao compartilhada (ex.: Redis) passa a
 *       ser necessaria antes disso acontecer.</li>
 *   <li>O IP do cliente e resolvido via X-Forwarded-For assumindo exatamente um
 *       proxy reverso confiavel na frente (a borda do Railway) - ver
 *       {@link ClientIpResolver}. Isso so e seguro enquanto o container nao for
 *       alcancavel por outra rota que nao passe por esse proxy. Os testes de
 *       {@code ClientIpResolver} comprovam apenas a regra local de parsing
 *       (usar o ultimo valor da lista); o comportamento real do proxy da
 *       Railway nessa convencao ainda nao foi validado em producao.</li>
 *   <li>Esta e uma protecao auxiliar contra forca bruta/abuso vindo de um
 *       numero pequeno de IPs de origem - nao deve ser considerada controle
 *       suficiente contra ataques distribuidos (muitos IPs distintos), que
 *       contornam o limite por IP quase que trivialmente.</li>
 * </ul>
 *
 * <p>Desligado no perfil "test" (ver application-test.yml) para nao interferir na
 * suite de testes de integracao, que autentica dezenas de vezes na mesma "janela".</p>
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthRateLimitFilter.class);
    private static final String PATH_LOGIN = "/api/auth/login";
    private static final String PATH_REGISTER = "/api/auth/register";
    private static final String PATH_FORGOT_PASSWORD = "/api/auth/forgot-password";
    private static final Set<String> LIMITED_PATHS = Set.of(PATH_LOGIN, PATH_REGISTER, PATH_FORGOT_PASSWORD);

    private final AuthRateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, ConcurrentLinkedDeque<Long>> hitsByKey = new ConcurrentHashMap<>();
    private Clock clock = Clock.systemUTC();

    public AuthRateLimitFilter(AuthRateLimitProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /** Somente para testes: permite controlar o tempo sem depender de Thread.sleep. */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!properties.enabled()
                || !"POST".equalsIgnoreCase(request.getMethod())
                || !LIMITED_PATHS.contains(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        long windowMillis = Math.max(1, properties.windowSeconds()) * 1000L;
        int limit = Math.max(1, limitFor(path));
        String key = path + "|" + clientIp(request);
        long now = clock.millis();

        ConcurrentLinkedDeque<Long> timestamps = hitsByKey.get(key);
        if (timestamps == null) {
            if (hitsByKey.size() >= Math.max(1, properties.maxTrackedKeys())) {
                // Salvaguarda de memoria: se o numero de chaves distintas (rota+IP)
                // observadas na janela atual explodir (ex.: ataque distribuido com
                // muitos IPs de origem), paramos de rastrear novas chaves em vez de
                // crescer sem limite. Efeito: passa a falhar aberto (sem limitar)
                // para IPs novos ate a proxima limpeza periodica liberar espaco -
                // preferimos isso a um outOfMemory ou a degradar o backend inteiro.
                LOGGER.warn("auth_rate_limit_capacity_exceeded path={} trackedKeys={}", path, hitsByKey.size());
                filterChain.doFilter(request, response);
                return;
            }
            timestamps = hitsByKey.computeIfAbsent(key, ignored -> new ConcurrentLinkedDeque<>());
        }

        boolean blocked;
        long retryAfterMillis = windowMillis;
        synchronized (timestamps) {
            evictOlderThan(timestamps, now, windowMillis);
            blocked = timestamps.size() >= limit;
            if (!blocked) {
                timestamps.addLast(now);
            } else {
                Long oldest = timestamps.peekFirst();
                if (oldest != null) {
                    retryAfterMillis = Math.max(1000L, windowMillis - (now - oldest));
                }
            }
        }

        if (blocked) {
            LOGGER.warn("auth_rate_limit_exceeded path={} clientHash={}", path, Integer.toHexString(key.hashCode()));
            writeTooManyRequests(response, retryAfterMillis / 1000L);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private int limitFor(String path) {
        if (PATH_REGISTER.equals(path)) {
            return properties.registerMaxRequests();
        }
        if (PATH_FORGOT_PASSWORD.equals(path)) {
            return properties.forgotPasswordMaxRequests();
        }
        return properties.maxRequests();
    }

    private void evictOlderThan(ConcurrentLinkedDeque<Long> timestamps, long now, long windowMillis) {
        while (true) {
            Long oldest = timestamps.peekFirst();
            if (oldest == null || now - oldest <= windowMillis) {
                return;
            }
            timestamps.pollFirst();
        }
    }

    private void writeTooManyRequests(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(Math.max(1, retryAfterSeconds)));
        ApiError body = ApiError.of(HttpStatus.TOO_MANY_REQUESTS.value(), "TOO_MANY_REQUESTS",
                "Muitas tentativas. Aguarde alguns minutos e tente novamente.");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private String clientIp(HttpServletRequest request) {
        return ClientIpResolver.resolve(request.getHeader("X-Forwarded-For"), request.getRemoteAddr());
    }

    @Scheduled(fixedDelay = 600000)
    void cleanupStaleEntries() {
        long windowMillis = Math.max(1, properties.windowSeconds()) * 1000L;
        long now = clock.millis();
        hitsByKey.forEach((key, timestamps) -> {
            synchronized (timestamps) {
                evictOlderThan(timestamps, now, windowMillis);
                if (timestamps.isEmpty()) {
                    hitsByKey.remove(key, timestamps);
                }
            }
        });
    }
}
