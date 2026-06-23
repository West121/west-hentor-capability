package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.dto.FindAbilityRequest;
import com.sgs.capability.model.Ability;
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

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/ability-write-parity-store.json")
@AutoConfigureMockMvc
class AbilityWriteParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/ability-write-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset ability write parity test store", ex);
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
    void createAbilityKeepsOriginalVoidResponseWhileSavingAbility() throws Exception {
        String marker = "TDD-CREATE-VOID-" + System.nanoTime();

        JsonNode response = postAbp("/api/services/app/Ability/CreateAbility", abilityPayload(marker, "重量法"));

        assertThat(response.path("result").isNull()).isTrue();
        assertThat(findOne(marker)).satisfies(ability -> {
            assertThat(ability.testItem).isEqualTo(marker);
            assertThat(ability.methodName).isEqualTo("重量法");
        });
    }

    @Test
    void updateAbilityKeepsOriginalVoidResponseWhileSavingAbility() throws Exception {
        String marker = "TDD-UPDATE-VOID-" + System.nanoTime();
        Ability existing = store.saveAbility(ability(marker, "重量法"));

        existing.methodName = "容量法";
        JsonNode response = postAbp("/api/services/app/Ability/UpdateAbility", existing);

        assertThat(response.path("result").isNull()).isTrue();
        assertThat(store.getAbility(existing.id.toString())).hasValueSatisfying(ability -> {
            assertThat(ability.testItem).isEqualTo(marker);
            assertThat(ability.methodName).isEqualTo("容量法");
        });
    }

    @Test
    void deleteAbilityAcceptsOriginalDeleteRequestWithQueryId() throws Exception {
        String marker = "TDD-DELETE-" + System.nanoTime();
        Ability existing = store.saveAbility(ability(marker, "重量法"));

        JsonNode response = deleteAbp("/api/services/app/Ability/DeleteAbility", "Id", existing.id.toString());

        assertThat(response.path("result").isNull()).isTrue();
        assertThat(store.getAbility(existing.id.toString())).isEmpty();
    }

    @Test
    void deleteAllAcceptsOriginalDeleteRequestWithQueryOrgName() throws Exception {
        String orgName = "TDD-DELETE-ALL-ORG-" + System.nanoTime();
        Ability first = ability("TDD-DELETE-ALL-1-" + System.nanoTime(), "重量法");
        first.orgId = null;
        first.orgName = orgName;
        Ability second = ability("TDD-DELETE-ALL-2-" + System.nanoTime(), "容量法");
        second.orgId = null;
        second.orgName = orgName;
        first = store.saveAbility(first);
        second = store.saveAbility(second);

        JsonNode response = deleteAbp("/api/services/app/Ability/DeleteAll", "OrgName", orgName);

        assertThat(response.path("result").isNull()).isTrue();
        assertThat(store.getAbility(first.id.toString())).isEmpty();
        assertThat(store.getAbility(second.id.toString())).isEmpty();
    }

    private Ability findOne(String marker) {
        FindAbilityRequest request = new FindAbilityRequest();
        request.filter = marker;
        request.maxResultCount = 10;
        return store.findAbilities(request).items.stream()
                .filter(ability -> marker.equals(ability.testItem))
                .findFirst()
                .orElseThrow();
    }

    private Map<String, Object> abilityPayload(String marker, String methodName) {
        return Map.ofEntries(
                entry("orgId", 2),
                entry("typeName", "矿石"),
                entry("samplingName", marker),
                entry("testItem", marker),
                entry("methodName", methodName),
                entry("methodEngName", "Gravimetric method"),
                entry("standardNo", marker),
                entry("cycleWorkingDay", "5"),
                entry("massRequired", "100"),
                entry("sizeRequired", "0.074"),
                entry("detectionLimit", "0.01%"),
                entry("price", "100")
        );
    }

    private Ability ability(String marker, String methodName) {
        return objectMapper.convertValue(abilityPayload(marker, methodName), Ability.class);
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

    private JsonNode deleteAbp(String url, String paramName, String paramValue) throws Exception {
        String body = mockMvc.perform(delete(url)
                        .header("Authorization", "Bearer " + adminToken())
                        .param(paramName, paramValue))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isTrue();
        return response;
    }

    private String adminToken() {
        return authService.authenticate("admin", "123qwe").orElseThrow().token();
    }
}
