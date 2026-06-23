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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/friendship-parity-store.json")
@AutoConfigureMockMvc
class FriendshipParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/friendship-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset friendship parity test store", ex);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthService authService;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void createFriendshipRequestForExistingFriendReturnsOriginalDuplicateError() throws Exception {
        JsonNode response = postJson("/api/services/app/Friendship/CreateFriendshipRequest", Map.of(
                "userId", 2
        ));

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText()).isEqualTo("You already added this user.");
    }

    @Test
    void createFriendshipRequestByUserNameForExistingFriendReturnsOriginalDuplicateError() throws Exception {
        JsonNode response = postJson("/api/services/app/Friendship/CreateFriendshipRequestByUserName", Map.of(
                "userName", "query"
        ));

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText()).isEqualTo("You already added this user.");
    }

    @Test
    void createFriendshipRequestByUserNameValidatesOriginalTenancyNameBeforeUserLookup() throws Exception {
        JsonNode response = postJson("/api/services/app/Friendship/CreateFriendshipRequestByUserName", Map.of(
                "tenancyName", "missing",
                "userName", "query"
        ));

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText())
                .isEqualTo("There is no tenant defined with name missing");
    }

    @Test
    void createFriendshipRequestForCurrentUserReturnsOriginalSelfFriendError() throws Exception {
        JsonNode response = postJson("/api/services/app/Friendship/CreateFriendshipRequest", Map.of(
                "userId", 1
        ));

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText())
                .isEqualTo("You can not be a friend with yourself.");
    }

    @Test
    void createFriendshipRequestByUserNameForCurrentUserReturnsOriginalSelfFriendError() throws Exception {
        JsonNode response = postJson("/api/services/app/Friendship/CreateFriendshipRequestByUserName", Map.of(
                "userName", "admin"
        ));

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText())
                .isEqualTo("You can not be a friend with yourself.");
    }

    @Test
    void createFriendshipRequestByMissingUserNameReturnsOriginalTenancyNameError() throws Exception {
        JsonNode response = postJson("/api/services/app/Friendship/CreateFriendshipRequestByUserName", Map.of(
                "tenancyName", ".",
                "userName", "missing-user"
        ));

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText())
                .isEqualTo("There is no tenant defined with name .");
    }

    @Test
    void friendshipUserIdentifierInputsRejectOriginalRangeViolations() throws Exception {
        assertValidationFailure(postJson("/api/services/app/Friendship/CreateFriendshipRequest", Map.of(
                "userId", 0
        )));
        assertValidationFailure(postJson("/api/services/app/Friendship/BlockUser", Map.of(
                "userId", 0
        )));
        assertValidationFailure(postJson("/api/services/app/Friendship/UnblockUser", Map.of(
                "userId", 0
        )));
        assertValidationFailure(postJson("/api/services/app/Friendship/AcceptFriendshipRequest", Map.of(
                "userId", 0
        )));
    }

    @Test
    void blockUserWithoutExistingFriendshipReturnsOriginalMissingFriendshipError() throws Exception {
        JsonNode response = postJson("/api/services/app/Friendship/BlockUser", Map.of(
                "userId", 999
        ));

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText())
                .startsWith("Friendship does not exist between");
    }

    @Test
    void unblockUserWithoutExistingFriendshipReturnsOriginalMissingFriendshipError() throws Exception {
        JsonNode response = postJson("/api/services/app/Friendship/UnblockUser", Map.of(
                "userId", 999
        ));

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText())
                .startsWith("Friendship does not exist between");
    }

    @Test
    void acceptFriendshipRequestWithoutExistingFriendshipReturnsOriginalMissingFriendshipError() throws Exception {
        JsonNode response = postJson("/api/services/app/Friendship/AcceptFriendshipRequest", Map.of(
                "userId", 999
        ));

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText())
                .startsWith("Friendship does not exist between");
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
