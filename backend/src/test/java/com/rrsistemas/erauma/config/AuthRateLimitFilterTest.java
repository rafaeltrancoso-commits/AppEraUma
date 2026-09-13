package com.rrsistemas.erauma.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Cada teste cria sua propria instancia de {@link AuthRateLimitFilter} (estado
 * em memoria isolado) e usa IPs/paths distintos quando relevante, para que a
 * ordem de execucao e o estado de um teste nunca afetem outro.
 */
class AuthRateLimitFilterTest {
    private static final String LOGIN = "/api/auth/login";
    private static final String REGISTER = "/api/auth/register";
    private static final String FORGOT_PASSWORD = "/api/auth/forgot-password";
    private static final String UNPROTECTED_PATH = "/api/auth/logout";

    // ApiError.timestamp is an Instant; production relies on Spring Boot's
    // auto-configured ObjectMapper, which registers JavaTimeModule for this.
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void requestsBelowTheLimitReachTheChain() throws Exception {
        AuthRateLimitFilter filter = filter(properties(3, 60, 3, 3, 1000));

        for (int attempt = 0; attempt < 3; attempt++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(postRequest(LOGIN, "203.0.113.1"), response, chain);

            assertThat(chain.getRequest()).as("attempt %d should reach the chain", attempt).isNotNull();
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Test
    void requestExceedingTheLimitReturns429() throws Exception {
        AuthRateLimitFilter filter = filter(properties(2, 60, 2, 2, 1000));
        String ip = "203.0.113.2";

        filter.doFilter(postRequest(LOGIN, ip), new MockHttpServletResponse(), new MockFilterChain());
        filter.doFilter(postRequest(LOGIN, ip), new MockHttpServletResponse(), new MockFilterChain());

        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        MockFilterChain blockedChain = new MockFilterChain();
        filter.doFilter(postRequest(LOGIN, ip), blockedResponse, blockedChain);

        assertThat(blockedResponse.getStatus()).isEqualTo(429);
        assertThat(blockedChain.getRequest()).as("blocked request must not reach the chain").isNull();
    }

    @Test
    void the429ResponseIncludesRetryAfterHeader() throws Exception {
        AuthRateLimitFilter filter = filter(properties(1, 120, 1, 1, 1000));
        String ip = "203.0.113.3";

        filter.doFilter(postRequest(LOGIN, ip), new MockHttpServletResponse(), new MockFilterChain());

        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        filter.doFilter(postRequest(LOGIN, ip), blockedResponse, new MockFilterChain());

        assertThat(blockedResponse.getStatus()).isEqualTo(429);
        String retryAfter = blockedResponse.getHeader("Retry-After");
        assertThat(retryAfter).isNotBlank();
        assertThat(Long.parseLong(retryAfter)).isPositive();
    }

    @Test
    void loginUsesMaxRequestsLimit() throws Exception {
        // maxRequests=1 for login, register/forgot-password get a much higher
        // limit so a leak between limits would be caught by this test.
        AuthRateLimitFilter filter = filter(properties(1, 60, 50, 50, 1000));
        String ip = "203.0.113.4";

        filter.doFilter(postRequest(LOGIN, ip), new MockHttpServletResponse(), new MockFilterChain());

        MockHttpServletResponse second = new MockHttpServletResponse();
        filter.doFilter(postRequest(LOGIN, ip), second, new MockFilterChain());

        assertThat(second.getStatus()).isEqualTo(429);
    }

    @Test
    void registerUsesRegisterMaxRequestsLimit() throws Exception {
        // maxRequests (login) intentionally high so this only passes if the
        // filter picks registerMaxRequests for /api/auth/register.
        AuthRateLimitFilter filter = filter(properties(50, 60, 1, 50, 1000));
        String ip = "203.0.113.5";

        filter.doFilter(postRequest(REGISTER, ip), new MockHttpServletResponse(), new MockFilterChain());

        MockHttpServletResponse second = new MockHttpServletResponse();
        filter.doFilter(postRequest(REGISTER, ip), second, new MockFilterChain());

        assertThat(second.getStatus()).isEqualTo(429);
    }

    @Test
    void forgotPasswordUsesForgotPasswordMaxRequestsLimit() throws Exception {
        AuthRateLimitFilter filter = filter(properties(50, 60, 50, 1, 1000));
        String ip = "203.0.113.6";

        filter.doFilter(postRequest(FORGOT_PASSWORD, ip), new MockHttpServletResponse(), new MockFilterChain());

        MockHttpServletResponse second = new MockHttpServletResponse();
        filter.doFilter(postRequest(FORGOT_PASSWORD, ip), second, new MockFilterChain());

        assertThat(second.getStatus()).isEqualTo(429);
    }

    @Test
    void routeOutsideTheProtectedSetIsNeverLimited() throws Exception {
        AuthRateLimitFilter filter = filter(properties(1, 60, 1, 1, 1000));
        String ip = "203.0.113.7";

        for (int attempt = 0; attempt < 5; attempt++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(postRequest(UNPROTECTED_PATH, ip), response, new MockFilterChain());
            assertThat(response.getStatus()).as("attempt %d on unprotected route", attempt).isEqualTo(200);
        }
    }

    @Test
    void nonPostMethodIsNeverLimited() throws Exception {
        AuthRateLimitFilter filter = filter(properties(1, 60, 1, 1, 1000));
        String ip = "203.0.113.8";

        for (int attempt = 0; attempt < 5; attempt++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", LOGIN);
            request.setRemoteAddr(ip);
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new MockFilterChain());

            assertThat(response.getStatus()).as("GET attempt %d", attempt).isEqualTo(200);
        }
    }

    @Test
    void expiredEntriesAreEvictedByTheScheduledCleanup() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2024-01-01T00:00:00Z"));
        AuthRateLimitFilter filter = filter(properties(1, 1, 1, 1, 1000));
        filter.setClock(clock);
        String ip = "203.0.113.9";

        filter.doFilter(postRequest(LOGIN, ip), new MockHttpServletResponse(), new MockFilterChain());

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(postRequest(LOGIN, ip), blocked, new MockFilterChain());
        assertThat(blocked.getStatus()).isEqualTo(429);

        clock.advance(Duration.ofSeconds(5));
        filter.cleanupStaleEntries();

        MockHttpServletResponse afterCleanup = new MockHttpServletResponse();
        MockFilterChain chainAfterCleanup = new MockFilterChain();
        filter.doFilter(postRequest(LOGIN, ip), afterCleanup, chainAfterCleanup);

        assertThat(afterCleanup.getStatus()).as("stale entry should have been evicted, freeing the window").isEqualTo(200);
        assertThat(chainAfterCleanup.getRequest()).isNotNull();
    }

