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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/dynamic-parameter-query-route-parity-store.json")
@AutoConfigureMockMvc
class DynamicParameterQueryRouteParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/dynamic-parameter-query-route-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset dynamic parameter query route parity test store", ex);
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
    void dynamicParameterRoutesAcceptOriginalGetAndPostQueryContracts() throws Exception {
        DynamicParameterItem parameter = dynamicParameter("RouteDynamic" + System.nanoTime());
        DynamicParameterValueItem value = dynamicParameterValue(parameter.id, "RouteValue");

        JsonNode parameterById = abp(get("/api/services/app/DynamicParameter/Get")
                .param("id", String.valueOf(parameter.id))).path("result");
        assertThat(parameterById.path("id").asInt()).isEqualTo(parameter.id);

        JsonNode inputType = abp(post("/api/services/app/DynamicParameter/FindAllowedInputType")
                .param("name", "DATE")).path("result");
        assertThat(inputType.path("name").asText()).isEqualTo("DATE");

        JsonNode valueById = abp(get("/api/services/app/DynamicParameterValue/Get")
                .param("id", String.valueOf(value.id))).path("result");
        assertThat(valueById.path("id").asInt()).isEqualTo(value.id);

        JsonNode values = abp(get("/api/services/app/DynamicParameterValue/GetAllValuesOfDynamicParameter")
                .param("Id", String.valueOf(parameter.id))).path("result").path("items");
        assertThat(values).hasSize(1);
        assertThat(values.get(0).path("dynamicParameterId").asInt()).isEqualTo(parameter.id);
    }

    @Test
    void entityDynamicParameterRoutesAcceptOriginalGetQueryContracts() throws Exception {
        DynamicParameterItem parameter = dynamicParameter("EntityRouteDynamic" + System.nanoTime());
        EntityDynamicParameterItem mapping = entityDynamicParameter("Capability.RouteEntity", parameter.id);
        EntityDynamicParameterValueItem value = entityDynamicParameterValue(mapping.id, "Capability.RouteEntity", "entity-42", "RouteEntityValue");

        JsonNode mappingById = abp(get("/api/services/app/EntityDynamicParameter/Get")
                .param("id", String.valueOf(mapping.id))).path("result");
        assertThat(mappingById.path("id").asInt()).isEqualTo(mapping.id);

        JsonNode mappings = abp(get("/api/services/app/EntityDynamicParameter/GetAllParametersOfAnEntity")
                .param("EntityFullName", "Capability.RouteEntity")).path("result").path("items");
        assertThat(mappings).hasSize(1);
        assertThat(mappings.get(0).path("entityFullName").asText()).isEqualTo("Capability.RouteEntity");

        JsonNode valueById = abp(get("/api/services/app/EntityDynamicParameterValue/Get")
                .param("id", String.valueOf(value.id))).path("result");
        assertThat(valueById.path("id").asInt()).isEqualTo(value.id);

        JsonNode valuesByParameter = abp(get("/api/services/app/EntityDynamicParameterValue/GetAll")
                .param("EntityId", "entity-42")
                .param("ParameterId", String.valueOf(mapping.id))).path("result").path("items");
        assertThat(valuesByParameter).hasSize(1);
        assertThat(valuesByParameter.get(0).path("id").asInt()).isEqualTo(value.id);

        JsonNode valuesByEntity = abp(get("/api/services/app/EntityDynamicParameterValue/GetAllEntityDynamicParameterValues")
                .param("EntityFullName", "Capability.RouteEntity")
                .param("EntityId", "entity-42")).path("result").path("items");
        assertThat(valuesByEntity).hasSize(1);
        assertThat(valuesByEntity.get(0).path("entityDynamicParameterId").asInt()).isEqualTo(mapping.id);
        assertThat(valuesByEntity.get(0).path("parameterName").asText()).isEqualTo(parameter.parameterName);
        assertThat(valuesByEntity.get(0).path("selectedValues")).extracting(JsonNode::asText).containsExactly("RouteEntityValue");
    }

    private DynamicParameterItem dynamicParameter(String parameterName) {
        DynamicParameterItem item = new DynamicParameterItem();
        item.parameterName = parameterName;
        item.displayName = parameterName;
        item.inputType = "SINGLE_LINE_STRING";
        return store.saveDynamicParameter(item);
    }

    private DynamicParameterValueItem dynamicParameterValue(Integer dynamicParameterId, String value) {
        DynamicParameterValueItem item = new DynamicParameterValueItem();
        item.dynamicParameterId = dynamicParameterId;
        item.value = value;
        return store.saveDynamicParameterValue(item);
    }

    private EntityDynamicParameterItem entityDynamicParameter(String entityFullName, Integer dynamicParameterId) {
        EntityDynamicParameterItem item = new EntityDynamicParameterItem();
        item.entityFullName = entityFullName;
        item.dynamicParameterId = dynamicParameterId;
        return store.saveEntityDynamicParameter(item);
    }

    private EntityDynamicParameterValueItem entityDynamicParameterValue(Integer entityDynamicParameterId,
                                                                        String entityFullName,
                                                                        String entityId,
                                                                        String value) {
        EntityDynamicParameterValueItem item = new EntityDynamicParameterValueItem();
        item.entityDynamicParameterId = entityDynamicParameterId;
        item.entityFullName = entityFullName;
        item.entityId = entityId;
        item.value = value;
        return store.saveEntityDynamicParameterValue(item);
    }

    private JsonNode abp(MockHttpServletRequestBuilder request) throws Exception {
        String body = mockMvc.perform(request.header("Authorization", "Bearer " + adminToken()))
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
