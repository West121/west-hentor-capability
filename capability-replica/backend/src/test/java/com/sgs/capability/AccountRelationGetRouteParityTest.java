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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/account-relation-get-route-parity-store.json")
@AutoConfigureMockMvc
class AccountRelationGetRouteParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/account-relation-get-route-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset account relation GET route parity test store", ex);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthService authService;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void accountRelationReadRoutesAcceptOriginalGetQueryParameters() throws Exception {
        // Original generated clients use GET with these PascalCase paging keys.
        JsonNode delegatedUsers = getAbp(get("/api/services/app/UserDelegation/GetDelegatedUsers")
                .param("MaxResultCount", "1")
                .param("SkipCount", "0")
                .param("Sorting", "username ASC")).path("result");

        assertThat(delegatedUsers.path("totalCount").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(delegatedUsers.path("items")).hasSize(1);
        assertThat(delegatedUsers.path("items").get(0).path("targetUserName").asText()).isEqualTo("query");

        JsonNode linkedUsers = getAbp(get("/api/services/app/UserLink/GetLinkedUsers")
                .param("MaxResultCount", "1")
                .param("SkipCount", "0")
                .param("Sorting", "TenancyName, Username")).path("result");

        assertThat(linkedUsers.path("totalCount").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(linkedUsers.path("items")).hasSize(1);
        assertThat(linkedUsers.path("items").get(0).path("username").asText()).isEqualTo("query");
    }

    @Test
    void linkedUsersRejectOriginalPagedInputRangeViolations() throws Exception {
        assertValidationFailure(raw(get("/api/services/app/UserLink/GetLinkedUsers")
                .param("MaxResultCount", "0")
                .param("SkipCount", "0")));

        assertValidationFailure(raw(get("/api/services/app/UserLink/GetLinkedUsers")
                .param("MaxResultCount", "1001")
                .param("SkipCount", "0")));

        assertValidationFailure(raw(get("/api/services/app/UserLink/GetLinkedUsers")
                .param("MaxResultCount", "10")
                .param("SkipCount", "-1")));

        assertValidationFailure(raw(post("/api/services/app/UserLink/GetLinkedUsers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of(
                        "maxResultCount", 0,
                        "skipCount", 0
                )))));
    }

    @Test
    void delegatedUsersRejectOriginalPagedInputRangeViolations() throws Exception {
        assertValidationFailure(raw(get("/api/services/app/UserDelegation/GetDelegatedUsers")
                .param("MaxResultCount", "0")
                .param("SkipCount", "0")));

        assertValidationFailure(raw(get("/api/services/app/UserDelegation/GetDelegatedUsers")
                .param("MaxResultCount", "1001")
                .param("SkipCount", "0")));

        assertValidationFailure(raw(get("/api/services/app/UserDelegation/GetDelegatedUsers")
                .param("MaxResultCount", "10")
                .param("SkipCount", "-1")));

        assertValidationFailure(raw(post("/api/services/app/UserDelegation/GetDelegatedUsers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of(
                        "maxResultCount", 1001,
                        "skipCount", 0
                )))));
    }

    private JsonNode getAbp(MockHttpServletRequestBuilder request) throws Exception {
        String body = mockMvc.perform(request.header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isTrue();
        return response;
    }

    private JsonNode raw(MockHttpServletRequestBuilder request) throws Exception {
        String body = mockMvc.perform(request.header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    private void assertValidationFailure(JsonNode response) {
        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
    }

    private String adminToken() {
        return authService.authenticate("admin", "123qwe").orElseThrow().token();
    }
}
