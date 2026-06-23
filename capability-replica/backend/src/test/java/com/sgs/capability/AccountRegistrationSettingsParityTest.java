package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.model.SystemSettingsItem;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/account-registration-settings-parity-store.json")
@AutoConfigureMockMvc
class AccountRegistrationSettingsParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/account-registration-settings-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset account registration settings parity test store", ex);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    CapabilityStore store;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void tenantUserRegistrationDefaultsMatchOriginalSettingProvider() {
        SystemSettingsItem.TenantSettings settings = resetTenantSettings();

        assertThat(settings.userManagement.allowSelfRegistration).isTrue();
        assertThat(settings.userManagement.isNewRegisteredUserActiveByDefault).isFalse();
        assertThat(settings.userManagement.useCaptchaOnRegistration).isTrue();
    }

    @Test
    void registerRejectsEmptyCaptchaWhenOriginalRegistrationCaptchaSettingIsEnabled() throws Exception {
        resetTenantSettings();
        String userName = "captchamissing" + System.nanoTime();

        JsonNode response = registerAccount(userName, userName + "@example.local");

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText()).isEqualTo("CaptchaCanNotBeEmpty");
        assertThat(store.userByUserNameOrEmail(userName)).isEmpty();
    }

    @Test
    void registerUsesOriginalActiveAndEmailConfirmationSettingsForCanLogin() throws Exception {
        SystemSettingsItem.TenantSettings settings = resetTenantSettings();
        settings.userManagement.allowSelfRegistration = true;
        settings.userManagement.isNewRegisteredUserActiveByDefault = false;
        settings.userManagement.isEmailConfirmationRequiredForLogin = true;
        settings.userManagement.useCaptchaOnRegistration = false;
        store.updateTenantSettings(settings);
        String userName = "inactiveuser" + System.nanoTime();

        JsonNode response = registerAccount(userName, userName + "@example.local");

        assertThat(response.path("success").asBoolean()).isTrue();
        assertThat(response.path("result").path("canLogin").asBoolean()).isFalse();
        assertThat(store.userByUserNameOrEmail(userName)).hasValueSatisfying(user -> {
            assertThat(user.isActive).isFalse();
            assertThat(user.isEmailConfirmed).isFalse();
        });
    }

    @Test
    void registerRejectsMissingEmailInsteadOfCreatingIncompleteUser() throws Exception {
        SystemSettingsItem.TenantSettings settings = resetTenantSettings();
        settings.userManagement.useCaptchaOnRegistration = false;
        store.updateTenantSettings(settings);
        String userName = "missingemailuser" + System.nanoTime();

        JsonNode response = registerAccount(Map.of(
                "name", "Register",
                "surname", "User",
                "userName", userName,
                "password", "123qwe"
        ));

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(store.userByUserNameOrEmail(userName)).isEmpty();
    }

    @Test
    void registerRejectsPasswordLongerThanOriginalRegisterInputLimit() throws Exception {
        SystemSettingsItem.TenantSettings settings = resetTenantSettings();
        settings.userManagement.useCaptchaOnRegistration = false;
        store.updateTenantSettings(settings);
        String userName = "longpassworduser" + System.nanoTime();

        JsonNode response = registerAccount(Map.of(
                "name", "Register",
                "surname", "User",
                "userName", userName,
                "emailAddress", userName + "@example.local",
                "password", "a".repeat(33)
        ));

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(store.userByUserNameOrEmail(userName)).isEmpty();
    }

    @Test
    void registerIsRejectedWhenOriginalTenantSelfRegistrationSettingIsDisabled() throws Exception {
        SystemSettingsItem.TenantSettings settings = resetTenantSettings();
        settings.userManagement.allowSelfRegistration = false;
        settings.userManagement.useCaptchaOnRegistration = false;
        store.updateTenantSettings(settings);
        String userName = "disableduser" + System.nanoTime();

        JsonNode response = registerAccount(userName, userName + "@example.local");

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(store.userByUserNameOrEmail(userName)).isEmpty();
    }

    private SystemSettingsItem.TenantSettings resetTenantSettings() {
        return store.updateTenantSettings(SystemSettingsItem.defaultTenantSettings());
    }

    private JsonNode registerAccount(String userName, String emailAddress) throws Exception {
        return registerAccount(Map.of(
                "name", "Register",
                "surname", "User",
                "userName", userName,
                "emailAddress", emailAddress,
                "password", "123qwe"
        ));
    }

    private JsonNode registerAccount(Object payload) throws Exception {
        String body = mockMvc.perform(post("/api/services/app/Account/Register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(payload)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }
}
