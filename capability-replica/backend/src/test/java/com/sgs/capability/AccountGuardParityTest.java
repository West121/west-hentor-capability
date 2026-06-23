package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.model.UserItem;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/account-guard-parity-store.json")
@AutoConfigureMockMvc
class AccountGuardParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/account-guard-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset account guard parity test store", ex);
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
    void linkToCurrentUserReturnsOriginalSameAccountError() throws Exception {
        JsonNode response = postJson("/api/services/app/UserLink/LinkToUser", Map.of(
                "usernameOrEmailAddress", "admin",
                "password", "123qwe"
        ));

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText()).isEqualTo("You can not link to same account!");
    }

    @Test
    void linkToUserRequiringPasswordChangeReturnsOriginalPasswordChangeError() throws Exception {
        UserItem query = store.user(2L).orElseThrow();
        boolean originalShouldChange = query.shouldChangePasswordOnNextLogin;
        query.shouldChangePasswordOnNextLogin = true;
        try {
            JsonNode response = postJson("/api/services/app/UserLink/LinkToUser", Map.of(
                    "usernameOrEmailAddress", "query",
                    "password", "123qwe"
            ));

            assertThat(response.path("success").asBoolean()).isFalse();
            assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
            assertThat(response.path("error").path("message").asText())
                    .isEqualTo("You must change your password before linking this account!");
        } finally {
            query.shouldChangePasswordOnNextLogin = originalShouldChange;
        }
    }

    @Test
    void linkToUserRejectsOriginalRequiredFieldViolations() throws Exception {
        assertValidationFailure(postJson("/api/services/app/UserLink/LinkToUser", Map.of(
                "password", "123qwe"
        )));

        assertValidationFailure(postJson("/api/services/app/UserLink/LinkToUser", Map.of(
                "usernameOrEmailAddress", "query"
        )));
    }

    @Test
    void unlinkUserWithoutAnyLinkedAccountReturnsOriginalError() throws Exception {
        JsonNode firstUnlink = postJson("/api/services/app/UserLink/UnlinkUser", Map.of(
                "userId", 2,
                "tenantId", 1
        ));
        assertThat(firstUnlink.path("success").asBoolean()).isTrue();

        JsonNode response = postJson("/api/services/app/UserLink/UnlinkUser", Map.of(
                "userId", 2,
                "tenantId", 1
        ));

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText()).isEqualTo("You are not linked to any account");
    }

    @Test
    void deleteCurrentUserReturnsOriginalOwnAccountError() throws Exception {
        String body = mockMvc.perform(delete("/api/services/app/User/DeleteUser")
                        .param("Id", "1")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText()).isEqualTo("You can not delete own user account!");
    }

    @Test
    void impersonationRoutesRejectOriginalPositiveUserIdViolations() throws Exception {
        assertValidationFailure(postJson("/api/services/app/Account/Impersonate", Map.of()));

        assertValidationFailure(postJson("/api/services/app/Account/Impersonate", Map.of("userId", 0)));

        assertValidationFailure(postJson("/api/services/app/Account/SwitchToLinkedAccount", Map.of()));

        assertValidationFailure(postJson("/api/services/app/Account/SwitchToLinkedAccount", Map.of("targetUserId", 0)));
    }

    private JsonNode postJson(String url, Object payload) throws Exception {
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

    private String adminToken() {
        return authService.authenticate("admin", "123qwe").orElseThrow().token();
    }
}
