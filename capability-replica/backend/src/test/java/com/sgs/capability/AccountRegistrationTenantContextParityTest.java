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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/account-registration-tenant-context-store.json")
@AutoConfigureMockMvc
class AccountRegistrationTenantContextParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/account-registration-tenant-context-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset account registration tenant-context test store", ex);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    CapabilityStore store;

    @Autowired
    AuthService authService;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void registerUsesTokenTenantRegistrationSettings() throws Exception {
        SystemSettingsItem.TenantSettings tenantOne = SystemSettingsItem.defaultTenantSettings();
        tenantOne.userManagement.allowSelfRegistration = true;
        tenantOne.userManagement.useCaptchaOnRegistration = false;
        tenantOne.userManagement.isNewRegisteredUserActiveByDefault = true;
        tenantOne.userManagement.isEmailConfirmationRequiredForLogin = false;
        store.updateTenantSettings(1, tenantOne);

        SystemSettingsItem.TenantSettings tenantTwo = SystemSettingsItem.defaultTenantSettings();
        tenantTwo.userManagement.allowSelfRegistration = true;
        tenantTwo.userManagement.useCaptchaOnRegistration = false;
        tenantTwo.userManagement.isNewRegisteredUserActiveByDefault = false;
        tenantTwo.userManagement.isEmailConfirmationRequiredForLogin = true;
        store.updateTenantSettings(2, tenantTwo);

        String accessToken = impersonatedTenantToken(2);
        String userName = "tenanttworegister" + System.nanoTime();

        JsonNode response = registerAccount(accessToken, userName, userName + "@example.local");

        assertThat(response.path("success").asBoolean()).isTrue();
        assertThat(response.path("result").path("canLogin").asBoolean()).isFalse();
        assertThat(store.userByUserNameOrEmail(userName)).hasValueSatisfying(user -> {
            assertThat(user.isActive).isFalse();
            assertThat(user.isEmailConfirmed).isFalse();
        });
    }

    private JsonNode registerAccount(String accessToken, String userName, String emailAddress) throws Exception {
        String body = mockMvc.perform(post("/api/services/app/Account/Register")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "name", "Tenant",
                                "surname", "Register",
                                "userName", userName,
                                "emailAddress", emailAddress,
                                "password", "123qwe"
                        ))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
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
