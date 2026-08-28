package com.rrsistemas.erauma;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrsistemas.erauma.auth.PasswordResetToken;
import com.rrsistemas.erauma.auth.PasswordResetTokenRepository;
import com.rrsistemas.erauma.family.FamilyMemberRepository;
import com.rrsistemas.erauma.family.FamilyMemberRole;
import com.rrsistemas.erauma.story.MockStoryImageGenerator;
import com.rrsistemas.erauma.user.AppUser;
import com.rrsistemas.erauma.user.AppUserRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EraUmaApplicationTests {
    private static final Path TEST_STORAGE_ROOT = Path.of(System.getProperty("java.io.tmpdir"), "erauma-test-storage-" + UUID.randomUUID());

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.local-path", () -> TEST_STORAGE_ROOT.toString());
        registry.add("app.story.generator", () -> "mock");
        registry.add("app.story.illustrated-daily-limit", () -> "2");
        registry.add("app.story.image.generation-enabled", () -> "true");
        registry.add("openai.api-key", () -> "");
    }

    @AfterAll
    static void cleanupTestStorage() throws IOException {
        if (!Files.exists(TEST_STORAGE_ROOT)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(TEST_STORAGE_ROOT)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        }
    }

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ApplicationContext applicationContext;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    AppUserRepository users;
    @Autowired
    FamilyMemberRepository members;
    @Autowired
    PasswordResetTokenRepository passwordResetTokens;
    @Autowired
    MultipartProperties multipartProperties;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    MockStoryImageGenerator mockStoryImageGenerator;

    @Test
    void doesNotCreateDefaultInMemoryUserDetailsService() {
        assertThat(applicationContext.getBeansOfType(InMemoryUserDetailsManager.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(UserDetailsService.class)).isEmpty();
    }

    @Test
    void actuatorHealthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void protectedApiRequiresJwtAuthentication() throws Exception {
        mockMvc.perform(get("/api/families/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registersUserWithoutReturningPassword() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Rafael","email":"RAFAEL@email.com","password":"segredo1"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("rafael@email.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        assertThat(users.findByEmailAndActiveTrue("rafael@email.com").orElseThrow().getPasswordHash()).startsWith("$2");
    }

    @Test
    void rejectsDuplicateEmail() throws Exception {
        register("duplicate@email.com", "segredo1");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Outro","email":"DUPLICATE@email.com","password":"segredo1"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void logsInWithValidCredentialsAndRejectsInvalidCredentials() throws Exception {
        register("login@email.com", "segredo1");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"login@email.com","password":"segredo1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"login@email.com","password":"errada"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void recoversPasswordWithoutEmailEnumerationAndInvalidatesOldPassword() throws Exception {
        register("reset@email.com", "segredo1");

        String unknown = mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"unknown@email.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Se este e-mail estiver cadastrado, enviaremos as instruções para redefinir sua senha."))
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(unknown).path("resetToken").isNull()).isTrue();

        String forgot = mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"reset@email.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Se este e-mail estiver cadastrado, enviaremos as instruções para redefinir sua senha."))
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(forgot).get("resetToken").asText();
        assertThat(token).isNotBlank();
        AppUser user = users.findByEmailAndActiveTrue("reset@email.com").orElseThrow();
        assertThat(user.getPasswordHash()).doesNotContain("segredo1");

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","newPassword":"nova123","confirmPassword":"diferente"}
                                """.formatted(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_CONFIRMATION_MISMATCH"));

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","newPassword":"123","confirmPassword":"123"}
                                """.formatted(token)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"invalido","newPassword":"nova123","confirmPassword":"nova123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_TOKEN_INVALID"));

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","newPassword":"nova123","confirmPassword":"nova123"}
                                """.formatted(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Senha alterada com sucesso."));

        assertThat(users.findByEmailAndActiveTrue("reset@email.com").orElseThrow().getPasswordHash()).doesNotContain("nova123");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"reset@email.com\",\"password\":\"segredo1\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"reset@email.com\",\"password\":\"nova123\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","newPassword":"outra123","confirmPassword":"outra123"}
                                """.formatted(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_TOKEN_INVALID"));
    }

    @Test
    void rejectsExpiredPasswordResetToken() throws Exception {
        register("expired-reset@email.com", "segredo1");
        AppUser user = users.findByEmailAndActiveTrue("expired-reset@email.com").orElseThrow();
        String token = "expired-token";
        passwordResetTokens.save(new PasswordResetToken(user, hashToken(token), Instant.now().minusSeconds(60)));

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","newPassword":"nova123","confirmPassword":"nova123"}
                                """.formatted(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_TOKEN_INVALID"));
    }

    @Test
    void allowsLocalCorsPreflightBeforeAuthentication() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.options("/api/auth/login")
                        .header("Origin", "http://localhost:8082")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Authorization, Content-Type, Accept"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getHeader("Access-Control-Allow-Origin")).isEqualTo("http://localhost:8082"))
                .andExpect(result -> assertThat(result.getResponse().getHeader("Access-Control-Allow-Headers")).contains("Authorization"))
                .andExpect(result -> assertThat(result.getResponse().getHeader("Access-Control-Allow-Headers")).contains("Content-Type"))
                .andExpect(result -> assertThat(result.getResponse().getHeader("Access-Control-Allow-Headers")).contains("Accept"));
    }

    @Test
    void allowsLanExpoWebCorsPreflightBeforeAuthentication() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.options("/api/auth/login")
                        .header("Origin", "http://192.168.0.6:8081")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Authorization, Content-Type, Accept"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getHeader("Access-Control-Allow-Origin")).isEqualTo("http://192.168.0.6:8081"))
                .andExpect(result -> assertThat(result.getResponse().getHeader("Access-Control-Allow-Methods")).contains("GET"))
                .andExpect(result -> assertThat(result.getResponse().getHeader("Access-Control-Allow-Methods")).contains("POST"))
                .andExpect(result -> assertThat(result.getResponse().getHeader("Access-Control-Allow-Methods")).contains("PUT"))
                .andExpect(result -> assertThat(result.getResponse().getHeader("Access-Control-Allow-Methods")).contains("PATCH"))
                .andExpect(result -> assertThat(result.getResponse().getHeader("Access-Control-Allow-Methods")).contains("DELETE"))
                .andExpect(result -> assertThat(result.getResponse().getHeader("Access-Control-Allow-Methods")).contains("OPTIONS"))
                .andExpect(result -> assertThat(result.getResponse().getHeader("Access-Control-Allow-Headers")).contains("Authorization"))
                .andExpect(result -> assertThat(result.getResponse().getHeader("Access-Control-Allow-Headers")).contains("Content-Type"))
                .andExpect(result -> assertThat(result.getResponse().getHeader("Access-Control-Allow-Headers")).contains("Accept"));
    }

    @Test
    void includesCorsHeadersForLocalLoginRequest() throws Exception {
        register("cors-login@email.com", "segredo1");

        mockMvc.perform(post("/api/auth/login")
                        .header("Origin", "http://127.0.0.1:8090")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"cors-login@email.com","password":"segredo1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getHeader("Access-Control-Allow-Origin")).isEqualTo("http://127.0.0.1:8090"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void blocksCorsPreflightFromDisallowedOrigin() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.options("/api/auth/login")
                        .header("Origin", "https://malicious.example")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Authorization, Content-Type, Accept"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createsFamilyOwnerAndChild() throws Exception {
        String token = token("family@email.com");
        UUID familyId = createFamily(token, "Família Trancoso");

        assertThat(members.findByFamily_IdAndUser_IdAndActiveTrue(familyId, users.findByEmailAndActiveTrue("family@email.com").orElseThrow().getId()))
                .get().extracting("role").isEqualTo(FamilyMemberRole.OWNER);

        mockMvc.perform(post("/api/families/{familyId}/children", familyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Nando","nickname":"Nandinho"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Nando"))
                .andExpect(jsonPath("$.favoriteAnimal").value(org.hamcrest.Matchers.nullValue()));

        mockMvc.perform(get("/api/families/{familyId}/children", familyId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Nando"));
    }

    @Test
    void childVisualProfileIsOptionalAndSupportsInclusiveOptions() throws Exception {
        String token = token("child-visual@email.com");
        UUID familyId = createFamily(token, "Familia Visual");

        mockMvc.perform(post("/api/families/{familyId}/children", familyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Nando"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.visualPresentation").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.skinTone").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.specialFeatures").value(org.hamcrest.Matchers.nullValue()));

        mockMvc.perform(post("/api/families/{familyId}/children", familyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Theo","visualPresentation":"BOY","skinTone":"MEDIUM","hairTexture":"CURLY"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.visualPresentation").value("BOY"))
                .andExpect(jsonPath("$.skinTone").value("MEDIUM"))
                .andExpect(jsonPath("$.hairTexture").value("CURLY"));

        mockMvc.perform(post("/api/families/{familyId}/children", familyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Lia","visualPresentation":"GIRL","skinTone":"LIGHT","hairTexture":"WAVY"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.visualPresentation").value("GIRL"))
                .andExpect(jsonPath("$.skinTone").value("LIGHT"))
                .andExpect(jsonPath("$.hairTexture").value("WAVY"));

        mockMvc.perform(post("/api/families/{familyId}/children", familyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Sol","visualPresentation":"UNSPECIFIED","skinTone":"UNSPECIFIED","hairTexture":"OTHER_OR_UNSPECIFIED","hairColor":"castanho"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.visualPresentation").value("UNSPECIFIED"))
                .andExpect(jsonPath("$.skinTone").value("UNSPECIFIED"))
                .andExpect(jsonPath("$.hairTexture").value("OTHER_OR_UNSPECIFIED"))
                .andExpect(jsonPath("$.hairColor").value("castanho"));
    }

    @Test
    void blocksCrossFamilyAccess() throws Exception {
        String tokenA = token("a@email.com");
        String tokenB = token("b@email.com");
        UUID familyA = createFamily(tokenA, "Família A");

        mockMvc.perform(get("/api/families/{familyId}/children", familyA)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FAMILY_NOT_FOUND"));
    }

    @Test
    void createsListsFiltersFavoritesUpdatesAndDeletesMoment() throws Exception {
        String token = token("moment@email.com");
        UUID familyId = createFamily(token, "Família Momentos");
        UUID childId = createChild(token, familyId, "Nando");
        UUID momentId = createMoment(token, familyId, childId, "Passeio de bicicleta", "2026-08-18T15:00:00");
        createMoment(token, familyId, null, "Bolo em família", "2026-08-17T15:00:00");

        mockMvc.perform(get("/api/families/{familyId}/moments?page=0&size=1", familyId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Passeio de bicicleta"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.last").value(false));

        mockMvc.perform(get("/api/families/{familyId}/moments?childId={childId}", familyId, childId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/api/moments/{momentId}", momentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.children[0].name").value("Nando"))
                .andExpect(jsonPath("$.participants[0].name").value("Papai"));

        mockMvc.perform(patch("/api/moments/{momentId}/favorite", momentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"favorite\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorite").value(true));

        mockMvc.perform(put("/api/moments/{momentId}", momentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Passeio atualizado","description":"Sem rodinhas","occurredAt":"2026-08-19T10:00:00","locationName":"Parque","childIds":["%s"],"participants":[{"name":"Mamãe","participantType":"ADULT"}]}
                                """.formatted(childId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Passeio atualizado"))
                .andExpect(jsonPath("$.participants[0].name").value("Mamãe"));

        mockMvc.perform(delete("/api/moments/{momentId}", momentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/moments/{momentId}", momentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void validatesMomentRequiredFieldsAndChildFamily() throws Exception {
        String tokenA = token("moment-a@email.com");
        String tokenB = token("moment-b@email.com");
        UUID familyA = createFamily(tokenA, "Família A");
        UUID familyB = createFamily(tokenB, "Família B");
        UUID childB = createChild(tokenB, familyB, "Sofia");

        mockMvc.perform(post("/api/families/{familyId}/moments", familyA)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"occurredAt\":\"2026-08-18T15:00:00\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/families/{familyId}/moments", familyA)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Sem data\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/families/{familyId}/moments", familyA)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Criança errada","occurredAt":"2026-08-18T15:00:00","childIds":["%s"]}
                                """.formatted(childB)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHILD_NOT_FOUND"));
    }

    @Test
    void validatesMomentPhotoUploadRulesAndIsolation() throws Exception {
        assertThat(multipartProperties.getMaxFileSize().toBytes()).isEqualTo(10L * 1024 * 1024);
        assertThat(multipartProperties.getMaxRequestSize().toBytes()).isEqualTo(100L * 1024 * 1024);

        String tokenA = token("photo-a@email.com");
        String tokenB = token("photo-b@email.com");
        UUID familyA = createFamily(tokenA, "Família A");
        createFamily(tokenB, "Família B");
        UUID momentA = createMoment(tokenA, familyA, null, "Foto", "2026-08-18T15:00:00");
        UUID momentB = createMoment(tokenA, familyA, null, "Foto celular", "2026-08-19T15:00:00");
        UUID momentC = createMoment(tokenA, familyA, null, "Varias fotos", "2026-08-20T15:00:00");

        MockMultipartFile photo = new MockMultipartFile("files", "foto.png", "image/png", new byte[] {1, 2, 3});
        String response = mockMvc.perform(multipart("/api/moments/{momentId}/photos", momentA)
                        .file(photo)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].contentType").value("image/png"))
                .andReturn().getResponse().getContentAsString();
        UUID photoId = UUID.fromString(objectMapper.readTree(response).get(0).get("id").asText());

        mockMvc.perform(get("/api/moment-photos/{photoId}/content", photoId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/moment-photos/{photoId}/content", photoId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        MockMultipartFile phonePhoto = new MockMultipartFile("files", "foto-celular.png", "image/png", new byte[2 * 1024 * 1024]);
        String phoneResponse = mockMvc.perform(multipart("/api/moments/{momentId}/photos", momentB)
                        .file(phonePhoto)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].sizeBytes").value(2 * 1024 * 1024))
                .andReturn().getResponse().getContentAsString();
        UUID phonePhotoId = UUID.fromString(objectMapper.readTree(phoneResponse).get(0).get("id").asText());
        mockMvc.perform(get("/api/moment-photos/{photoId}/content", phonePhotoId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentLength()).isEqualTo(2 * 1024 * 1024));

        mockMvc.perform(multipart("/api/moments/{momentId}/photos", momentC)
                        .file(new MockMultipartFile("files", "foto-1.png", "image/png", new byte[] {1, 2, 3}))
                        .file(new MockMultipartFile("files", "foto-2.jpg", "image/jpeg", new byte[] {4, 5, 6}))
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2));

        MockMultipartHttpServletRequestBuilder tooManyPhotos = multipart("/api/moments/{momentId}/photos", momentA);
        for (int index = 0; index < 10; index++) {
            tooManyPhotos.file(new MockMultipartFile("files", "foto-" + index + ".png", "image/png", new byte[] {1, 2, 3}));
        }
        mockMvc.perform(tooManyPhotos.header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PHOTO_LIMIT_EXCEEDED"));

        MockMultipartFile invalid = new MockMultipartFile("files", "foto.txt", "text/plain", new byte[] {1});
        mockMvc.perform(multipart("/api/moments/{momentId}/photos", momentA)
                        .file(invalid)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILE_TYPE"));

        MockMultipartFile large = new MockMultipartFile("files", "grande.png", "image/png", new byte[11 * 1024 * 1024]);
        mockMvc.perform(multipart("/api/moments/{momentId}/photos", momentA)
                        .file(large)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("PHOTO_TOO_LARGE"))
                .andExpect(jsonPath("$.message").value("A foto deve ter no máximo 10 MB."));

        MockMultipartHttpServletRequestBuilder deleteMultipart = multipart("/api/moment-photos/{photoId}", photoId);
        deleteMultipart.with(request -> { request.setMethod("DELETE"); return request; });
        mockMvc.perform(deleteMultipart.header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());
    }

    @Test
    void generatesListsFiltersFavoritesAndDeletesStory() throws Exception {
        String token = token("story@email.com");
        UUID familyId = createFamily(token, "Família Story");
        UUID childId = createChild(token, familyId, "Nando");
        UUID shortStory = generateStory(token, familyId, childId, null, "Medo do escuro", "ADVENTURE", "SHORT");
        UUID longStory = generateStory(token, familyId, childId, null, "Viagem ao espaço", "FANTASY", "LONG");

        mockMvc.perform(get("/api/stories/{storyId}", shortStory).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value(org.hamcrest.Matchers.containsString("Nando")))
                .andExpect(jsonPath("$.chapters.length()").value(1))
                .andExpect(jsonPath("$.generationType").value("MOCK"));

        mockMvc.perform(get("/api/stories/{storyId}", longStory).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chapters.length()").value(3));

        mockMvc.perform(get("/api/families/{familyId}/stories?page=0&size=1", familyId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.last").value(false));

        mockMvc.perform(get("/api/families/{familyId}/stories?childId={childId}&style=FANTASY", familyId, childId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(patch("/api/stories/{storyId}/favorite", longStory)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"favorite\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorite").value(true));

        mockMvc.perform(get("/api/families/{familyId}/stories?favorite=true", familyId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(put("/api/stories/{storyId}", longStory)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"História editada\",\"favorite\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("História editada"));

        mockMvc.perform(delete("/api/stories/{storyId}", shortStory).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/stories/{storyId}", shortStory).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/families/{familyId}/stories", familyId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(longStory.toString()));

        mockMvc.perform(delete("/api/stories/{storyId}", UUID.randomUUID()).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void generatesStoryWithOptionalChildAndCustomCharacters() throws Exception {
        String token = token("story-characters@email.com");
        UUID familyId = createFamily(token, "FamÃ­lia Personagens");
        UUID childId = createChild(token, familyId, "Fernando Trancoso");

        UUID childFallbackStory = generateStory(token, familyId, childId, null, "Coragem", "ADVENTURE", "SHORT");
        mockMvc.perform(get("/api/stories/{storyId}", childFallbackStory).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mainCharacterName").value("Fernando"))
                .andExpect(jsonPath("$.child.name").value("Fernando Trancoso"));

        String manualResponse = mockMvc.perform(post("/api/families/{familyId}/stories/generate", familyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"childId":"%s","mainCharacterName":"Super Nando","secondCharacterName":"Luna","theme":"Medo do escuro","favoriteAnimal":"Dinossauro","style":"BEDTIME","length":"SHORT"}
                                """.formatted(childId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mainCharacterName").value("Super Nando"))
                .andExpect(jsonPath("$.secondCharacterName").value("Luna"))
                .andExpect(jsonPath("$.favoriteAnimal").value("Dinossauro"))
                .andExpect(jsonPath("$.title").value(org.hamcrest.Matchers.containsString("Super Nando")))
                .andReturn().getResponse().getContentAsString();
        UUID manualStoryId = UUID.fromString(objectMapper.readTree(manualResponse).get("id").asText());

        mockMvc.perform(get("/api/stories/{storyId}", manualStoryId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mainCharacterName").value("Super Nando"))
                .andExpect(jsonPath("$.secondCharacterName").value("Luna"));

        mockMvc.perform(post("/api/families/{familyId}/stories/generate", familyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mainCharacterName":"Capitão Theo","theme":"Floresta encantada","style":"FANTASY","length":"SHORT"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mainCharacterName").value("Capitão Theo"))
                .andExpect(jsonPath("$.favoriteAnimal").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.child").doesNotExist());

        mockMvc.perform(post("/api/families/{familyId}/stories/generate", familyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"theme":"Sem personagem","style":"FUNNY","length":"SHORT"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MAIN_CHARACTER_REQUIRED"));
    }

    @Test
    void validatesStoryRelationshipsMomentSourceAndIsolation() throws Exception {
        String tokenA = token("story-a@email.com");
        String tokenB = token("story-b@email.com");
        UUID familyA = createFamily(tokenA, "Família A");
        UUID familyB = createFamily(tokenB, "Família B");
        UUID childA = createChild(tokenA, familyA, "Nando");
        UUID childB = createChild(tokenB, familyB, "Sofia");
        UUID momentA = createMoment(tokenA, familyA, childA, "Primeiro passeio de bicicleta", "2026-08-18T15:00:00");
        UUID momentB = createMoment(tokenB, familyB, childB, "Momento B", "2026-08-18T15:00:00");
        UUID storyA = generateStory(tokenA, familyA, childA, momentA, "Coragem", "ADVENTURE", "MEDIUM");

        mockMvc.perform(post("/api/families/{familyId}/stories/generate", familyA)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"childId":"%s","theme":"Criança errada","style":"FUNNY","length":"SHORT"}
                                """.formatted(childB)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/families/{familyId}/stories/generate", familyA)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"childId":"%s","sourceMomentId":"%s","theme":"Momento errado","style":"FUNNY","length":"SHORT"}
                                """.formatted(childA, momentB)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/families/{familyId}/stories/generate", familyA)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"childId":"%s","theme":"","style":"FUNNY","length":"SHORT"}
                                """.formatted(childA)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/families/{familyId}/stories/generate", familyA)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"childId":"%s","theme":"Enum inválido","style":"SCARY","length":"SHORT"}
                                """.formatted(childA)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/stories/{storyId}", storyA).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/stories/{storyId}/favorite", storyA).header("Authorization", "Bearer " + tokenB).contentType(MediaType.APPLICATION_JSON).content("{\"favorite\":true}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/stories/{storyId}", storyA).header("Authorization", "Bearer " + tokenB).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Ataque\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/stories/{storyId}", storyA).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    void supportsPhaseSevenFiltersCalendarAndMomentStoryRelation() throws Exception {
        String token = token("phase7@email.com");
        UUID familyId = createFamily(token, "Família Fase 7");
        UUID childA = createChild(token, familyId, "Fernando");
        UUID childB = createChild(token, familyId, "Lety");
        createMoment(token, familyId, childA, "Ano novo", "2026-01-01T09:00:00");
        UUID momentA = createMoment(token, familyId, childA, "Passeio de bicicleta", "2026-08-21T10:00:00");
        createMoment(token, familyId, childB, "Primeiro dia na escola", "2026-08-21T15:00:00");
        createMoment(token, familyId, childA, "História antes de dormir", "2026-08-12T20:00:00");
        createMoment(token, familyId, childB, "Virada do ano", "2026-12-31T21:00:00");
        mockMvc.perform(post("/api/families/{familyId}/moments", familyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Momento com duas criancas","occurredAt":"2026-08-23T16:00:00","childIds":["%s","%s"]}
                                """.formatted(childA, childB)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.children.length()").value(2));
        UUID storyA = generateStory(token, familyId, childA, momentA, "Coragem", "ADVENTURE", "SHORT");

        mockMvc.perform(patch("/api/stories/{storyId}/favorite", storyA)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"favorite\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorite").value(true));

        mockMvc.perform(get("/api/families/{familyId}/children", familyId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/families/{familyId}/stories?childId={childId}&favorite=true&style=ADVENTURE&generationMode=TEXT_ONLY", familyId, childA)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(storyA.toString()));

        mockMvc.perform(get("/api/families/{familyId}/moments?childId={childId}&from=2026-08-21&to=2026-08-21", familyId, childA)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Passeio de bicicleta"));

        mockMvc.perform(get("/api/families/{familyId}/moments?from=2026-08-22&to=2026-08-22", familyId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content").isEmpty());

        mockMvc.perform(get("/api/families/{familyId}/moments/calendar?year=2026&month=8", familyId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.date == '2026-08-21')].count").value(org.hamcrest.Matchers.contains(2)))
                .andExpect(jsonPath("$[?(@.date == '2026-08-23')].count").value(org.hamcrest.Matchers.contains(1)));

        mockMvc.perform(get("/api/families/{familyId}/moments/calendar?year=2026&month=1", familyId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.date == '2026-01-01')].count").value(org.hamcrest.Matchers.contains(1)));

        mockMvc.perform(get("/api/families/{familyId}/moments/calendar?year=2026&month=12", familyId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.date == '2026-12-31')].count").value(org.hamcrest.Matchers.contains(1)));

        String noChildStory = mockMvc.perform(post("/api/families/{familyId}/stories/generate", familyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceMomentId":"%s","mainCharacterName":"Nando","theme":"Coragem","style":"ADVENTURE","length":"SHORT"}
                                """.formatted(momentA)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.child").doesNotExist())
                .andExpect(jsonPath("$.images").isEmpty())
                .andReturn().getResponse().getContentAsString();
        UUID noChildStoryId = UUID.fromString(objectMapper.readTree(noChildStory).get("id").asText());

        mockMvc.perform(get("/api/moments/{momentId}", momentA).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stories[*].id").value(org.hamcrest.Matchers.hasItems(storyA.toString(), noChildStoryId.toString())))
                .andExpect(jsonPath("$.stories[*].title").isNotEmpty());
    }

    @Test
    void textOnlyStoryDoesNotCreateImagesAndIllustratedCreatesPrivateImages() throws Exception {
        String tokenA = token("story-image-a@email.com");
        String tokenB = token("story-image-b@email.com");
        UUID familyA = createFamily(tokenA, "FamÃ­lia Imagem A");
        createFamily(tokenB, "FamÃ­lia Imagem B");
        UUID childA = createChild(tokenA, familyA, "Nando");

        String textOnly = mockMvc.perform(post("/api/families/{familyId}/stories/generate", familyA)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"childId":"%s","theme":"Coragem","style":"BEDTIME","length":"SHORT"}
                                """.formatted(childA)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.images").isArray())
                .andExpect(jsonPath("$.images").isEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(textOnly).doesNotContain("storage");

        String illustrated = mockMvc.perform(post("/api/families/{familyId}/stories/generate", familyA)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"childId":"%s","theme":"Amizade","style":"FANTASY","length":"MEDIUM","generationMode":"ILLUSTRATED"}
                                """.formatted(childA)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.images.length()").value(3))
                .andExpect(jsonPath("$.images[0].type").value("COVER"))
                .andExpect(jsonPath("$.images[0].status").value("GENERATED"))
                .andExpect(jsonPath("$.images[0].contentUrl").value(org.hamcrest.Matchers.startsWith("/api/story-images/")))
                .andExpect(jsonPath("$.images[0].storageKey").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        JsonNode image = objectMapper.readTree(illustrated).get("images").get(0);
        UUID imageId = UUID.fromString(image.get("id").asText());
        mockMvc.perform(get("/api/story-images/{imageId}/content", imageId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentType()).isEqualTo("image/png"))
                .andExpect(result -> assertThat(result.getResponse().getContentLength()).isGreaterThan(0))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).startsWith(new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47}));
        mockMvc.perform(get("/api/story-images/{imageId}/content", imageId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/story-images/{imageId}/content", imageId).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(result -> assertJsonErrorContentType(result.getResponse().getContentType()))
                .andExpect(jsonPath("$.code").value("FAMILY_NOT_FOUND"));
        mockMvc.perform(get("/api/story-images/{imageId}/content", UUID.randomUUID()).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(result -> assertJsonErrorContentType(result.getResponse().getContentType()))
                .andExpect(jsonPath("$.code").value("STORY_IMAGE_NOT_FOUND"));
    }

    @Test
    void storyImageContentErrorsReturnJsonWithoutImageContentType() throws Exception {
        String token = token("story-image-content-errors@email.com");
        UUID familyId = createFamily(token, "Familia Erros Conteudo");
        UUID childId = createChild(token, familyId, "Nando");

        String illustrated = generateIllustratedStory(token, familyId, childId, "Erros controlados")
                .andExpect(jsonPath("$.images.length()").value(3))
                .andReturn().getResponse().getContentAsString();

        JsonNode images = objectMapper.readTree(illustrated).get("images");
        UUID validImageId = UUID.fromString(images.get(0).get("id").asText());
        UUID missingFileImageId = UUID.fromString(images.get(1).get("id").asText());
        UUID failedImageId = UUID.fromString(images.get(2).get("id").asText());

        String missingStorageKey = jdbcTemplate.queryForObject("select storage_key from story_image where id = ?", String.class, missingFileImageId);
        Path missingFile = TEST_STORAGE_ROOT.resolve("stories").resolve(missingStorageKey).normalize();
        assertThat(missingFile).startsWith(TEST_STORAGE_ROOT.resolve("stories").normalize());
        Files.deleteIfExists(missingFile);
        jdbcTemplate.update("update story_image set status = 'FAILED' where id = ?", failedImageId);

        mockMvc.perform(get("/api/story-images/{imageId}/content", validImageId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentType()).isEqualTo("image/png"))
                .andExpect(result -> assertThat(result.getResponse().getContentLength()).isGreaterThan(0))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray().length).isGreaterThan(0));

        mockMvc.perform(get("/api/story-images/{imageId}/content", missingFileImageId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(result -> assertJsonErrorContentType(result.getResponse().getContentType()))
                .andExpect(jsonPath("$.code").value("STORY_IMAGE_NOT_FOUND"));

        mockMvc.perform(get("/api/story-images/{imageId}/content", failedImageId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(result -> assertJsonErrorContentType(result.getResponse().getContentType()))
                .andExpect(jsonPath("$.code").value("STORY_IMAGE_NOT_FOUND"));
    }

    @Test
    void illustratedStoryUsesChildVisualProfileInAllImagePromptsWithoutRealOpenAi() throws Exception {
        String token = token("story-visual-profile@email.com");
        UUID familyId = createFamily(token, "Familia Perfil Visual");
        UUID childId = createVisualChild(token, familyId);
        mockStoryImageGenerator.clearPrompts();

        String response = generateIllustratedStory(token, familyId, childId, "Amizade no jardim")
                .andExpect(jsonPath("$.images.length()").value(3))
                .andExpect(jsonPath("$.images[0].status").value("GENERATED"))
                .andExpect(jsonPath("$.images[1].status").value("GENERATED"))
                .andExpect(jsonPath("$.images[2].status").value("GENERATED"))
                .andReturn().getResponse().getContentAsString();

        UUID storyId = UUID.fromString(objectMapper.readTree(response).get("id").asText());
        Integer imageRows = jdbcTemplate.queryForObject("select count(*) from story_image where story_id = ?", Integer.class, storyId);
        assertThat(imageRows).isEqualTo(3);
        assertThat(mockStoryImageGenerator.prompts()).hasSize(3);
        assertThat(mockStoryImageGenerator.prompts())
                .allSatisfy(prompt -> assertThat(prompt)
                        .contains("Perfil visual do protagonista")
                        .contains("apresentacao visual: menina")
                        .contains("tom de pele: moreno")
                        .contains("cabelo cor: preto")
                        .contains("cabelo comprimento: curto")
                        .contains("cabelo textura: cacheado")
                        .contains("olhos: castanhos")
                        .contains("detalhes especiais: usa oculos vermelhos"));
    }

    @Test
    void oneFailedImageDoesNotInvalidateIllustratedStoryOrOtherImages() throws Exception {
        String token = token("story-partial-image-failure@email.com");
        UUID familyId = createFamily(token, "Familia Falha Parcial");
        UUID childId = createChild(token, familyId, "Nando");

        String response = generateIllustratedStory(token, familyId, childId, "mock-fail-scene-1")
                .andExpect(jsonPath("$.images.length()").value(3))
                .andExpect(jsonPath("$.images[0].status").value("GENERATED"))
                .andExpect(jsonPath("$.images[1].status").value("FAILED"))
                .andExpect(jsonPath("$.images[1].contentUrl").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.images[2].status").value("GENERATED"))
                .andReturn().getResponse().getContentAsString();

        UUID storyId = UUID.fromString(objectMapper.readTree(response).get("id").asText());
        Integer imageRows = jdbcTemplate.queryForObject("select count(*) from story_image where story_id = ?", Integer.class, storyId);
        Integer failedRows = jdbcTemplate.queryForObject("select count(*) from story_image where story_id = ? and status = 'FAILED'", Integer.class, storyId);
        assertThat(imageRows).isEqualTo(3);
        assertThat(failedRows).isEqualTo(1);

        mockMvc.perform(get("/api/stories/{storyId}", storyId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(storyId.toString()))
                .andExpect(jsonPath("$.images.length()").value(3));
    }

    @Test
    void illustratedShortStoryStillCreatesCoverAndTwoScenes() throws Exception {
        String token = token("story-short-illustrated@email.com");
        UUID familyId = createFamily(token, "Familia Ilustrada Curta");
        UUID childId = createChild(token, familyId, "Nando");

        String response = mockMvc.perform(post("/api/families/{familyId}/stories/generate", familyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"childId":"%s","theme":"Praia","style":"ADVENTURE","length":"SHORT","generationMode":"ILLUSTRATED"}
                                """.formatted(childId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.chapters.length()").value(1))
                .andExpect(jsonPath("$.images.length()").value(3))
                .andExpect(jsonPath("$.images[0].type").value("COVER"))
                .andExpect(jsonPath("$.images[1].type").value("SCENE"))
                .andExpect(jsonPath("$.images[2].type").value("SCENE"))
                .andReturn().getResponse().getContentAsString();

        UUID storyId = UUID.fromString(objectMapper.readTree(response).get("id").asText());
        Integer imageRows = jdbcTemplate.queryForObject("select count(*) from story_image where story_id = ?", Integer.class, storyId);
        assertThat(imageRows).isEqualTo(3);
    }

    @Test
    void enforcesConfigurableIllustratedStoryLimitByFamilyAndCurrentDayWithoutCallingOpenAi() throws Exception {
        String token = token("illustrated-limit@email.com");
        UUID familyA = createFamily(token, "Familia Ilustrada A");
        UUID childA = createChild(token, familyA, "Nando");

        generateStory(token, familyA, childA, null, "Texto 1", "BEDTIME", "SHORT");
        generateStory(token, familyA, childA, null, "Texto 2", "BEDTIME", "SHORT");
        generateStory(token, familyA, childA, null, "Texto 3", "BEDTIME", "SHORT");

        generateIllustratedStory(token, familyA, childA, "Ilustrada 1")
                .andExpect(jsonPath("$.images.length()").value(3))
                .andExpect(jsonPath("$.images[0].status").value("GENERATED"));
        generateIllustratedStory(token, familyA, childA, "Ilustrada 2")
                .andExpect(jsonPath("$.images.length()").value(3))
                .andExpect(jsonPath("$.images[1].status").value("GENERATED"));
        mockMvc.perform(post("/api/families/{familyId}/stories/generate", familyA)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"childId":"%s","theme":"Ilustrada 3","style":"FANTASY","length":"MEDIUM","generationMode":"ILLUSTRATED"}
                                """.formatted(childA)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("STORY_ILLUSTRATED_DAILY_LIMIT_REACHED"));

        UUID familyB = createFamily(token, "Familia Ilustrada B");
        UUID childB = createChild(token, familyB, "Lia");
        generateIllustratedStory(token, familyB, childB, "Outra familia")
                .andExpect(jsonPath("$.images[0].status").value("GENERATED"));

        String tokenDay = token("illustrated-current-day@email.com");
        UUID familyDay = createFamily(tokenDay, "Familia Dia Atual");
        UUID childDay = createChild(tokenDay, familyDay, "Theo");
        String oldStory = generateIllustratedStory(tokenDay, familyDay, childDay, "Ontem")
                .andExpect(jsonPath("$.images[0].status").value("GENERATED"))
                .andReturn().getResponse().getContentAsString();
        UUID oldStoryId = UUID.fromString(objectMapper.readTree(oldStory).get("id").asText());
        jdbcTemplate.update("update story set created_at = ? where id = ?", java.sql.Timestamp.from(Instant.now().minusSeconds(2 * 24 * 60 * 60)), oldStoryId);

        generateIllustratedStory(tokenDay, familyDay, childDay, "Hoje 1")
                .andExpect(jsonPath("$.images[0].status").value("GENERATED"));
        generateIllustratedStory(tokenDay, familyDay, childDay, "Hoje 2")
                .andExpect(jsonPath("$.images[0].status").value("GENERATED"));
        mockMvc.perform(post("/api/families/{familyId}/stories/generate", familyDay)
                        .header("Authorization", "Bearer " + tokenDay)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"childId":"%s","theme":"Hoje 3","style":"FANTASY","length":"MEDIUM","generationMode":"ILLUSTRATED"}
                                """.formatted(childDay)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("STORY_ILLUSTRATED_DAILY_LIMIT_REACHED"));
    }

    @Test
    void enforcesDailyStoryGenerationLimitWithoutCallingOpenAi() throws Exception {
        String token = token("story-limit@email.com");
        UUID familyId = createFamily(token, "FamÃ­lia Limite");
        UUID childId = createChild(token, familyId, "Nando");

        for (int index = 0; index < 10; index++) {
            generateStory(token, familyId, childId, null, "Tema " + index, "BEDTIME", "SHORT");
        }

        mockMvc.perform(post("/api/families/{familyId}/stories/generate", familyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"childId":"%s","theme":"Mais uma","style":"BEDTIME","length":"SHORT"}
                                """.formatted(childId)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("STORY_DAILY_LIMIT_REACHED"));
    }

    private void register(String email, String password) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Usuário","email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isCreated());
    }

    private String token(String email) throws Exception {
        register(email, "segredo1");
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"segredo1"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private UUID createFamily(String token, String name) throws Exception {
        String response = mockMvc.perform(post("/api/families")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return UUID.fromString(json.get("id").asText());
    }

    private UUID createChild(String token, UUID familyId, String name) throws Exception {
        String response = mockMvc.perform(post("/api/families/{familyId}/children", familyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private UUID createVisualChild(String token, UUID familyId) throws Exception {
        String response = mockMvc.perform(post("/api/families/{familyId}/children", familyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Lia","birthDate":"2022-08-24","visualPresentation":"GIRL","skinTone":"BROWN","hairColor":"preto","hairLength":"curto","hairTexture":"CURLY","eyeColor":"castanhos","specialFeatures":"usa oculos vermelhos"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private UUID createMoment(String token, UUID familyId, UUID childId, String title, String occurredAt) throws Exception {
        String children = childId == null ? "" : "\"childIds\":[\"" + childId + "\"],";
        String response = mockMvc.perform(post("/api/families/{familyId}/moments", familyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","description":"Memória afetiva","occurredAt":"%s","locationName":"Parque",%s"participants":[{"name":"Papai","participantType":"ADULT"}]}
                                """.formatted(title, occurredAt, children)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private UUID generateStory(String token, UUID familyId, UUID childId, UUID sourceMomentId, String theme, String style, String length) throws Exception {
        String source = sourceMomentId == null ? "" : "\"sourceMomentId\":\"" + sourceMomentId + "\",";
        String response = mockMvc.perform(post("/api/families/{familyId}/stories/generate", familyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"childId":"%s",%s"theme":"%s","place":"Floresta","favoriteAnimal":"Dinossauro","style":"%s","length":"%s"}
                                """.formatted(childId, source, theme, style, length)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private ResultActions generateIllustratedStory(String token, UUID familyId, UUID childId, String theme) throws Exception {
        return mockMvc.perform(post("/api/families/{familyId}/stories/generate", familyId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"childId":"%s","theme":"%s","style":"FANTASY","length":"MEDIUM","generationMode":"ILLUSTRATED"}
                        """.formatted(childId, theme)))
                .andExpect(status().isCreated());
    }

    private void assertJsonErrorContentType(String contentType) {
        assertThat(contentType).isNotEqualTo("image/png");
        assertThat(MediaType.parseMediaType(contentType).isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
    }

    private String hashToken(String token) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }
}