    @Test
    void maxTrackedKeysCapsMemoryGrowthAndFailsOpenForNewKeys() throws Exception {
        AuthRateLimitFilter filter = filter(properties(1, 300, 1, 1, 2));

        filter.doFilter(postRequest(LOGIN, "203.0.113.10"), new MockHttpServletResponse(), new MockFilterChain());
        filter.doFilter(postRequest(LOGIN, "203.0.113.11"), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(trackedKeyCount(filter)).isEqualTo(2);

        for (int index = 0; index < 50; index++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(postRequest(LOGIN, "203.0.113." + (20 + index)), response, chain);

            assertThat(response.getStatus()).as("new key %d beyond capacity fails open", index).isEqualTo(200);
            assertThat(chain.getRequest()).isNotNull();
        }

        assertThat(trackedKeyCount(filter))
                .as("tracked keys must never exceed maxTrackedKeys")
                .isEqualTo(2);
    }

    private int trackedKeyCount(AuthRateLimitFilter filter) {
        Map<?, ?> hitsByKey = (Map<?, ?>) ReflectionTestUtils.getField(filter, "hitsByKey");
        return hitsByKey.size();
    }

    private AuthRateLimitFilter filter(AuthRateLimitProperties properties) {
        return new AuthRateLimitFilter(properties, objectMapper);
    }

    private AuthRateLimitProperties properties(int maxRequests, int windowSeconds, int registerMaxRequests,
            int forgotPasswordMaxRequests, int maxTrackedKeys) {
        return new AuthRateLimitProperties(true, maxRequests, windowSeconds, registerMaxRequests,
                forgotPasswordMaxRequests, maxTrackedKeys);
    }

    private MockHttpServletRequest postRequest(String path, String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(remoteAddr);
        return request;
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zone;

        MutableClock(Instant instant) {
            this(instant, ZoneOffset.UTC);
        }

        private MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
