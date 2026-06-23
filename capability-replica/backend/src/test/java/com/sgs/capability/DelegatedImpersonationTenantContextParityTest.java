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

@SpringBootTest(properties = "replica.store.path=target/test-data/delegated-impersonation-tenant-context-store.json")
@AutoConfigureMockMvc
class DelegatedImpersonationTenantContextParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/delegated-impersonation-tenant-context-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset delegated impersonation tenant context test store", ex);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthService authService;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void delegatedImpersonateUsesTenantStoredOnUserDelegation() throws Exception {
        String adminTenantTwoToken = tokenForImpersonatedTenant(1L, 2);
        String start = "2026-01-02T03:04:05";
        String end = "2030-01-09T03:04:05";

        postAbpWithToken("/api/services/app/UserDelegation/DelegateNewUser", adminTenantTwoToken, Map.of(
                "targetUserId", 2,
                "startTime", start,
                "endTime", end
        ));
        Long delegationId = findDelegationId(adminTenantTwoToken, start, end);

        JsonNode delegatedOutput = postAbpWithToken("/api/services/app/Account/DelegatedImpersonate",
                tokenFor("query", "123qwe"), Map.of("userDelegationId", delegationId))
                .path("result");

        assertThat(delegatedOutput.path("tenancyName").asText()).isEqualTo("trial");

        String accessToken = getAbp("/api/TokenAuth/ImpersonatedAuthenticate?impersonationToken="
                + delegatedOutput.path("impersonationToken").asText())
                .path("result")
                .path("accessToken")
                .asText();
        JsonNode session = getAbpWithToken("/api/services/app/Session/GetCurrentLoginInformations", accessToken)
                .path("result");

        assertThat(session.path("tenant").path("id").asInt()).isEqualTo(2);
        assertThat(session.path("tenant").path("tenancyName").asText()).isEqualTo("trial");
    }

    @Test
    void delegatedSessionIsRejectedAfterDelegationIsRemoved() throws Exception {
        JsonNode delegatedOutput = postAbpWithToken("/api/services/app/Account/DelegatedImpersonate",
                tokenFor("query", "123qwe"), Map.of("userDelegationId", 1))
                .path("result");
        String delegatedAccessToken = getAbp("/api/TokenAuth/ImpersonatedAuthenticate?impersonationToken="
                + delegatedOutput.path("impersonationToken").asText())
                .path("result")
                .path("accessToken")
                .asText();

        postAbpWithToken("/api/services/app/UserDelegation/RemoveDelegation",
                tokenFor("admin", "123qwe"), Map.of("id", "1"));

        JsonNode response = getJsonWithToken("/api/services/app/Session/GetCurrentLoginInformations", delegatedAccessToken);

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isTrue();
        assertThat(response.path("error").path("message").asText())
                .isEqualTo("ThereIsNoActiveUserDelegationBetweenYourUserAndCurrentUser");
    }

    private Long findDelegationId(String sourceToken, String start, String end) throws Exception {
        JsonNode items = postAbpWithToken("/api/services/app/UserDelegation/GetDelegatedUsers", sourceToken, Map.of(
                "maxResultCount", 100
        )).path("result").path("items");
        for (JsonNode item : items) {
            if (start.equals(item.path("startTime").asText()) && end.equals(item.path("endTime").asText())) {
                return item.path("id").asLong();
            }
        }
        throw new AssertionError("Delegation not found for " + start + " - " + end);
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

    private JsonNode getJsonWithToken(String url, String accessToken) throws Exception {
        String body = mockMvc.perform(get(url).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    private String tokenFor(String userName, String password) {
        return authService.authenticate(userName, password).orElseThrow().token();
    }
}
