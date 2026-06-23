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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/settings-parity-store.json")
@AutoConfigureMockMvc
class SettingsParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/settings-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset settings parity test store", ex);
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
    void sessionTimeOutDefaultsMatchOriginalSettingProvider() {
        SystemSettingsItem.HostSettings host = SystemSettingsItem.defaultHostSettings();
        SystemSettingsItem.TenantSettings tenant = SystemSettingsItem.defaultTenantSettings();

        assertThat(host.userManagement.sessionTimeOutSettings.isEnabled).isFalse();
        assertThat(host.userManagement.sessionTimeOutSettings.timeOutSecond).isEqualTo(30);
        assertThat(host.userManagement.sessionTimeOutSettings.showTimeOutNotificationSecond).isEqualTo(30);
        assertThat(host.userManagement.sessionTimeOutSettings.showLockScreenWhenTimedOut).isFalse();
        assertThat(tenant.userManagement.sessionTimeOutSettings.isEnabled).isFalse();
        assertThat(tenant.userManagement.sessionTimeOutSettings.timeOutSecond).isEqualTo(30);
        assertThat(tenant.userManagement.sessionTimeOutSettings.showTimeOutNotificationSecond).isEqualTo(30);
        assertThat(tenant.userManagement.sessionTimeOutSettings.showLockScreenWhenTimedOut).isFalse();
    }

    @Test
    void updateHostSettingsKeepsOriginalVoidResponseWhileSavingSettings() throws Exception {
        SystemSettingsItem.HostSettings input = SystemSettingsItem.defaultHostSettings();
        input.email.defaultFromAddress = "host-parity@example.local";
        input.billing.legalName = "Host Parity Legal";

        JsonNode response = postAbp("/api/services/app/HostSettings/UpdateAllSettings", input);

        assertThat(response.path("result").isNull()).isTrue();
        assertThat(store.hostSettings().email.defaultFromAddress).isEqualTo("host-parity@example.local");
        assertThat(store.hostSettings().billing.legalName).isEqualTo("Host Parity Legal");
    }

    @Test
    void updateHostAbilitySettingsKeepsOriginalVoidResponseWhileSavingSettings() throws Exception {
        SystemSettingsItem.AbilitySettings input = new SystemSettingsItem.AbilitySettings();
        input.description = "Ability description parity " + System.nanoTime();

        JsonNode response = postAbp("/api/services/app/HostSettings/UpdateAbilitySettings", input);

        assertThat(response.path("result").isNull()).isTrue();
        assertThat(store.abilitySettings().description).isEqualTo(input.description);
    }

    @Test
    void updateTenantSettingsKeepsOriginalVoidResponseWhileSavingSettings() throws Exception {
        SystemSettingsItem.TenantSettings input = SystemSettingsItem.defaultTenantSettings();
        input.email.defaultFromDisplayName = "Tenant Parity";
        input.billing.legalName = "Tenant Parity Legal";

        JsonNode response = postAbp("/api/services/app/TenantSettings/UpdateAllSettings", input);

        assertThat(response.path("result").isNull()).isTrue();
        assertThat(store.tenantSettings().email.defaultFromDisplayName).isEqualTo("Tenant Parity");
        assertThat(store.tenantSettings().billing.legalName).isEqualTo("Tenant Parity Legal");
    }

    @Test
    void updateSettingsRejectsOriginalSessionTimeOutRangeViolations() throws Exception {
        SystemSettingsItem.HostSettings hostShortTimeout = SystemSettingsItem.defaultHostSettings();
        hostShortTimeout.userManagement.sessionTimeOutSettings.timeOutSecond = 9;

        assertValidationFailure(postAbpRaw("/api/services/app/HostSettings/UpdateAllSettings", hostShortTimeout));

        SystemSettingsItem.HostSettings hostShortNotification = SystemSettingsItem.defaultHostSettings();
        hostShortNotification.userManagement.sessionTimeOutSettings.showTimeOutNotificationSecond = 9;

        assertValidationFailure(postAbpRaw("/api/services/app/HostSettings/UpdateAllSettings", hostShortNotification));

        SystemSettingsItem.TenantSettings tenantShortTimeout = SystemSettingsItem.defaultTenantSettings();
        tenantShortTimeout.userManagement.sessionTimeOutSettings.timeOutSecond = 9;

        assertValidationFailure(postAbpRaw("/api/services/app/TenantSettings/UpdateAllSettings", tenantShortTimeout));

        SystemSettingsItem.TenantSettings tenantShortNotification = SystemSettingsItem.defaultTenantSettings();
        tenantShortNotification.userManagement.sessionTimeOutSettings.showTimeOutNotificationSecond = 9;

        assertValidationFailure(postAbpRaw("/api/services/app/TenantSettings/UpdateAllSettings", tenantShortNotification));
    }

    @Test
    void updateSettingsRejectsOriginalRequiredSectionViolations() throws Exception {
        SystemSettingsItem.HostSettings hostMissingGeneral = SystemSettingsItem.defaultHostSettings();
        hostMissingGeneral.general = null;
        assertValidationFailure(postAbpRaw("/api/services/app/HostSettings/UpdateAllSettings", hostMissingGeneral));

        SystemSettingsItem.HostSettings hostMissingUserManagement = SystemSettingsItem.defaultHostSettings();
        hostMissingUserManagement.userManagement = null;
        assertValidationFailure(postAbpRaw("/api/services/app/HostSettings/UpdateAllSettings", hostMissingUserManagement));

        SystemSettingsItem.HostSettings hostMissingEmail = SystemSettingsItem.defaultHostSettings();
        hostMissingEmail.email = null;
        assertValidationFailure(postAbpRaw("/api/services/app/HostSettings/UpdateAllSettings", hostMissingEmail));

        SystemSettingsItem.HostSettings hostMissingTenantManagement = SystemSettingsItem.defaultHostSettings();
        hostMissingTenantManagement.tenantManagement = null;
        assertValidationFailure(postAbpRaw("/api/services/app/HostSettings/UpdateAllSettings", hostMissingTenantManagement));

        SystemSettingsItem.HostSettings hostMissingSecurity = SystemSettingsItem.defaultHostSettings();
        hostMissingSecurity.security = null;
        assertValidationFailure(postAbpRaw("/api/services/app/HostSettings/UpdateAllSettings", hostMissingSecurity));

        SystemSettingsItem.TenantSettings tenantMissingUserManagement = SystemSettingsItem.defaultTenantSettings();
        tenantMissingUserManagement.userManagement = null;
        assertValidationFailure(postAbpRaw("/api/services/app/TenantSettings/UpdateAllSettings", tenantMissingUserManagement));

        SystemSettingsItem.TenantSettings tenantMissingEmail = SystemSettingsItem.defaultTenantSettings();
        tenantMissingEmail.email = null;
        assertValidationFailure(postAbpRaw("/api/services/app/TenantSettings/UpdateAllSettings", tenantMissingEmail));

        SystemSettingsItem.TenantSettings tenantMissingSecurity = SystemSettingsItem.defaultTenantSettings();
        tenantMissingSecurity.security = null;
        assertValidationFailure(postAbpRaw("/api/services/app/TenantSettings/UpdateAllSettings", tenantMissingSecurity));
    }

    @Test
    void sendTestEmailRejectsOriginalDtoValidationViolations() throws Exception {
        JsonNode missingHostEmail = postAbpRaw("/api/services/app/HostSettings/SendTestEmail", Map.of());

        assertValidationFailure(missingHostEmail);

        JsonNode longHostEmail = postAbpRaw("/api/services/app/HostSettings/SendTestEmail", Map.of(
                "emailAddress", "e".repeat(245) + "@example.local"
        ));

        assertValidationFailure(longHostEmail);

        JsonNode missingTenantEmail = postAbpRaw("/api/services/app/TenantSettings/SendTestEmail", Map.of());

        assertValidationFailure(missingTenantEmail);
    }

    private JsonNode postAbp(String url, Object payload) throws Exception {
        JsonNode response = postAbpRaw(url, payload);
        assertThat(response.path("success").asBoolean()).isTrue();
        return response;
    }

    private JsonNode postAbpRaw(String url, Object payload) throws Exception {
        String body = mockMvc.perform(post(url)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(payload)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode response = objectMapper.readTree(body);
        return response;
    }

    private void assertValidationFailure(JsonNode response) {
        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText()).isEqualTo("Validation failed");
    }

    private String adminToken() {
        return authService.authenticate("admin", "123qwe").orElseThrow().token();
    }
}
