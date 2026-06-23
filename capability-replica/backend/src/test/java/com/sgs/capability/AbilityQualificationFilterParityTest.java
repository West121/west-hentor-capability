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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/ability-qualification-filter-store.json")
@AutoConfigureMockMvc
class AbilityQualificationFilterParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/ability-qualification-filter-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset qualification filter test store", ex);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthService authService;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void allLabAbilityMatchesCnasAndCmaQualificationFilters() throws Exception {
        String token = adminToken();
        String marker = "TDD-资质包含-" + System.nanoTime();
        createAllQualifiedAbility(token, marker);

        assertThat(queryByQualification(token, marker, "CNAS").path("totalCount").asInt()).isEqualTo(1);
        assertThat(queryByQualification(token, marker, "CMA").path("totalCount").asInt()).isEqualTo(1);
    }

    private void createAllQualifiedAbility(String token, String marker) throws Exception {
        postAbp(token, "/api/services/app/Ability/CreateAbility", Map.ofEntries(
                entry("orgId", 2),
                entry("typeName", "矿石"),
                entry("samplingName", marker),
                entry("testItem", marker),
                entry("methodName", "重量法"),
                entry("methodEngName", "Gravimetric method"),
                entry("standardNo", marker),
                entry("cycleWorkingDay", "5"),
                entry("massRequired", "100"),
                entry("sizeRequired", "0.074"),
                entry("detectionLimit", "0.01%"),
                entry("price", "100"),
                entry("labAbilities", List.of(Map.of(
                        "code", "TJ",
                        "isAbility", true,
                        "hasCnas", true,
                        "hasCma", true
                )))
        ));
    }

    private JsonNode queryByQualification(String token, String marker, String qualification) throws Exception {
        return postAbp(token, "/api/services/app/AbilityQuery/FindAblibities", Map.of(
                "filter", marker,
                "filterItems", List.of(
                        Map.of("field", "labAbility", "value", "TJ"),
                        Map.of("field", "ability", "value", qualification)
                ),
                "skipCount", 0,
                "maxResultCount", 10
        )).path("result");
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

    private String adminToken() {
        return authService.authenticate("admin", "123qwe")
                .orElseThrow()
                .token();
    }
}
