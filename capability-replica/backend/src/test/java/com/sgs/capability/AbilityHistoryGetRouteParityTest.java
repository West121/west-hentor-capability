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

@SpringBootTest(properties = "replica.store.path=target/test-data/ability-history-get-route-parity-store.json")
@AutoConfigureMockMvc
class AbilityHistoryGetRouteParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/ability-history-get-route-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset ability history GET route parity test store", ex);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthService authService;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void abilityHistoryReadRoutesAcceptOriginalGetQueryParameters() throws Exception {
        // These query names mirror the generated Angular service, including casing.
        JsonNode firstPage = getAbp(get("/api/services/app/AbilityHistory/GetAbilityHistory")
                .param("Sorting", "changeTime DESC")
                .param("MaxResultCount", "1")
                .param("SkipCount", "0")).path("result");

        assertThat(firstPage.path("totalCount").asInt()).isGreaterThan(1);
        assertThat(firstPage.path("items")).hasSize(1);

        long firstId = firstPage.path("items").get(0).path("id").asLong();
        JsonNode secondPage = getAbp(get("/api/services/app/AbilityHistory/GetAbilityHistory")
                .param("Sorting", "changeTime DESC")
                .param("MaxResultCount", "1")
                .param("SkipCount", "1")).path("result");
        assertThat(secondPage.path("items")).hasSize(1);
        assertThat(secondPage.path("items").get(0).path("id").asLong()).isNotEqualTo(firstId);

        JsonNode detail = getAbp(get("/api/services/app/AbilityHistory/GetHistoryDetail")
                .param("Id", String.valueOf(firstId))).path("result").path("items");
        assertThat(detail.isArray()).isTrue();
    }

    @Test
    void abilityHistorySortsByOriginalChangeTimeInput() throws Exception {
        JsonNode items = getAbp(get("/api/services/app/AbilityHistory/GetAbilityHistory")
                .param("Sorting", "changeTime ASC")
                .param("MaxResultCount", "20")
                .param("SkipCount", "0")).path("result").path("items");

        assertThat(items.size()).isGreaterThan(1);
        String firstChangeTime = items.get(0).path("changeTime").asText();
        String lastChangeTime = items.get(items.size() - 1).path("changeTime").asText();
        assertThat(firstChangeTime).isLessThanOrEqualTo(lastChangeTime);
    }

    @Test
    void abilityHistoryRejectsOriginalPagedInputRangeViolations() throws Exception {
        assertValidationFailure(raw(get("/api/services/app/AbilityHistory/GetAbilityHistory")
                .param("SkipCount", "0")
                .param("MaxResultCount", "0")));

        assertValidationFailure(raw(post("/api/services/app/AbilityHistory/GetAbilityHistory")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of(
                        "skipCount", 0,
                        "maxResultCount", 1001
                )))));

        assertValidationFailure(raw(get("/api/services/app/AbilityHistory/GetAbilityHistory")
                .param("SkipCount", "-1")
                .param("MaxResultCount", "10")));
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

    private void assertValidationFailure(JsonNode response) {
        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText()).isEqualTo("Validation failed");
    }

    private JsonNode raw(MockHttpServletRequestBuilder request) throws Exception {
        String body = mockMvc.perform(request.header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    private String adminToken() {
        return authService.authenticate("admin", "123qwe").orElseThrow().token();
    }
}
