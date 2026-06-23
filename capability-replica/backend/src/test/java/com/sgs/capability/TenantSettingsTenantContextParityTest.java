package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.model.SystemSettingsItem;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/tenant-settings-tenant-context-store.json")
@AutoConfigureMockMvc
class TenantSettingsTenantContextParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/tenant-settings-tenant-context-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset tenant settings tenant-context test store", ex);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthService authService;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void getAndUpdateAllSettingsUseTokenTenant() throws Exception {
        String tenantOneToken = adminToken();
        String tenantTwoToken = impersonatedTenantToken(2);
        SystemSettingsItem.TenantSettings tenantOne = settings("Tenant One Display", "Tenant One Legal");
        SystemSettingsItem.TenantSettings tenantTwo = settings("Tenant Two Display", "Tenant Two Legal");

        putSettings(tenantOneToken, tenantOne);
        putSettings(tenantTwoToken, tenantTwo);

        JsonNode tenantOneRead = getSettings(tenantOneToken);
        JsonNode tenantTwoRead = getSettings(tenantTwoToken);

        assertThat(tenantOneRead.path("email").path("defaultFromDisplayName").asText()).isEqualTo("Tenant One Display");
        assertThat(tenantOneRead.path("billing").path("legalName").asText()).isEqualTo("Tenant One Legal");
        assertThat(tenantTwoRead.path("email").path("defaultFromDisplayName").asText()).isEqualTo("Tenant Two Display");
        assertThat(tenantTwoRead.path("billing").path("legalName").asText()).isEqualTo("Tenant Two Legal");
    }

    private SystemSettingsItem.TenantSettings settings(String displayName, String legalName) {
        SystemSettingsItem.TenantSettings settings = SystemSettingsItem.defaultTenantSettings();
        settings.email.defaultFromDisplayName = displayName;
        settings.billing.legalName = legalName;
        return settings;
    }

    private void putSettings(String accessToken, SystemSettingsItem.TenantSettings input) throws Exception {
        String body = mockMvc.perform(put("/api/services/app/TenantSettings/UpdateAllSettings")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(input)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isTrue();
        assertThat(response.path("result").isNull()).isTrue();
    }

    private JsonNode getSettings(String accessToken) throws Exception {
        String body = mockMvc.perform(get("/api/services/app/TenantSettings/GetAllSettings")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isTrue();
        return response.path("result");
    }

    private String adminToken() {
        return authService.authenticate("admin", "123qwe").orElseThrow().token();
    }

    private String impersonatedTenantToken(int tenantId) throws Exception {
        String token = authService.createImpersonationToken(1L, tenantId);
        String body = mockMvc.perform(get("/api/TokenAuth/ImpersonatedAuthenticate")
                        .param("impersonationToken", token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("result").path("accessToken").asText();
    }
}
