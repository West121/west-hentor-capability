package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.model.SystemSettingsItem;
import com.sgs.capability.security.AuthService;
import com.sgs.capability.service.CapabilityStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/profile-parity-store.json")
@AutoConfigureMockMvc
class ProfileParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/profile-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset profile parity test store", ex);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthService authService;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    CapabilityStore store;

    @Test
    void updateCurrentUserProfileKeepsOriginalVoidResponseWhileSavingProfile() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        Map<String, Object> input = Map.of(
                "name", "Profile" + suffix,
                "surname", "Parity",
                "userName", "admin",
                "emailAddress", "profile-" + suffix + "@example.local",
                "phoneNumber", "139" + suffix.substring(Math.max(0, suffix.length() - 8)),
                "engName", "Profile Parity"
        );

        JsonNode response = postAbp("/api/services/app/Profile/UpdateCurrentUserProfile", input);

        assertThat(response.path("result").isNull()).isTrue();
        JsonNode profile = getAbp("/api/services/app/Profile/GetCurrentUserProfileForEdit").path("result");
        assertThat(profile.path("name").asText()).isEqualTo(input.get("name"));
        assertThat(profile.path("surname").asText()).isEqualTo("Parity");
        assertThat(profile.path("emailAddress").asText()).isEqualTo(input.get("emailAddress"));
        assertThat(profile.path("phoneNumber").asText()).isEqualTo(input.get("phoneNumber"));
        assertThat(profile.path("engName").asText()).isEqualTo("Profile Parity");
    }

    @Test
    void updateCurrentUserProfileRejectsMissingSurnameLikeOriginalDtoValidation() throws Exception {
        JsonNode before = getAbp("/api/services/app/Profile/GetCurrentUserProfileForEdit").path("result");
        JsonNode response = postRaw("/api/services/app/Profile/UpdateCurrentUserProfile", Map.of(
                "name", "MissingSurname",
                "userName", before.path("userName").asText(),
                "emailAddress", "missing-surname-" + System.nanoTime() + "@example.local",
                "phoneNumber", before.path("phoneNumber").asText(),
                "engName", before.path("engName").asText()
        ));

        assertThat(response.path("success").asBoolean()).isFalse();
        JsonNode after = getAbp("/api/services/app/Profile/GetCurrentUserProfileForEdit").path("result");
        assertThat(after.path("surname").asText()).isEqualTo(before.path("surname").asText());
    }

    @Test
    void updateCurrentUserProfileRejectsOriginalDtoRequiredAndLengthViolations() throws Exception {
        JsonNode before = getAbp("/api/services/app/Profile/GetCurrentUserProfileForEdit").path("result");
        java.util.Map<String, Object> invalidFields = new java.util.LinkedHashMap<>();
        invalidFields.put("name", "");
        invalidFields.put("userName", "");
        invalidFields.put("emailAddress", "");
        invalidFields.put("nameTooLong", "n".repeat(65));
        invalidFields.put("surnameTooLong", "s".repeat(65));
        invalidFields.put("userNameTooLong", "u".repeat(257));
        invalidFields.put("emailAddressTooLong", "e".repeat(245) + "@example.local");
        invalidFields.put("phoneNumberTooLong", "1".repeat(25));

        for (java.util.Map.Entry<String, Object> invalid : invalidFields.entrySet()) {
            java.util.Map<String, Object> input = new java.util.LinkedHashMap<>();
            input.put("name", "Profile");
            input.put("surname", "Parity");
            input.put("userName", before.path("userName").asText());
            input.put("emailAddress", "profile-valid-" + System.nanoTime() + "@example.local");
            input.put("phoneNumber", before.path("phoneNumber").asText());
            input.put("engName", before.path("engName").asText());
            input.put(invalid.getKey().replace("TooLong", ""), invalid.getValue());

            JsonNode response = postRaw("/api/services/app/Profile/UpdateCurrentUserProfile", input);

            assertThat(response.path("success").asBoolean())
                    .as("field %s should fail validation", invalid.getKey())
                    .isFalse();
        }
    }

    @Test
    void verifySmsCodeRejectsWrongCodeWithOriginalMessage() throws Exception {
        String phoneNumber = "139" + String.valueOf(System.nanoTime()).substring(4, 12);
        postAbp("/api/services/app/Profile/SendVerificationSms", Map.of("phoneNumber", phoneNumber));

        JsonNode response = postRaw("/api/services/app/Profile/VerifySmsCode", Map.of(
                "phoneNumber", phoneNumber,
                "code", "000000"
        ));

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText()).isEqualTo("Wrong verification code!");
    }

    @Test
    void sendVerificationSmsStoresRandomSixDigitCodeLikeOriginalRandomHelper() throws Exception {
        Set<String> generatedCodes = new HashSet<>();

        for (int index = 0; index < 5; index++) {
            String phoneNumber = "139" + String.valueOf(System.nanoTime()).substring(4, 12);
            postAbp("/api/services/app/Profile/SendVerificationSms", Map.of("phoneNumber", phoneNumber));
            String code = store.user(1L).orElseThrow().lastSmsVerificationCode;

            assertThat(code).matches("[1-9][0-9]{5}");
            generatedCodes.add(code);
        }

        assertThat(generatedCodes).hasSizeGreaterThan(1);
    }

    @Test
    void smsVerificationDefersPhoneChangeUntilCodeIsVerifiedLikeOriginalService() throws Exception {
        JsonNode before = getAbp("/api/services/app/Profile/GetCurrentUserProfileForEdit").path("result");
        String originalPhoneNumber = before.path("phoneNumber").asText();
        boolean originalConfirmed = before.path("isPhoneNumberConfirmed").asBoolean();
        String newPhoneNumber = "139" + String.valueOf(System.nanoTime()).substring(4, 12);

        postAbp("/api/services/app/Profile/SendVerificationSms", Map.of("phoneNumber", newPhoneNumber));
        String code = store.user(1L).orElseThrow().lastSmsVerificationCode;

        JsonNode afterSend = getAbp("/api/services/app/Profile/GetCurrentUserProfileForEdit").path("result");
        assertThat(afterSend.path("phoneNumber").asText()).isEqualTo(originalPhoneNumber);
        assertThat(afterSend.path("isPhoneNumberConfirmed").asBoolean()).isEqualTo(originalConfirmed);

        JsonNode verifyResponse = postAbp("/api/services/app/Profile/VerifySmsCode", Map.of(
                "phoneNumber", newPhoneNumber,
                "code", code
        ));
        assertThat(verifyResponse.path("result").isNull()).isTrue();

        JsonNode afterVerify = getAbp("/api/services/app/Profile/GetCurrentUserProfileForEdit").path("result");
        assertThat(afterVerify.path("phoneNumber").asText()).isEqualTo(newPhoneNumber);
        assertThat(afterVerify.path("isPhoneNumberConfirmed").asBoolean()).isTrue();
    }

    @Test
    void changePasswordRejectsWrongCurrentPasswordWithOriginalMessage() throws Exception {
        JsonNode response = postRaw("/api/services/app/Profile/ChangePassword", Map.of(
                "currentPassword", "wrong-current-password",
                "newPassword", "123qwe"
        ));

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText()).isEqualTo("Incorrect password.");
    }

    @Test
    void changePasswordRejectsOriginalRequiredFieldViolations() throws Exception {
        assertValidationFailure(postRaw("/api/services/app/Profile/ChangePassword", Map.of()));

        assertValidationFailure(postRaw("/api/services/app/Profile/ChangePassword", Map.of(
                "currentPassword", "123qwe"
        )));

        assertValidationFailure(postRaw("/api/services/app/Profile/ChangePassword", Map.of(
                "newPassword", "123qwe"
        )));
    }

    @Test
    void changePasswordRejectsNewPasswordBelowTenantRequiredLengthLikeOriginalUserManager() throws Exception {
        SystemSettingsItem.TenantSettings settings = SystemSettingsItem.defaultTenantSettings();
        settings.security.useDefaultPasswordComplexitySettings = false;
        settings.security.passwordComplexity.requireDigit = false;
        settings.security.passwordComplexity.requireLowercase = false;
        settings.security.passwordComplexity.requireNonAlphanumeric = false;
        settings.security.passwordComplexity.requireUppercase = false;
        settings.security.passwordComplexity.requiredLength = 9;
        putAbp("/api/services/app/TenantSettings/UpdateAllSettings", settings);

        JsonNode response = postRaw("/api/services/app/Profile/ChangePassword", Map.of(
                "currentPassword", "123qwe",
                "newPassword", "abc123"
        ));

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(authService.authenticate("admin", "123qwe")).isPresent();
    }

    @Test
    void changePasswordRejectsMissingTenantRequiredCharacterCategoriesLikeOriginalUserManager() throws Exception {
        assertPasswordRejectedByComplexity(complexity(true, false, false, false, 6), "abcdef");
        assertPasswordRejectedByComplexity(complexity(false, true, false, false, 6), "ABC123");
        assertPasswordRejectedByComplexity(complexity(false, false, false, true, 6), "abc123");
        assertPasswordRejectedByComplexity(complexity(false, false, true, false, 6), "abc123");

        assertThat(authService.authenticate("admin", "123qwe")).isPresent();
    }

    @Test
    void passwordComplexitySettingReflectsTenantSecuritySettingsLikeOriginalService() throws Exception {
        SystemSettingsItem.TenantSettings settings = SystemSettingsItem.defaultTenantSettings();
        settings.security.useDefaultPasswordComplexitySettings = false;
        settings.security.passwordComplexity.requireDigit = false;
        settings.security.passwordComplexity.requireLowercase = true;
        settings.security.passwordComplexity.requireNonAlphanumeric = true;
        settings.security.passwordComplexity.requireUppercase = true;
        settings.security.passwordComplexity.requiredLength = 9;

        putAbp("/api/services/app/TenantSettings/UpdateAllSettings", settings);

        JsonNode complexity = getAbp("/api/services/app/Profile/GetPasswordComplexitySetting")
                .path("result")
                .path("setting");
        assertThat(complexity.path("requireDigit").asBoolean()).isFalse();
        assertThat(complexity.path("requireLowercase").asBoolean()).isTrue();
        assertThat(complexity.path("requireNonAlphanumeric").asBoolean()).isTrue();
        assertThat(complexity.path("requireUppercase").asBoolean()).isTrue();
        assertThat(complexity.path("requiredLength").asInt()).isEqualTo(9);
    }

    @Test
    void passwordComplexitySettingAllowsAnonymousLikeOriginalService() throws Exception {
        String body = mockMvc.perform(get("/api/services/app/Profile/GetPasswordComplexitySetting"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isTrue();
        assertThat(response.path("result").path("setting").has("requiredLength")).isTrue();
    }

    @Test
    void changeLanguageRejectsOriginalRequiredLanguageNameViolation() throws Exception {
        JsonNode missingLanguage = postRaw("/api/services/app/Profile/ChangeLanguage", Map.of());

        assertValidationFailure(missingLanguage);

        JsonNode blankLanguage = postRaw("/api/services/app/Profile/ChangeLanguage", Map.of(
                "languageName", " "
        ));

        assertValidationFailure(blankLanguage);
    }

    @Test
    void updateProfilePictureRejectsOriginalFileTokenValidationViolations() throws Exception {
        assertValidationFailure(postRaw("/api/services/app/Profile/UpdateProfilePicture", Map.of()));

        assertValidationFailure(postRaw("/api/services/app/Profile/UpdateProfilePicture", Map.of(
                "fileToken", " "
        )));

        assertValidationFailure(postRaw("/api/services/app/Profile/UpdateProfilePicture", Map.of(
                "fileToken", "t".repeat(401)
        )));
    }

    @Test
    void updateProfilePictureRejectsMissingFileTokenLikeOriginalService() throws Exception {
        String fileToken = "missing-profile-token-" + System.nanoTime();

        JsonNode response = postRaw("/api/services/app/Profile/UpdateProfilePicture", Map.of(
                "fileToken", fileToken,
                "x", 0,
                "y", 0,
                "width", 1,
                "height", 1
        ));

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText())
                .isEqualTo("There is no such image file with the token: " + fileToken);
    }

    @Test
    void updateGoogleAuthenticatorKeyUsesOriginalLowercaseGuidKeyShape() throws Exception {
        for (int i = 0; i < 12; i++) {
            JsonNode response = postAbp("/api/services/app/Profile/UpdateGoogleAuthenticatorKey", Map.of());

            String key = googleAuthenticatorKeyFromQr(response.path("result").path("qrCodeSetupImageUrl").asText());
            assertThat(key).matches("[0-9a-f]{10}");
        }
    }

    private JsonNode postAbp(String url, Object payload) throws Exception {
        JsonNode response = postRaw(url, payload);
        assertThat(response.path("success").asBoolean()).isTrue();
        return response;
    }

    private JsonNode putAbp(String url, Object payload) throws Exception {
        String body = mockMvc.perform(put(url)
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

    private void assertPasswordRejectedByComplexity(SystemSettingsItem.PasswordComplexitySetting complexity,
                                                    String newPassword) throws Exception {
        SystemSettingsItem.TenantSettings settings = SystemSettingsItem.defaultTenantSettings();
        settings.security.useDefaultPasswordComplexitySettings = false;
        settings.security.passwordComplexity = complexity;
        putAbp("/api/services/app/TenantSettings/UpdateAllSettings", settings);

        JsonNode response = postRaw("/api/services/app/Profile/ChangePassword", Map.of(
                "currentPassword", "123qwe",
                "newPassword", newPassword
        ));

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
    }

    private SystemSettingsItem.PasswordComplexitySetting complexity(boolean requireDigit, boolean requireLowercase,
                                                                    boolean requireNonAlphanumeric,
                                                                    boolean requireUppercase,
                                                                    int requiredLength) {
        SystemSettingsItem.PasswordComplexitySetting setting = new SystemSettingsItem.PasswordComplexitySetting();
        setting.requireDigit = requireDigit;
        setting.requireLowercase = requireLowercase;
        setting.requireNonAlphanumeric = requireNonAlphanumeric;
        setting.requireUppercase = requireUppercase;
        setting.requiredLength = requiredLength;
        return setting;
    }

    private JsonNode postRaw(String url, Object payload) throws Exception {
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

    private JsonNode getAbp(String url) throws Exception {
        String body = mockMvc.perform(get(url)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isTrue();
        return response;
    }

    private String googleAuthenticatorKeyFromQr(String dataUri) {
        String prefix = "data:image/svg+xml;base64,";
        assertThat(dataUri).startsWith(prefix);
        String svg = new String(Base64.getDecoder().decode(dataUri.substring(prefix.length())), StandardCharsets.UTF_8);
        int end = svg.lastIndexOf("</text>");
        int start = svg.lastIndexOf('>', end);
        return svg.substring(start + 1, end);
    }

    private String adminToken() {
        return authService.authenticate("admin", "123qwe").orElseThrow().token();
    }
}
