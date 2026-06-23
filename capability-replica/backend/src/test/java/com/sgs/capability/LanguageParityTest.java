package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.model.LanguageItem;
import com.sgs.capability.security.AuthService;
import com.sgs.capability.service.CapabilityStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/language-parity-store.json")
@AutoConfigureMockMvc
class LanguageParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/language-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset language parity test store", ex);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthService authService;

    @Autowired
    CapabilityStore store;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void createOrUpdateLanguageKeepsOriginalVoidResponseWhileSavingLanguage() throws Exception {
        String languageName = "de";
        JsonNode response = postAbp("/api/services/app/Language/CreateOrUpdateLanguage", Map.of(
                "language", Map.of(
                        "name", languageName,
                        "displayName", "Submitted Display Name Should Be Ignored",
                        "icon", "famfamfam-flags de",
                        "isEnabled", true
                )
        ));

        assertThat(response.path("result").isNull()).isTrue();
        assertThat(store.languages()).anySatisfy(language -> {
            assertThat(language.name).isEqualTo(languageName);
            assertThat(language.displayName).isEqualTo("German");
            assertThat(language.icon).isEqualTo("famfamfam-flags de");
            assertThat(language.isDisabled).isFalse();
        });
    }

    @Test
    void createOrUpdateLanguageRejectsDuplicateNameLikeOriginalService() throws Exception {
        String body = mockMvc.perform(post("/api/services/app/Language/CreateOrUpdateLanguage")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "language", Map.of(
                                        "name", "zh-Hans",
                                        "displayName", "Duplicate Chinese",
                                        "icon", "famfamfam-flag-cn",
                                        "isDisabled", false
                                )
                        ))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText())
                .isEqualTo("This language already exists!");
    }

    @Test
    void createLanguageFromTenantSideReturnsOriginalHostOnlyError() throws Exception {
        JsonNode response = postRawWithToken("/api/services/app/Language/CreateOrUpdateLanguage",
                tenantToken(2), Map.of(
                        "language", Map.of(
                                "name", "x-tenant-language-" + System.nanoTime(),
                                "displayName", "Tenant Language",
                                "icon", "famfamfam-flag-test",
                                "isDisabled", false
                        )
                ));

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText()).isEqualTo("Tenants cannot create language.");
    }

    @Test
    void createOrUpdateLanguageRejectsOriginalDtoValidationViolations() throws Exception {
        JsonNode missingLanguage = postRawWithToken("/api/services/app/Language/CreateOrUpdateLanguage",
                adminToken(), Map.of());

        assertThat(missingLanguage.path("success").asBoolean()).isFalse();
        assertThat(missingLanguage.path("error").path("message").asText()).isEqualTo("Validation failed");

        String topLevelName = "x-top-level-language-" + System.nanoTime();
        JsonNode missingLanguageWrapper = postRawWithToken("/api/services/app/Language/CreateOrUpdateLanguage",
                adminToken(), Map.of(
                        "name", topLevelName,
                        "displayName", "Top Level Language",
                        "icon", "famfamfam-flag-test",
                        "isDisabled", false
                ));

        assertValidationFailure(missingLanguageWrapper);
        assertThat(store.languages()).noneMatch(language -> topLevelName.equals(language.name));

        JsonNode longName = postRawWithToken("/api/services/app/Language/CreateOrUpdateLanguage",
                adminToken(), Map.of(
                        "language", Map.of(
                                "name", "l".repeat(129),
                                "displayName", "Language Name Limit",
                                "icon", "famfamfam-flag-test",
                                "isDisabled", false
                        )
                ));

        assertThat(longName.path("success").asBoolean()).isFalse();
        assertThat(longName.path("error").path("message").asText()).isEqualTo("Validation failed");

        JsonNode longIcon = postRawWithToken("/api/services/app/Language/CreateOrUpdateLanguage",
                adminToken(), Map.of(
                        "language", Map.of(
                                "name", "x-icon-limit-" + System.nanoTime(),
                                "displayName", "Language Icon Limit",
                                "icon", "i".repeat(129),
                                "isDisabled", false
                        )
                ));

        assertThat(longIcon.path("success").asBoolean()).isFalse();
        assertThat(longIcon.path("error").path("message").asText()).isEqualTo("Validation failed");
    }

    @Test
    void updateLanguageTextKeepsOriginalVoidResponseWhileSavingText() throws Exception {
        String key = "LanguageVoidText." + System.nanoTime();
        JsonNode response = postAbp("/api/services/app/Language/UpdateLanguageText", Map.of(
                "languageName", "zh-Hans",
                "sourceName", "CapabilityTable",
                "key", key,
                "baseValue", "Language text base",
                "targetValue", "语言文本已更新"
        ));

        assertThat(response.path("result").isNull()).isTrue();
        assertThat(store.languageTexts("zh-Hans", key, 0, 10).items).anySatisfy(text -> {
            assertThat(text.key).isEqualTo(key);
            assertThat(text.targetValue).isEqualTo("语言文本已更新");
        });
    }

    @Test
    void updateLanguageTextRejectsOriginalDtoValidationViolations() throws Exception {
        JsonNode missingSource = postRawWithToken("/api/services/app/Language/UpdateLanguageText",
                adminToken(), Map.of(
                        "languageName", "zh-Hans",
                        "key", "MissingSource",
                        "value", "Missing source should fail"
                ));

        assertThat(missingSource.path("success").asBoolean()).isFalse();
        assertThat(missingSource.path("error").path("message").asText()).isEqualTo("Validation failed");

        JsonNode longLanguage = postRawWithToken("/api/services/app/Language/UpdateLanguageText",
                adminToken(), Map.of(
                        "languageName", "l".repeat(129),
                        "sourceName", "CapabilityTable",
                        "key", "LongLanguage",
                        "value", "Long language should fail"
                ));

        assertThat(longLanguage.path("success").asBoolean()).isFalse();
        assertThat(longLanguage.path("error").path("message").asText()).isEqualTo("Validation failed");

        JsonNode longKey = postRawWithToken("/api/services/app/Language/UpdateLanguageText",
                adminToken(), Map.of(
                        "languageName", "zh-Hans",
                        "sourceName", "CapabilityTable",
                        "key", "k".repeat(257),
                        "value", "Long key should fail"
                ));

        assertThat(longKey.path("success").asBoolean()).isFalse();
        assertThat(longKey.path("error").path("message").asText()).isEqualTo("Validation failed");

        JsonNode missingValue = postRawWithToken("/api/services/app/Language/UpdateLanguageText",
                adminToken(), Map.of(
                        "languageName", "zh-Hans",
                        "sourceName", "CapabilityTable",
                        "key", "MissingValue"
                ));

        assertThat(missingValue.path("success").asBoolean()).isFalse();
        assertThat(missingValue.path("error").path("message").asText()).isEqualTo("Validation failed");
    }

    @Test
    void setDefaultLanguageRejectsOriginalDtoValidationViolations() throws Exception {
        JsonNode missingName = postRawWithToken("/api/services/app/Language/SetDefaultLanguage",
                adminToken(), Map.of());

        assertThat(missingName.path("success").asBoolean()).isFalse();
        assertThat(missingName.path("error").path("message").asText()).isEqualTo("Validation failed");

        JsonNode longName = postRawWithToken("/api/services/app/Language/SetDefaultLanguage",
                adminToken(), Map.of("name", "l".repeat(129)));

        assertThat(longName.path("success").asBoolean()).isFalse();
        assertThat(longName.path("error").path("message").asText()).isEqualTo("Validation failed");
    }

    @Test
    void getLanguageForEditReturnsOriginalLanguageNameAndFlagComboboxes() throws Exception {
        LanguageItem language = store.languages().stream()
                .filter(item -> "zh-Hans".equals(item.name))
                .findFirst()
                .orElseThrow();

        JsonNode edit = getRawWithToken(get("/api/services/app/Language/GetLanguageForEdit")
                .param("Id", language.id.toString()), adminToken());

        JsonNode result = edit.path("result");
        assertThat(edit.path("success").asBoolean()).isTrue();
        assertThat(result.path("language").path("name").asText()).isEqualTo("zh-Hans");
        assertThat(result.path("language").path("icon").asText()).isEqualTo("famfamfam-flags cn");
        assertThat(result.path("languageNames")).anySatisfy(item -> {
            assertThat(item.path("value").asText()).isEqualTo("zh-Hans");
            assertThat(item.path("displayText").asText()).isEqualTo("Chinese (Simplified) (zh-Hans)");
            assertThat(item.path("isSelected").asBoolean()).isTrue();
        });
        assertThat(result.path("flags")).anySatisfy(item -> {
            assertThat(item.path("value").asText()).isEqualTo("famfamfam-flags cn");
            assertThat(item.path("displayText").asText()).isEqualTo("cn");
            assertThat(item.path("isSelected").asBoolean()).isTrue();
        });

        JsonNode create = getRawWithToken(get("/api/services/app/Language/GetLanguageForEdit"), adminToken());
        assertThat(create.path("result").path("language").path("name").isNull()).isTrue();
        assertThat(create.path("result").path("languageNames")).isNotEmpty();
        assertThat(create.path("result").path("flags").get(0).path("value").asText()).isEqualTo("famfamfam-flags ad");
    }

    @Test
    void createOrUpdateLanguageUsesOriginalIsEnabledAndCultureDisplayName() throws Exception {
        String languageName = "fr";

        JsonNode response = postAbp("/api/services/app/Language/CreateOrUpdateLanguage", Map.of(
                "language", Map.of(
                        "name", languageName,
                        "displayName", "Submitted Display Name Should Be Ignored",
                        "icon", "famfamfam-flags fr",
                        "isEnabled", false
                )
        ));

        assertThat(response.path("result").isNull()).isTrue();
        assertThat(store.languages()).anySatisfy(language -> {
            assertThat(language.name).isEqualTo(languageName);
            assertThat(language.displayName).isEqualTo("French");
            assertThat(language.icon).isEqualTo("famfamfam-flags fr");
            assertThat(language.isDisabled).isTrue();
        });

        JsonNode edit = getRawWithToken(get("/api/services/app/Language/GetLanguageForEdit")
                .param("Id", store.languages().stream()
                        .filter(language -> languageName.equals(language.name))
                        .findFirst()
                        .orElseThrow()
                        .id
                        .toString()), adminToken());
        assertThat(edit.path("result").path("language").path("isEnabled").asBoolean()).isFalse();
    }

    @Test
    void getLanguageTextsRejectsOriginalDtoValidationViolations() throws Exception {
        JsonNode missingSource = getRawWithToken(get("/api/services/app/Language/GetLanguageTexts")
                .param("TargetLanguageName", "zh-Hans"), adminToken());

        assertValidationFailure(missingSource);

        JsonNode shortTargetLanguage = getRawWithToken(get("/api/services/app/Language/GetLanguageTexts")
                .param("SourceName", "CapabilityTable")
                .param("TargetLanguageName", "z"), adminToken());

        assertValidationFailure(shortTargetLanguage);

        JsonNode negativePaging = getRawWithToken(get("/api/services/app/Language/GetLanguageTexts")
                .param("SourceName", "CapabilityTable")
                .param("TargetLanguageName", "zh-Hans")
                .param("SkipCount", "-1"), adminToken());

        assertValidationFailure(negativePaging);

        JsonNode postLongBaseLanguage = postRawWithToken("/api/services/app/Language/GetLanguageTexts",
                adminToken(), Map.of(
                        "sourceName", "CapabilityTable",
                        "baseLanguageName", "b".repeat(129),
                        "targetLanguageName", "zh-Hans"
                ));

        assertValidationFailure(postLongBaseLanguage);
    }

    private JsonNode postAbp(String url, Object payload) throws Exception {
        String body = mockMvc.perform(post(url)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(payload)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isTrue();
        return response;
    }

    private JsonNode postRawWithToken(String url, String token, Object payload) throws Exception {
        String body = mockMvc.perform(post(url)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(payload)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    private JsonNode getRawWithToken(MockHttpServletRequestBuilder request, String token) throws Exception {
        String body = mockMvc.perform(request.header("Authorization", "Bearer " + token))
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

    private String tenantToken(Integer tenantId) throws Exception {
        String impersonationToken = authService.createImpersonationToken(1L, tenantId);
        String body = mockMvc.perform(get("/api/TokenAuth/ImpersonatedAuthenticate")
                        .param("impersonationToken", impersonationToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isTrue();
        return response.path("result").path("accessToken").asText();
    }

    private String adminToken() {
        return authService.authenticate("admin", "123qwe").orElseThrow().token();
    }
}
