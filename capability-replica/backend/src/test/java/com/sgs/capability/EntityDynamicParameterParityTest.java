package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.model.DynamicParameterItem;
import com.sgs.capability.model.DynamicParameterValueItem;
import com.sgs.capability.model.EntityDynamicParameterItem;
import com.sgs.capability.model.EntityDynamicParameterValueItem;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/entity-dynamic-parameter-parity-store.json")
@AutoConfigureMockMvc
class EntityDynamicParameterParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/entity-dynamic-parameter-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset entity dynamic parameter parity test store", ex);
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
    void addEntityDynamicParameterKeepsOriginalVoidResponseWhileSavingMapping() throws Exception {
        DynamicParameterItem parameter = dynamicParameter("EntityAddDynamic" + System.nanoTime());

        JsonNode response = postAbp("/api/services/app/EntityDynamicParameter/Add", Map.of(
                "entityFullName", "Capability.TestEntity",
                "dynamicParameterId", parameter.id
        ));

        assertThat(response.path("result").isNull()).isTrue();
        assertThat(store.entityDynamicParameters("Capability.TestEntity")).anySatisfy(mapping -> {
            assertThat(mapping.dynamicParameterId).isEqualTo(parameter.id);
            assertThat(mapping.parameterName).isEqualTo(parameter.parameterName);
            assertThat(mapping.displayName).isEqualTo(parameter.displayName);
        });
    }

    @Test
    void updateEntityDynamicParameterKeepsOriginalVoidResponseWhileSavingMapping() throws Exception {
        DynamicParameterItem first = dynamicParameter("EntityUpdateFirst" + System.nanoTime());
        DynamicParameterItem second = dynamicParameter("EntityUpdateSecond" + System.nanoTime());
        EntityDynamicParameterItem existing = entityDynamicParameter("Capability.UpdateEntity", first.id);
        existing.dynamicParameterId = second.id;

        JsonNode response = postAbp("/api/services/app/EntityDynamicParameter/Update", existing);

        assertThat(response.path("result").isNull()).isTrue();
        assertThat(store.entityDynamicParameter(existing.id)).hasValueSatisfying(mapping -> {
            assertThat(mapping.dynamicParameterId).isEqualTo(second.id);
            assertThat(mapping.parameterName).isEqualTo(second.parameterName);
            assertThat(mapping.displayName).isEqualTo(second.displayName);
        });
    }

    @Test
    void deleteEntityDynamicParameterAcceptsOriginalDeleteRequestWithQueryId() throws Exception {
        DynamicParameterItem parameter = dynamicParameter("EntityDeleteDynamic" + System.nanoTime());
        EntityDynamicParameterItem existing = entityDynamicParameter("Capability.DeleteEntity", parameter.id);

        JsonNode response = deleteAbp("/api/services/app/EntityDynamicParameter/Delete", existing.id);

        assertThat(response.path("result").isNull()).isTrue();
        assertThat(store.entityDynamicParameter(existing.id)).isEmpty();
    }

    @Test
    void addEntityDynamicParameterValueKeepsOriginalVoidResponseWhileSavingValue() throws Exception {
        DynamicParameterItem parameter = dynamicParameter("EntityValueAdd" + System.nanoTime());
        EntityDynamicParameterItem mapping = entityDynamicParameter("Capability.ValueEntity", parameter.id);

        JsonNode response = postAbp("/api/services/app/EntityDynamicParameterValue/Add", Map.of(
                "entityDynamicParameterId", mapping.id,
                "entityId", "entity-1",
                "value", "A"
        ));

        assertThat(response.path("result").isNull()).isTrue();
        assertThat(store.entityDynamicParameterValues(mapping.id, "entity-1")).anySatisfy(value -> {
            assertThat(value.entityFullName).isEqualTo("Capability.ValueEntity");
            assertThat(value.dynamicParameterId).isEqualTo(parameter.id);
            assertThat(value.parameterName).isEqualTo(parameter.parameterName);
            assertThat(value.value).isEqualTo("A");
        });
    }

    @Test
    void updateEntityDynamicParameterValueKeepsOriginalVoidResponseWhileSavingValue() throws Exception {
        DynamicParameterItem parameter = dynamicParameter("EntityValueUpdate" + System.nanoTime());
        EntityDynamicParameterItem mapping = entityDynamicParameter("Capability.ValueUpdateEntity", parameter.id);
        EntityDynamicParameterValueItem existing = entityDynamicParameterValue(mapping.id, "entity-2", "Before");
        existing.value = "After";

        JsonNode response = postAbp("/api/services/app/EntityDynamicParameterValue/Update", existing);

        assertThat(response.path("result").isNull()).isTrue();
        assertThat(store.entityDynamicParameterValue(existing.id)).hasValueSatisfying(value -> {
            assertThat(value.entityFullName).isEqualTo("Capability.ValueUpdateEntity");
            assertThat(value.parameterName).isEqualTo(parameter.parameterName);
            assertThat(value.value).isEqualTo("After");
        });
    }

    @Test
    void deleteEntityDynamicParameterValueAcceptsOriginalDeleteRequestWithQueryId() throws Exception {
        DynamicParameterItem parameter = dynamicParameter("EntityValueDelete" + System.nanoTime());
        EntityDynamicParameterItem mapping = entityDynamicParameter("Capability.ValueDeleteEntity", parameter.id);
        EntityDynamicParameterValueItem existing = entityDynamicParameterValue(mapping.id, "entity-3", "Before");

        JsonNode response = deleteAbp("/api/services/app/EntityDynamicParameterValue/Delete", existing.id);

        assertThat(response.path("result").isNull()).isTrue();
        assertThat(store.entityDynamicParameterValue(existing.id)).isEmpty();
    }

    @Test
    void getAllEntityDynamicParameterValuesRejectsOriginalRequiredFieldViolations() throws Exception {
        assertValidationFailure(getJson("/api/services/app/EntityDynamicParameterValue/GetAllEntityDynamicParameterValues",
                Map.of("EntityId", "entity-1")));
        assertValidationFailure(getJson("/api/services/app/EntityDynamicParameterValue/GetAllEntityDynamicParameterValues",
                Map.of("EntityFullName", "Capability.RequiredEntity")));

        assertValidationFailure(postJson("/api/services/app/EntityDynamicParameterValue/GetAllEntityDynamicParameterValues",
                Map.of("entityId", "entity-1")));
        assertValidationFailure(postJson("/api/services/app/EntityDynamicParameterValue/GetAllEntityDynamicParameterValues",
                Map.of("entityFullName", "Capability.RequiredEntity")));
    }

    @Test
    void insertOrUpdateAllValuesKeepsOriginalVoidResponseWhileReplacingValues() throws Exception {
        DynamicParameterItem parameter = dynamicParameter("EntityBulkValue" + System.nanoTime());
        EntityDynamicParameterItem mapping = entityDynamicParameter("Capability.BulkEntity", parameter.id);
        entityDynamicParameterValue(mapping.id, "entity-bulk", "Old");
        entityDynamicParameterValue(mapping.id, "entity-bulk", "Stale");

        JsonNode response = postAbp("/api/services/app/EntityDynamicParameterValue/InsertOrUpdateAllValues", Map.of(
                "items", List.of(Map.of(
                        "entityDynamicParameterId", mapping.id,
                        "entityId", "entity-bulk",
                        "values", List.of("A", "B")
                ))
        ));

        assertThat(response.path("result").isNull()).isTrue();
        assertThat(store.entityDynamicParameterValues(mapping.id, "entity-bulk"))
                .extracting(value -> value.value)
                .containsExactly("A", "B");
    }

    @Test
    void cleanValuesUsesOriginalEntityDynamicParameterIdAndEntityIdScope() throws Exception {
        DynamicParameterItem first = dynamicParameter("EntityCleanFirst" + System.nanoTime());
        DynamicParameterItem second = dynamicParameter("EntityCleanSecond" + System.nanoTime());
        EntityDynamicParameterItem firstMapping = entityDynamicParameter("Capability.CleanEntity", first.id);
        EntityDynamicParameterItem secondMapping = entityDynamicParameter("Capability.CleanEntity", second.id);
        entityDynamicParameterValue(firstMapping.id, "entity-clean", "Remove");
        entityDynamicParameterValue(secondMapping.id, "entity-clean", "Keep");

        JsonNode response = postAbp("/api/services/app/EntityDynamicParameterValue/CleanValues", Map.of(
                "entityDynamicParameterId", firstMapping.id,
                "entityId", "entity-clean"
        ));

        assertThat(response.path("result").isNull()).isTrue();
        assertThat(store.entityDynamicParameterValues(firstMapping.id, "entity-clean")).isEmpty();
        assertThat(store.entityDynamicParameterValues(secondMapping.id, "entity-clean"))
                .extracting(value -> value.value)
                .containsExactly("Keep");
    }

    @Test
    void getAllEntityDynamicParameterValuesReturnsOriginalOutputItemsWithOptionsAndSelectedValues() throws Exception {
        DynamicParameterItem parameter = dynamicParameter("EntityOutputValue" + System.nanoTime());
        parameter.inputType = "COMBOBOX";
        store.saveDynamicParameter(parameter);
        dynamicParameterValue(parameter.id, "Allowed-A");
        dynamicParameterValue(parameter.id, "Allowed-B");
        EntityDynamicParameterItem mapping = entityDynamicParameter("Capability.OutputEntity", parameter.id);
        entityDynamicParameterValue(mapping.id, "entity-output", "Allowed-B");

        JsonNode response = getJson("/api/services/app/EntityDynamicParameterValue/GetAllEntityDynamicParameterValues",
                Map.of("EntityFullName", "Capability.OutputEntity", "EntityId", "entity-output"));

        assertThat(response.path("success").asBoolean()).isTrue();
        JsonNode item = response.path("result").path("items").get(0);
        assertThat(item.path("entityDynamicParameterId").asInt()).isEqualTo(mapping.id);
        assertThat(item.path("parameterName").asText()).isEqualTo(parameter.parameterName);
        assertThat(item.path("inputType").path("name").asText()).isEqualTo("COMBOBOX");
        assertThat(item.path("selectedValues")).extracting(JsonNode::asText).containsExactly("Allowed-B");
        assertThat(item.path("allValuesInputTypeHas")).extracting(JsonNode::asText).containsExactly("Allowed-A", "Allowed-B");
    }

    private DynamicParameterItem dynamicParameter(String parameterName) {
        DynamicParameterItem item = new DynamicParameterItem();
        item.parameterName = parameterName;
        item.displayName = parameterName + " Display";
        item.inputType = "SINGLE_LINE_STRING";
        item.permission = "";
        return store.saveDynamicParameter(item);
    }

    private EntityDynamicParameterItem entityDynamicParameter(String entityFullName, Integer dynamicParameterId) {
        EntityDynamicParameterItem item = new EntityDynamicParameterItem();
        item.entityFullName = entityFullName;
        item.dynamicParameterId = dynamicParameterId;
        return store.saveEntityDynamicParameter(item);
    }

    private DynamicParameterValueItem dynamicParameterValue(Integer dynamicParameterId, String value) {
        DynamicParameterValueItem item = new DynamicParameterValueItem();
        item.dynamicParameterId = dynamicParameterId;
        item.value = value;
        return store.saveDynamicParameterValue(item);
    }

    private EntityDynamicParameterValueItem entityDynamicParameterValue(Integer entityDynamicParameterId,
                                                                        String entityId,
                                                                        String value) {
        EntityDynamicParameterValueItem item = new EntityDynamicParameterValueItem();
        item.entityDynamicParameterId = entityDynamicParameterId;
        item.entityId = entityId;
        item.value = value;
        return store.saveEntityDynamicParameterValue(item);
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

    private JsonNode deleteAbp(String url, Integer id) throws Exception {
        String body = mockMvc.perform(delete(url)
                        .header("Authorization", "Bearer " + adminToken())
                        .param("id", String.valueOf(id)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isTrue();
        return response;
    }

    private JsonNode getJson(String url, Map<String, String> params) throws Exception {
        var request = get(url).header("Authorization", "Bearer " + adminToken());
        params.forEach(request::param);
        String body = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
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
