package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.security.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/install-parity-store.json")
@AutoConfigureMockMvc
class InstallParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/install-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset install parity test store", ex);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AuthService authService;

    @Test
    void setupRejectsOriginalRequiredFields() throws Exception {
        assertValidationFailure(postJson(Map.of(
                "adminPassword", "123qwe",
                "webSiteUrl", "http://localhost:5173/",
                "defaultLanguage", "zh-Hans"
        )));

        assertValidationFailure(postJson(Map.of(
                "connectionString", "Server=(local);Database=Capability;",
                "webSiteUrl", "http://localhost:5173/",
                "defaultLanguage", "zh-Hans"
        )));

        assertValidationFailure(postJson(Map.of(
                "connectionString", "Server=(local);Database=Capability;",
                "adminPassword", "123qwe",
                "defaultLanguage", "zh-Hans"
        )));

        assertValidationFailure(postJson(Map.of(
                "connectionString", "Server=(local);Database=Capability;",
                "adminPassword", "123qwe",
                "webSiteUrl", "http://localhost:5173/"
        )));
    }

    @Test
    void setupRejectsSecondRunWhenOriginalDatabaseAlreadyExists() throws Exception {
        JsonNode first = postJson(validSetup("Server=(local);Database=CapabilityFirst;",
                "http://first.example/", "http://api-first.example/"));
        assertThat(first.path("success").asBoolean()).isTrue();

        JsonNode second = postJson(validSetup("Server=(local);Database=CapabilitySecond;",
                "http://second.example/", "http://api-second.example/"));

        assertThat(second.path("success").asBoolean()).isFalse();
        assertThat(second.path("error").path("message").asText()).isEqualTo("Setup process is already done.");
        JsonNode settings = postJson("/api/services/app/Install/GetAppSettingsJson", Map.of());
        assertThat(settings.path("success").asBoolean()).isTrue();
        assertThat(settings.path("result").path("webSiteUrl").asText()).isEqualTo("http://first.example/");
        assertThat(settings.path("result").path("serverSiteUrl").asText()).isEqualTo("http://api-first.example/");
    }

    @Test
    void getAppSettingsJsonKeepsOriginalInitialLanguagesInsteadOfMutableLanguageStore() throws Exception {
        String customLanguage = "x-install-language-" + System.nanoTime();
        JsonNode created = postJsonWithToken("/api/services/app/Language/CreateOrUpdateLanguage", Map.of(
                "language", Map.of(
                        "name", customLanguage,
                        "displayName", "Install Test Language",
                        "icon", "famfamfam-flag-test",
                        "isDisabled", false
                )
        ));
        assertThat(created.path("success").asBoolean()).isTrue();

        JsonNode settings = postJson("/api/services/app/Install/GetAppSettingsJson", Map.of());

        assertThat(settings.path("success").asBoolean()).isTrue();
        assertThat(settings.path("result").path("languages"))
                .allSatisfy(language -> assertThat(language.path("value").asText()).isNotEqualTo(customLanguage));
    }

    private JsonNode postJson(Object payload) throws Exception {
        return postJson("/api/services/app/Install/Setup", payload);
    }

    private JsonNode postJson(String url, Object payload) throws Exception {
        String body = mockMvc.perform(post(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(payload)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    private JsonNode postJsonWithToken(String url, Object payload) throws Exception {
        String body = mockMvc.perform(post(url)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(payload)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    private void assertValidationFailure(JsonNode response) {
        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText()).isEqualTo("Validation failed");
    }

    private Map<String, Object> validSetup(String connectionString, String webSiteUrl, String serverUrl) {
        return Map.of(
                "connectionString", connectionString,
                "adminPassword", "123qwe",
                "webSiteUrl", webSiteUrl,
                "serverUrl", serverUrl,
                "defaultLanguage", "zh-Hans"
        );
    }

    private String adminToken() {
        return authService.authenticate("admin", "123qwe").orElseThrow().token();
    }
}
