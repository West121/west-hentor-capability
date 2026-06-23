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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/back-to-impersonator-tenant-context-store.json")
@AutoConfigureMockMvc
class BackToImpersonatorTenantContextParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/back-to-impersonator-tenant-context-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset back-to-impersonator tenant context test store", ex);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthService authService;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void backToImpersonatorUsesImpersonatorTenantFromCurrentSession() throws Exception {
        String adminTenantTwoToken = tokenForImpersonatedTenant(1L, 2);
        String targetImpersonationToken = postAbpWithToken("/api/services/app/Account/Impersonate",
                adminTenantTwoToken, Map.of("userId", 2, "tenantId", 1))
                .path("result")
                .path("impersonationToken")
                .asText();
        String impersonatedUserToken = getAbp("/api/TokenAuth/ImpersonatedAuthenticate?impersonationToken="
                + targetImpersonationToken)
                .path("result")
                .path("accessToken")
                .asText();

        JsonNode backOutput = postAbpWithToken("/api/services/app/Account/BackToImpersonator",
                impersonatedUserToken, Map.of())
                .path("result");

        assertThat(backOutput.path("tenancyName").asText()).isEqualTo("trial");

        String backAccessToken = getAbp("/api/TokenAuth/ImpersonatedAuthenticate?impersonationToken="
                + backOutput.path("impersonationToken").asText())
                .path("result")
                .path("accessToken")
                .asText();
        JsonNode session = getAbpWithToken("/api/services/app/Session/GetCurrentLoginInformations", backAccessToken)
                .path("result");

        assertThat(session.path("user").path("id").asLong()).isEqualTo(1L);
        assertThat(session.path("tenant").path("id").asInt()).isEqualTo(2);
        assertThat(session.path("tenant").path("tenancyName").asText()).isEqualTo("trial");
    }

    @Test
    void linkedAccountSwitchPreservesImpersonatorTenantForBackNavigation() throws Exception {
        String adminTenantTwoToken = tokenForImpersonatedTenant(1L, 2);
        String targetImpersonationToken = postAbpWithToken("/api/services/app/Account/Impersonate",
                adminTenantTwoToken, Map.of("userId", 2, "tenantId", 1))
                .path("result")
                .path("impersonationToken")
                .asText();
        String impersonatedUserToken = getAbp("/api/TokenAuth/ImpersonatedAuthenticate?impersonationToken="
                + targetImpersonationToken)
                .path("result")
                .path("accessToken")
                .asText();

        String switchAccountToken = postAbpWithToken("/api/services/app/Account/SwitchToLinkedAccount",
                impersonatedUserToken, Map.of("targetUserId", 1, "targetTenantId", 1))
                .path("result")
                .path("switchAccountToken")
                .asText();
        String switchedAccessToken = getAbp("/api/TokenAuth/LinkedAccountAuthenticate?switchAccountToken="
                + switchAccountToken)
                .path("result")
                .path("accessToken")
                .asText();

        JsonNode backOutput = postAbpWithToken("/api/services/app/Account/BackToImpersonator",
                switchedAccessToken, Map.of())
                .path("result");

        assertThat(backOutput.path("tenancyName").asText()).isEqualTo("trial");

        String backAccessToken = getAbp("/api/TokenAuth/ImpersonatedAuthenticate?impersonationToken="
                + backOutput.path("impersonationToken").asText())
                .path("result")
                .path("accessToken")
                .asText();
        JsonNode session = getAbpWithToken("/api/services/app/Session/GetCurrentLoginInformations", backAccessToken)
                .path("result");

        assertThat(session.path("user").path("id").asLong()).isEqualTo(1L);
        assertThat(session.path("tenant").path("id").asInt()).isEqualTo(2);
        assertThat(session.path("tenant").path("tenancyName").asText()).isEqualTo("trial");
    }

    private String tokenForImpersonatedTenant(Long userId, Integer tenantId) throws Exception {
        String impersonationToken = authService.createImpersonationToken(userId, tenantId);
        return getAbp("/api/TokenAuth/ImpersonatedAuthenticate?impersonationToken=" + impersonationToken)
                .path("result")
                .path("accessToken")
                .asText();
    }

    private JsonNode postAbpWithToken(String url, String accessToken, Object payload) throws Exception {
        String body = mockMvc.perform(post(url)
                        .header("Authorization", "Bearer " + accessToken)
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

    private JsonNode getAbp(String url) throws Exception {
        String body = mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isTrue();
        return response;
    }

    private JsonNode getAbpWithToken(String url, String accessToken) throws Exception {
        String body = mockMvc.perform(get(url).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isTrue();
        return response;
    }
}
