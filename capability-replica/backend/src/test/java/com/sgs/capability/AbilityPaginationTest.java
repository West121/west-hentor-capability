package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.model.Ability;
import com.sgs.capability.security.AuthService;
import com.sgs.capability.service.CapabilityStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/ability-pagination-store.json")
@AutoConfigureMockMvc
class AbilityPaginationTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/ability-pagination-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset ability pagination test store", ex);
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
    void abilityManagementEndpointKeepsTotalCountWhenReturningOnlyRequestedPage() throws Exception {
        String token = adminToken();
        String marker = "TDD-PAGE-" + System.nanoTime();
        createAbility(token, marker, "A");
        createAbility(token, marker, "B");

        JsonNode firstPage = findPage(token, "/api/services/app/Ability/FindPageAblibities", marker, 0, 1, "testItem ASC");
        JsonNode secondPage = findPage(token, "/api/services/app/Ability/FindPageAblibities", marker, 1, 1, "testItem ASC");

        assertThat(firstPage.path("totalCount").asInt()).isEqualTo(2);
        assertThat(firstPage.path("items")).hasSize(1);
        assertThat(firstPage.path("items").get(0).path("testItem").asText()).isEqualTo(marker + "-A");
        assertThat(secondPage.path("totalCount").asInt()).isEqualTo(2);
        assertThat(secondPage.path("items")).hasSize(1);
        assertThat(secondPage.path("items").get(0).path("testItem").asText()).isEqualTo(marker + "-B");
    }

    @Test
    void abilityQueryEndpointUsesTheSamePaginationContractAndKeepsLabs() throws Exception {
        String token = adminToken();
        String marker = "TDD-QUERY-PAGE-" + System.nanoTime();
        createAbility(token, marker, "A");
        createAbility(token, marker, "B");

        JsonNode page = findPage(token, "/api/services/app/AbilityQuery/FindAblibities", marker, 1, 1, "testItem ASC");

        assertThat(page.path("totalCount").asInt()).isEqualTo(2);
        assertThat(page.path("items")).hasSize(1);
        assertThat(page.path("items").get(0).path("testItem").asText()).isEqualTo(marker + "-B");
        assertThat(page.path("labs")).isNotEmpty();
    }

    @Test
    void abilityManagementEndpointDefaultsBlankSortingToId() throws Exception {
        String token = adminToken();
        String marker = "TDD-MGMT-SORT-" + System.nanoTime();
        seedAbilityWithId("00000000-0000-0000-0000-000000000010", marker, "B");
        seedAbilityWithId("00000000-0000-0000-0000-000000000020", marker, "A");

        JsonNode page = findPage(token, "/api/services/app/Ability/FindPageAblibities", marker, 0, 1);

        assertThat(page.path("items")).hasSize(1);
        assertThat(page.path("items").get(0).path("testItem").asText()).isEqualTo(marker + "-B");
    }

    @Test
    void abilityQueryEndpointDefaultsBlankSortingToId() throws Exception {
        String token = adminToken();
        String marker = "TDD-QUERY-SORT-" + System.nanoTime();
        seedAbilityWithId("00000000-0000-0000-0000-000000000030", marker, "B");
        seedAbilityWithId("00000000-0000-0000-0000-000000000040", marker, "A");

        JsonNode page = findPage(token, "/api/services/app/AbilityQuery/FindAblibities", marker, 0, 1);

        assertThat(page.path("items")).hasSize(1);
        assertThat(page.path("items").get(0).path("testItem").asText()).isEqualTo(marker + "-B");
    }

    @Test
    void abilityPagingInputsRejectOriginalRangeViolations() throws Exception {
        String token = adminToken();
        assertValidationFailure(postAbpRaw(token, "/api/services/app/Ability/FindPageAblibities", Map.of(
                "skipCount", 0,
                "maxResultCount", 0
        )));
        assertValidationFailure(postAbpRaw(token, "/api/services/app/Ability/FindPageAblibities", Map.of(
                "skipCount", 0,
                "maxResultCount", 1001
        )));
        assertValidationFailure(postAbpRaw(token, "/api/services/app/Ability/FindPageAblibities", Map.of(
                "skipCount", -1,
                "maxResultCount", 10
        )));
        assertValidationFailure(postAbpRaw(token, "/api/services/app/AbilityQuery/FindAblibities", Map.of(
                "skipCount", -1,
                "maxResultCount", 10
        )));
    }

    private void createAbility(String token, String marker, String suffix) throws Exception {
        postAbp(token, "/api/services/app/Ability/CreateAbility", Map.ofEntries(
                entry("orgId", 2),
                entry("typeName", "矿石"),
                entry("samplingName", marker),
                entry("testItem", marker + "-" + suffix),
                entry("methodName", "重量法"),
                entry("methodEngName", "Gravimetric method"),
                entry("standardNo", marker + "-" + suffix),
                entry("cycleWorkingDay", "5"),
                entry("massRequired", "100"),
                entry("sizeRequired", "0.074"),
                entry("detectionLimit", "0.01%"),
                entry("price", "100")
        ));
    }

    private void seedAbilityWithId(String id, String marker, String suffix) {
        Ability ability = new Ability();
        ability.id = UUID.fromString(id);
        ability.orgId = 2L;
        ability.typeName = "矿石";
        ability.samplingName = marker;
        ability.testItem = marker + "-" + suffix;
        ability.methodName = "重量法";
        ability.methodEngName = "Gravimetric method";
        ability.standardNo = marker + "-" + suffix;
        ability.cycleWorkingDay = "5";
        ability.massRequired = "100";
        ability.sizeRequired = "0.074";
        ability.detectionLimit = "0.01%";
        ability.price = "100";
        store.saveAbility(ability);
    }

    private JsonNode findPage(String token, String url, String marker, int skipCount, int maxResultCount) throws Exception {
        return findPage(token, url, marker, skipCount, maxResultCount, null);
    }

    private JsonNode findPage(String token, String url, String marker, int skipCount, int maxResultCount,
                              String sorting) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("filter", marker);
        payload.put("skipCount", skipCount);
        payload.put("maxResultCount", maxResultCount);
        if (sorting != null) {
            payload.put("sorting", sorting);
        }
        return postAbp(token, url, payload).path("result");
    }

    private JsonNode postAbp(String token, String url, Object payload) throws Exception {
        String body = mockMvc.perform(post(url)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(payload)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isTrue();
        return response;
    }

    private JsonNode postAbpRaw(String token, String url, Object payload) throws Exception {
        String body = mockMvc.perform(post(url)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(payload)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body);
    }

    private void assertValidationFailure(JsonNode response) {
        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText()).isEqualTo("Validation failed");
    }

    private String adminToken() {
        return authService.authenticate("admin", "123qwe")
                .orElseThrow()
                .token();
    }
}
