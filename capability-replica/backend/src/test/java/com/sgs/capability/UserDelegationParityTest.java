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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/user-delegation-parity-store.json")
@AutoConfigureMockMvc
class UserDelegationParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/user-delegation-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset user delegation parity test store", ex);
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
    void delegateNewUserKeepsOriginalVoidResponseWhileSavingDelegation() throws Exception {
        String start = "2030-01-02T03:04:05";
        String end = "2030-01-09T03:04:05";

        JsonNode response = postAbp("/api/services/app/UserDelegation/DelegateNewUser", Map.of(
                "targetUserId", 2,
                "startTime", start,
                "endTime", end
        ));

        assertThat(response.path("result").isNull()).isTrue();
        JsonNode list = postAbp("/api/services/app/UserDelegation/GetDelegatedUsers", Map.of(
                "maxResultCount", 100
        )).path("result").path("items");
        assertThat(list).anySatisfy(item -> {
            assertThat(item.path("targetUserId").asLong()).isEqualTo(2L);
            assertThat(item.path("startTime").asText()).isEqualTo(start);
            assertThat(item.path("endTime").asText()).isEqualTo(end);
        });
    }

    @Test
    void delegateNewUserToCurrentUserReturnsOriginalSelfDelegationError() throws Exception {
        JsonNode response = postJson("/api/services/app/UserDelegation/DelegateNewUser", Map.of(
                "targetUserId", 1,
                "startTime", "2030-01-02T03:04:05",
                "endTime", "2030-01-09T03:04:05"
        ));

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText())
                .isEqualTo("You can't delegate authorization to yourself !");
    }

    @Test
    void delegateNewUserRejectsOriginalInputValidationViolations() throws Exception {
        assertValidationFailure(postJson("/api/services/app/UserDelegation/DelegateNewUser", Map.of(
                "startTime", "2030-01-02T03:04:05",
                "endTime", "2030-01-09T03:04:05"
        )));

        assertValidationFailure(postJson("/api/services/app/UserDelegation/DelegateNewUser", Map.of(
                "targetUserId", 0,
                "startTime", "2030-01-02T03:04:05",
                "endTime", "2030-01-09T03:04:05"
        )));

        assertValidationFailure(postJson("/api/services/app/UserDelegation/DelegateNewUser", Map.of(
                "targetUserId", 2,
                "endTime", "2030-01-09T03:04:05"
        )));

        assertValidationFailure(postJson("/api/services/app/UserDelegation/DelegateNewUser", Map.of(
                "targetUserId", 2,
                "startTime", "2030-01-02T03:04:05"
        )));

        JsonNode invertedRange = postJson("/api/services/app/UserDelegation/DelegateNewUser", Map.of(
                "targetUserId", 2,
                "startTime", "2030-01-09T03:04:05",
                "endTime", "2030-01-02T03:04:05"
        ));

        assertThat(invertedRange.path("success").asBoolean()).isFalse();
        assertThat(invertedRange.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(invertedRange.path("error").path("message").asText())
                .isEqualTo("StartTime of a user delegation operation can't be bigger than EndTime!");
    }

    @Test
    void removeDelegationByNonSourceUserReturnsOriginalOwnershipError() throws Exception {
        JsonNode response = postJsonWithToken("/api/services/app/UserDelegation/RemoveDelegation",
                Map.of("id", "1"), tokenFor("query", "123qwe"));

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText())
                .isEqualTo("Only source user can delete a user delegation !");
    }

    @Test
    void delegatedUsersSortByOriginalNormalizedUsernameAscending() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        UserItem zzz = user("zzz_delegated_" + suffix);
        UserItem aaa = user("aaa_delegated_" + suffix);

        postAbp("/api/services/app/UserDelegation/DelegateNewUser", Map.of(
                "targetUserId", zzz.id,
                "startTime", "2030-01-02T03:04:05",
                "endTime", "2030-01-09T03:04:05"
        ));
        postAbp("/api/services/app/UserDelegation/DelegateNewUser", Map.of(
                "targetUserId", aaa.id,
                "startTime", "2030-01-02T03:04:05",
                "endTime", "2030-01-09T03:04:05"
        ));

        JsonNode items = getAbp(get("/api/services/app/UserDelegation/GetDelegatedUsers")
                .param("MaxResultCount", "10")
                .param("SkipCount", "0")
                .param("Sorting", "userName ASC"))
                .path("result")
                .path("items");

        assertThat(items.get(0).path("targetUserName").asText()).isEqualTo(aaa.userName);
        assertThat(items.get(items.size() - 1).path("targetUserName").asText()).isEqualTo(zzz.userName);
    }

    private JsonNode postAbp(String url, Object payload) throws Exception {
        String body = mockMvc.perform(post(url)
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

    private JsonNode postJson(String url, Object payload) throws Exception {
        return postJsonWithToken(url, payload, adminToken());
    }

    private JsonNode postJsonWithToken(String url, Object payload, String token) throws Exception {
        String body = mockMvc.perform(post(url)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(payload)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    private JsonNode getAbp(String url) throws Exception {
        return getAbp(get(url));
    }

    private JsonNode getAbp(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
        String body = mockMvc.perform(request.header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isTrue();
        return response;
    }

    private UserItem user(String userName) {
        UserItem user = new UserItem();
        user.userName = userName;
        user.name = userName;
        user.surname = "Delegated";
        user.emailAddress = userName + "@example.local";
        user.isActive = true;
        return store.saveUser(user, List.of("User"), List.of(), List.of());
    }

    private void assertValidationFailure(JsonNode response) {
        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText()).isEqualTo("Validation failed");
    }

    private String adminToken() {
        return tokenFor("admin", "123qwe");
    }

    private String tokenFor(String userName, String password) {
        return authService.authenticate(userName, password).orElseThrow().token();
    }
}
