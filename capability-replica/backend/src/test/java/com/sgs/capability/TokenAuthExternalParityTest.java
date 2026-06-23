package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.model.SystemSettingsItem;
import com.sgs.capability.model.UserDelegation;
import com.sgs.capability.model.UserItem;
import com.sgs.capability.security.AuthService;
import com.sgs.capability.service.CapabilityStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/token-auth-external-parity-store.json")
@AutoConfigureMockMvc
class TokenAuthExternalParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/token-auth-external-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset token auth external parity test store", ex);
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
    void authenticateAcceptsOriginalBase64EncodedPassword() throws Exception {
        JsonNode response = postAbp("/api/TokenAuth/Authenticate", Map.of(
                "userNameOrEmailAddress", "admin",
                "password", base64("123qwe")
        ));

        JsonNode result = response.path("result");
        assertThat(result.path("accessToken").asText()).contains(".");
        assertThat(result.path("encryptedAccessToken").asText()).isNotBlank();
        assertThat(result.path("refreshToken").asText()).startsWith("refresh-");
    }

    @Test
    void authenticateRejectsOriginalDtoLengthViolations() throws Exception {
        JsonNode longLoginName = postRaw("/api/TokenAuth/Authenticate", Map.of(
                "userNameOrEmailAddress", "a".repeat(257),
                "password", base64("123qwe")
        ));
        assertValidationFailed(longLoginName);

        JsonNode longPasswordPayload = postRaw("/api/TokenAuth/Authenticate", Map.of(
                "userNameOrEmailAddress", "admin",
                "password", base64("a".repeat(25))
        ));
        assertValidationFailed(longPasswordPayload);
    }

    @Test
    void authenticateDoesNotDefaultBlankUserNameToAdmin() throws Exception {
        JsonNode response = postRaw("/api/TokenAuth/Authenticate", Map.of(
                "password", base64("123qwe")
        ));

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("result").isNull()).isTrue();
    }

    @Test
    void authenticateDoesNotDefaultBlankPasswordToLocalDemoPassword() throws Exception {
        JsonNode response = postRaw("/api/TokenAuth/Authenticate", Map.of(
                "userNameOrEmailAddress", "admin",
                "password", ""
        ));

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("result").isNull()).isTrue();
    }

    @Test
    void authenticateDoesNotAcceptPlainTextPasswordPayload() throws Exception {
        JsonNode response = postRaw("/api/TokenAuth/Authenticate", Map.of(
                "userNameOrEmailAddress", "admin",
                "password", "123qwe"
        ));

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("result").isNull()).isTrue();
    }

    @Test
    void authenticateReturnsOriginalPasswordResetChallengeWhenUserMustChangePassword() throws Exception {
        UserItem admin = store.user(1L).orElseThrow();
        boolean originalShouldChangePassword = admin.shouldChangePasswordOnNextLogin;
        String originalResetCode = admin.passwordResetCode;
        admin.shouldChangePasswordOnNextLogin = true;
        admin.passwordResetCode = null;
        try {
            JsonNode response = postAbp("/api/TokenAuth/Authenticate", Map.of(
                    "userNameOrEmailAddress", "admin",
                    "password", base64("123qwe"),
                    "returnUrl", "/account/password-reset"
            ));

            JsonNode result = response.path("result");
            assertThat(result.path("shouldResetPassword").asBoolean()).isTrue();
            assertThat(result.path("passwordResetCode").asText()).isNotBlank();
            assertThat(result.path("userId").asLong()).isEqualTo(1L);
            assertThat(result.path("returnUrl").asText()).isEqualTo("/account/password-reset");
            assertThat(result.path("accessToken").isNull()).isTrue();
        } finally {
            admin.shouldChangePasswordOnNextLogin = originalShouldChangePassword;
            admin.passwordResetCode = originalResetCode;
        }
    }

    @Test
    void authenticateAddsOriginalSingleSignInParametersToReturnUrl() throws Exception {
        JsonNode response = postAbp("/api/TokenAuth/Authenticate", Map.of(
                "userNameOrEmailAddress", "admin",
                "password", base64("123qwe"),
                "returnUrl", "/account/security?tab=sessions",
                "singleSignIn", true
        ));

        String returnUrl = response.path("result").path("returnUrl").asText();
        assertThat(returnUrl).startsWith("/account/security?tab=sessions&accessToken=");
        assertThat(returnUrl).contains("&userId=MQ==");
        assertThat(returnUrl).contains("&tenantId=MQ==");
        assertThat(returnUrl).doesNotContain("signInToken=");
    }

    @Test
    void externalProviderEndpointDoesNotInventLocalProvidersWhenSettingsAreBlank() throws Exception {
        SystemSettingsItem.ExternalLoginProviderSettings original = replaceExternalLoginSettings(blankExternalLoginSettings());
        try {
            JsonNode response = getAbp("/api/TokenAuth/GetExternalAuthenticationProviders");
            JsonNode providers = response.path("result");

            assertThat(providers).isEmpty();
        } finally {
            replaceExternalLoginSettings(original);
        }
    }

    @Test
    void externalProviderEndpointReturnsOnlyValidConfiguredProviders() throws Exception {
        SystemSettingsItem.ExternalLoginProviderSettings original = replaceExternalLoginSettings(googleExternalLoginSettings());
        try {
            JsonNode response = getAbp("/api/TokenAuth/GetExternalAuthenticationProviders");
            JsonNode providers = response.path("result");

            assertThat(providers).hasSize(1);
            JsonNode google = providers.get(0);
            assertThat(google.path("name").asText()).isEqualTo("Google");
            assertThat(google.path("clientId").asText()).isEqualTo("configured-google-client");
            assertThat(google.path("additionalParams").path("UserInfoEndpoint").asText()).isEqualTo("https://example.local/userinfo");
        } finally {
            replaceExternalLoginSettings(original);
        }
    }

    @Test
    void externalAuthenticateReturnsAccessAndRefreshTokens() throws Exception {
        SystemSettingsItem.ExternalLoginProviderSettings original = replaceExternalLoginSettings(googleExternalLoginSettings());
        try {
            JsonNode response = postAbp("/api/TokenAuth/ExternalAuthenticate", Map.of(
                    "authProvider", "Google",
                    "providerKey", "admin@example.local",
                    "providerAccessCode", "local-google-code",
                    "returnUrl", "/account/security",
                    "singleSignIn", true
            ));

            JsonNode result = response.path("result");
            assertThat(result.path("accessToken").asText()).contains(".");
            assertThat(result.path("encryptedAccessToken").asText()).isNotBlank();
            assertThat(result.path("refreshToken").asText()).startsWith("refresh-");
            assertThat(result.path("expireInSeconds").asInt()).isGreaterThan(0);
            assertThat(result.path("refreshTokenExpireInSeconds").asInt()).isGreaterThan(0);
            assertThat(result.path("returnUrl").asText()).contains("/account/security");
            assertThat(result.path("returnUrl").asText()).contains("accessToken=");
            assertThat(result.path("returnUrl").asText()).doesNotContain("signInToken=");
        } finally {
            replaceExternalLoginSettings(original);
        }
    }

    @Test
    void externalAuthenticateRejectsOriginalDtoLengthViolations() throws Exception {
        SystemSettingsItem.ExternalLoginProviderSettings original = replaceExternalLoginSettings(googleExternalLoginSettings());
        try {
            JsonNode longProvider = postRaw("/api/TokenAuth/ExternalAuthenticate", Map.of(
                    "authProvider", "G".repeat(129),
                    "providerKey", "admin@example.local",
                    "providerAccessCode", "local-google-code"
            ));
            assertValidationFailed(longProvider);

            JsonNode longProviderKey = postRaw("/api/TokenAuth/ExternalAuthenticate", Map.of(
                    "authProvider", "Google",
                    "providerKey", "a".repeat(257) + "@example.local",
                    "providerAccessCode", "local-google-code"
            ));
            assertValidationFailed(longProviderKey);
        } finally {
            replaceExternalLoginSettings(original);
        }
    }

    @Test
    void sendTwoFactorAuthCodeRejectsOriginalDtoValidationFailures() throws Exception {
        JsonNode invalidUserId = postRaw("/api/TokenAuth/SendTwoFactorAuthCode", Map.of(
                "userId", 0,
                "provider", "Email"
        ));
        assertValidationFailed(invalidUserId);

        String body = mockMvc.perform(post("/api/TokenAuth/SendTwoFactorAuthCode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertValidationFailed(objectMapper.readTree(body));
    }

    @Test
    void sendTwoFactorAuthCodeKeepsOriginalVoidResponse() throws Exception {
        UserItem admin = store.user(1L).orElseThrow();
        boolean originalTwoFactorEnabled = admin.isTwoFactorEnabled;
        admin.isTwoFactorEnabled = true;
        try {
            JsonNode challenge = postAbp("/api/TokenAuth/Authenticate", Map.of(
                    "userNameOrEmailAddress", "admin",
                    "password", base64("123qwe")
            )).path("result");
            assertThat(challenge.path("requiresTwoFactorVerification").asBoolean()).isTrue();

            JsonNode response = postAbp("/api/TokenAuth/SendTwoFactorAuthCode", Map.of(
                    "userId", 1,
                    "provider", "Email"
            ));

            assertThat(response.path("result").isNull()).isTrue();
        } finally {
            admin.isTwoFactorEnabled = originalTwoFactorEnabled;
        }
    }

    @Test
    void sendTwoFactorAuthCodeRejectsMissingOriginalPendingChallenge() throws Exception {
        String body = mockMvc.perform(post("/api/TokenAuth/SendTwoFactorAuthCode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "userId", 2,
                                "provider", "Email"
                        ))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText()).isEqualTo("SendSecurityCodeErrorMessage");
    }

    @Test
    void authenticateRejectsInvalidTwoFactorCodeWithOriginalSecurityCodeError() throws Exception {
        UserItem admin = store.user(1L).orElseThrow();
        boolean originalTwoFactorEnabled = admin.isTwoFactorEnabled;
        admin.isTwoFactorEnabled = true;
        try {
            JsonNode challenge = postAbp("/api/TokenAuth/Authenticate", Map.of(
                    "userNameOrEmailAddress", "admin",
                    "password", base64("123qwe")
            )).path("result");
            assertThat(challenge.path("requiresTwoFactorVerification").asBoolean()).isTrue();

            postAbp("/api/TokenAuth/SendTwoFactorAuthCode", Map.of(
                    "userId", 1,
                    "provider", "Email"
            ));

            String body = mockMvc.perform(post("/api/TokenAuth/Authenticate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsBytes(Map.of(
                                    "userNameOrEmailAddress", "admin",
                                    "password", base64("123qwe"),
                                    "twoFactorVerificationCode", "000000"
                            ))))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            JsonNode response = objectMapper.readTree(body);
            assertThat(response.path("success").asBoolean()).isFalse();
            assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
            assertThat(response.path("error").path("message").asText()).isEqualTo("InvalidSecurityCode");
        } finally {
            admin.isTwoFactorEnabled = originalTwoFactorEnabled;
        }
    }

    @Test
    void twoFactorRememberClientTokenIsReturnedOnlyAfterOriginalCodeVerification() throws Exception {
        UserItem admin = store.user(1L).orElseThrow();
        boolean originalTwoFactorEnabled = admin.isTwoFactorEnabled;
        admin.isTwoFactorEnabled = true;
        try {
            JsonNode challenge = postAbp("/api/TokenAuth/Authenticate", Map.of(
                    "userNameOrEmailAddress", "admin",
                    "password", base64("123qwe"),
                    "rememberClient", true
            )).path("result");
            assertThat(challenge.path("requiresTwoFactorVerification").asBoolean()).isTrue();
            assertThat(challenge.path("twoFactorRememberClientToken").isNull()).isTrue();

            postAbp("/api/TokenAuth/SendTwoFactorAuthCode", Map.of(
                    "userId", 1,
                    "provider", "Email"
            ));

            JsonNode verified = postAbp("/api/TokenAuth/Authenticate", Map.of(
                    "userNameOrEmailAddress", "admin",
                    "password", base64("123qwe"),
                    "twoFactorVerificationCode", generatedTwoFactorCode(1L),
                    "rememberClient", true
            )).path("result");

            assertThat(verified.path("accessToken").asText()).contains(".");
            assertThat(verified.path("twoFactorRememberClientToken").asText()).isNotBlank();
        } finally {
            admin.isTwoFactorEnabled = originalTwoFactorEnabled;
        }
    }

    @Test
    void delegatedImpersonatedAuthenticateKeepsOriginalPostRoute() throws Exception {
        UserDelegation delegation = store.userDelegations().stream().findFirst().orElseThrow();
        String token = authService.createImpersonationToken(delegation.sourceUserId, null);

        String body = mockMvc.perform(post("/api/TokenAuth/DelegatedImpersonatedAuthenticate")
                        .param("userDelegationId", String.valueOf(delegation.id))
                        .param("impersonationToken", token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isTrue();
        JsonNode result = response.path("result");
        assertThat(result.path("accessToken").asText()).contains(".");
        assertThat(result.path("encryptedAccessToken").asText()).isNotBlank();
        assertThat(result.path("expireInSeconds").asInt()).isGreaterThan(0);
    }

    @Test
    void impersonatedAndLinkedAccountAuthenticateKeepOriginalPostQueryRoutes() throws Exception {
        String impersonationToken = authService.createImpersonationToken(1L, 1);

        JsonNode impersonated = postQueryAbp("/api/TokenAuth/ImpersonatedAuthenticate", Map.of(
                "impersonationToken", impersonationToken
        )).path("result");

        assertThat(impersonated.path("accessToken").asText()).contains(".");
        assertThat(impersonated.path("encryptedAccessToken").asText()).isNotBlank();
        assertThat(impersonated.path("expireInSeconds").asInt()).isGreaterThan(0);

        String switchAccountToken = authService.createSwitchAccountToken(1L, 1);
        JsonNode linked = postQueryAbp("/api/TokenAuth/LinkedAccountAuthenticate", Map.of(
                "switchAccountToken", switchAccountToken
        )).path("result");

        assertThat(linked.path("accessToken").asText()).contains(".");
        assertThat(linked.path("encryptedAccessToken").asText()).isNotBlank();
        assertThat(linked.path("expireInSeconds").asInt()).isGreaterThan(0);
    }

    @Test
    void logoutKeepsOriginalGetRoute() throws Exception {
        String token = authService.authenticate("admin", "123qwe").orElseThrow().token();

        JsonNode response = getAbpWithToken("/api/TokenAuth/LogOut", token);

        assertThat(response.path("result").isNull()).isTrue();
    }

    private SystemSettingsItem.ExternalLoginProviderSettings replaceExternalLoginSettings(
            SystemSettingsItem.ExternalLoginProviderSettings next) {
        SystemSettingsItem.HostSettings settings = store.hostSettings();
        SystemSettingsItem.ExternalLoginProviderSettings previous = settings.externalLoginProviderSettings;
        settings.externalLoginProviderSettings = next;
        store.updateHostSettings(settings);
        return previous;
    }

    private SystemSettingsItem.ExternalLoginProviderSettings blankExternalLoginSettings() {
        return new SystemSettingsItem.ExternalLoginProviderSettings();
    }

    private SystemSettingsItem.ExternalLoginProviderSettings googleExternalLoginSettings() {
        SystemSettingsItem.ExternalLoginProviderSettings settings = blankExternalLoginSettings();
        settings.google.clientId = "configured-google-client";
        settings.google.clientSecret = "configured-google-secret";
        settings.google.userInfoEndpoint = "https://example.local/userinfo";
        return settings;
    }

    private JsonNode getAbp(String url) throws Exception {
        String body = mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isTrue();
        return response;
    }

    private String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private JsonNode getAbpWithToken(String url, String token) throws Exception {
        String body = mockMvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isTrue();
        return response;
    }

    private JsonNode postAbp(String url, Object payload) throws Exception {
        String body = mockMvc.perform(post(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(payload)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isTrue();
        return response;
    }

    private JsonNode postRaw(String url, Object payload) throws Exception {
        String body = mockMvc.perform(post(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(payload)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body);
    }

    private void assertValidationFailed(JsonNode response) {
        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText()).isEqualTo("Validation failed");
    }

    private JsonNode postQueryAbp(String url, Map<String, String> params) throws Exception {
        var request = post(url).contentType(MediaType.APPLICATION_JSON);
        params.forEach(request::param);
        String body = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isTrue();
        return response;
    }

    @SuppressWarnings("unchecked")
    private String generatedTwoFactorCode(Long userId) throws Exception {
        Field field = AuthService.class.getDeclaredField("twoFactorCodes");
        field.setAccessible(true);
        ConcurrentMap<Long, String> codes = (ConcurrentMap<Long, String>) field.get(authService);
        return codes.get(userId);
    }
}
