package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.model.EditionItem;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/tenant-registration-settings-parity-store.json")
@AutoConfigureMockMvc
class TenantRegistrationSettingsParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/tenant-registration-settings-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset tenant registration settings parity test store", ex);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    CapabilityStore store;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void hostTenantRegistrationDefaultsMatchOriginalSettingProvider() {
        SystemSettingsItem.HostSettings settings = resetHostSettings();

        assertThat(settings.tenantManagement.allowSelfRegistration).isTrue();
        assertThat(settings.tenantManagement.isNewRegisteredTenantActiveByDefault).isFalse();
        assertThat(settings.tenantManagement.useCaptchaOnRegistration).isTrue();
    }

    @Test
    void registrationRejectsEmptyCaptchaWhenOriginalTenantCaptchaSettingIsEnabled() throws Exception {
        resetHostSettings();
        EditionItem freeEdition = freeEdition("Captcha Tenant Edition " + System.nanoTime());
        String tenancyName = "captchatenant" + System.nanoTime();

        JsonNode response = registerTenant(Map.of(
                "tenancyName", tenancyName,
                "name", "Captcha Tenant",
                "adminEmailAddress", "captcha-tenant@example.local",
                "adminPassword", "123qwe",
                "editionId", freeEdition.id,
                "subscriptionStartType", 1
        ));

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText()).isEqualTo("CaptchaCanNotBeEmpty");
        assertThat(store.tenantByTenancyName(tenancyName)).isEmpty();
    }

    @Test
    void freeRegistrationUsesOriginalHostActivationAndEmailConfirmationSettings() throws Exception {
        SystemSettingsItem.HostSettings settings = resetHostSettings();
        settings.tenantManagement.allowSelfRegistration = true;
        settings.tenantManagement.isNewRegisteredTenantActiveByDefault = false;
        settings.tenantManagement.useCaptchaOnRegistration = false;
        settings.userManagement.isEmailConfirmationRequiredForLogin = true;
        store.updateHostSettings(settings);
        EditionItem freeEdition = freeEdition("Settings Free Edition " + System.nanoTime());
        String tenancyName = "settingsfree" + System.nanoTime();

        JsonNode response = registerTenant(Map.of(
                "tenancyName", tenancyName,
                "name", "Settings Free Tenant",
                "adminEmailAddress", "settings-free@example.local",
                "adminPassword", "123qwe",
                "editionId", freeEdition.id,
                "subscriptionStartType", 1
        ));

        assertThat(response.path("success").asBoolean()).isTrue();
        assertThat(response.path("result").path("isActive").asBoolean()).isFalse();
        assertThat(response.path("result").path("isTenantActive").asBoolean()).isFalse();
        assertThat(response.path("result").path("isEmailConfirmationRequired").asBoolean()).isTrue();
        assertThat(store.tenantByTenancyName(tenancyName)).hasValueSatisfying(tenant -> assertThat(tenant.isActive).isFalse());
    }

    @Test
    void registrationRejectsMissingAdminEmailInsteadOfInventingLocalAddress() throws Exception {
        SystemSettingsItem.HostSettings settings = resetHostSettings();
        settings.tenantManagement.useCaptchaOnRegistration = false;
        store.updateHostSettings(settings);
        EditionItem freeEdition = freeEdition("Required Email Edition " + System.nanoTime());
        String tenancyName = "requiredemail" + System.nanoTime();

        JsonNode response = registerTenant(Map.of(
                "tenancyName", tenancyName,
                "name", "Required Email Tenant",
                "adminPassword", "123qwe",
                "editionId", freeEdition.id,
                "subscriptionStartType", 1
        ));

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(store.tenantByTenancyName(tenancyName)).isEmpty();
    }

    @Test
    void registrationRejectsOriginalRegisterTenantInputValidationViolations() throws Exception {
        SystemSettingsItem.HostSettings settings = resetHostSettings();
        settings.tenantManagement.useCaptchaOnRegistration = false;
        store.updateHostSettings(settings);
        EditionItem freeEdition = freeEdition("Register Validation Edition " + System.nanoTime());

        String longTenancyName = "t".repeat(65);
        assertValidationFailure(Map.of(
                "tenancyName", longTenancyName,
                "name", "Long Tenancy Tenant",
                "adminEmailAddress", "long-tenancy@example.local",
                "editionId", freeEdition.id,
                "subscriptionStartType", 1
        ), longTenancyName);

        String tenancyForLongName = "longname" + System.nanoTime();
        assertValidationFailure(Map.of(
                "tenancyName", tenancyForLongName,
                "name", "n".repeat(65),
                "adminEmailAddress", "long-name@example.local",
                "editionId", freeEdition.id,
                "subscriptionStartType", 1
        ), tenancyForLongName);

        String tenancyForLongEmail = "longemail" + System.nanoTime();
        assertValidationFailure(Map.of(
                "tenancyName", tenancyForLongEmail,
                "name", "Long Email Tenant",
                "adminEmailAddress", "e".repeat(245) + "@example.local",
                "editionId", freeEdition.id,
                "subscriptionStartType", 1
        ), tenancyForLongEmail);

        String tenancyForLongPassword = "longpassword" + System.nanoTime();
        assertValidationFailure(Map.of(
                "tenancyName", tenancyForLongPassword,
                "name", "Long Password Tenant",
                "adminEmailAddress", "long-password@example.local",
                "adminPassword", "p".repeat(33),
                "editionId", freeEdition.id,
                "subscriptionStartType", 1
        ), tenancyForLongPassword);
    }

    @Test
    void registrationIsRejectedWhenOriginalHostSelfRegistrationSettingIsDisabled() throws Exception {
        SystemSettingsItem.HostSettings settings = resetHostSettings();
        settings.tenantManagement.allowSelfRegistration = false;
        settings.tenantManagement.useCaptchaOnRegistration = false;
        store.updateHostSettings(settings);
        EditionItem freeEdition = freeEdition("Disabled Registration Edition " + System.nanoTime());
        String tenancyName = "disabledreg" + System.nanoTime();

        JsonNode response = registerTenant(Map.of(
                "tenancyName", tenancyName,
                "name", "Disabled Registration Tenant",
                "adminEmailAddress", "disabled-registration@example.local",
                "adminPassword", "123qwe",
                "editionId", freeEdition.id,
                "subscriptionStartType", 1
        ));

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(store.tenantByTenancyName(tenancyName)).isEmpty();
    }

    private EditionItem freeEdition(String displayName) {
        EditionItem item = new EditionItem();
        item.name = displayName.replaceAll("\\s+", "");
        item.displayName = displayName;
        return store.saveEdition(item, List.of());
    }

    private SystemSettingsItem.HostSettings resetHostSettings() {
        return store.updateHostSettings(SystemSettingsItem.defaultHostSettings());
    }

    private void assertValidationFailure(Object payload, String tenancyName) throws Exception {
        JsonNode response = registerTenant(payload);

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText()).isEqualTo("Validation failed");
        assertThat(store.tenantByTenancyName(tenancyName)).isEmpty();
    }

    private JsonNode registerTenant(Object payload) throws Exception {
        String body = mockMvc.perform(post("/api/services/app/TenantRegistration/RegisterTenant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(payload)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }
}
