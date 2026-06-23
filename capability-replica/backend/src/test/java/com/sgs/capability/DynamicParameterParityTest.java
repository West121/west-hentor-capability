package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.model.DynamicParameterItem;
import com.sgs.capability.model.DynamicParameterValueItem;
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

@SpringBootTest(properties = "replica.store.path=target/test-data/dynamic-parameter-parity-store.json")
@AutoConfigureMockMvc
class DynamicParameterParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/dynamic-parameter-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset dynamic parameter parity test store", ex);
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
    void addDynamicParameterKeepsOriginalVoidResponseWhileSavingParameter() throws Exception {
        String parameterName = "VoidDynamic" + System.nanoTime();
        JsonNode response = postAbp("/api/services/app/DynamicParameter/Add", Map.of(
                "parameterName", parameterName,
                "displayName", "Void Dynamic Parameter",
                "inputType", "SINGLE_LINE_STRING",
                "permission", ""
        ));

        assertThat(response.path("result").isNull()).isTrue();
        assertThat(store.dynamicParameters()).anySatisfy(parameter -> {
            assertThat(parameter.parameterName).isEqualTo(parameterName);
            assertThat(parameter.displayName).isEqualTo("Void Dynamic Parameter");
        });
    }

    @Test
    void updateDynamicParameterKeepsOriginalVoidResponseWhileSavingParameter() throws Exception {
        DynamicParameterItem existing = dynamicParameter("UpdateDynamic" + System.nanoTime());
        existing.displayName = "Updated Dynamic Parameter";
        existing.inputType = "COMBOBOX";

        JsonNode response = postAbp("/api/services/app/DynamicParameter/Update", existing);

        assertThat(response.path("result").isNull()).isTrue();
        assertThat(store.dynamicParameter(existing.id)).hasValueSatisfying(parameter -> {
            assertThat(parameter.displayName).isEqualTo("Updated Dynamic Parameter");
            assertThat(parameter.inputType).isEqualTo("COMBOBOX");
        });
    }

    @Test
    void deleteDynamicParameterAcceptsOriginalDeleteRequestWithQueryId() throws Exception {
        DynamicParameterItem existing = dynamicParameter("DeleteDynamic" + System.nanoTime());

        JsonNode response = deleteAbp("/api/services/app/DynamicParameter/Delete", existing.id);

        assertThat(response.path("result").isNull()).isTrue();
        assertThat(store.dynamicParameter(existing.id)).isEmpty();
    }

    @Test
    void addDynamicParameterValueKeepsOriginalVoidResponseWhileSavingValue() throws Exception {
        DynamicParameterItem parameter = dynamicParameter("ValueAddDynamic" + System.nanoTime());

        JsonNode response = postAbp("/api/services/app/DynamicParameterValue/Add", Map.of(
                "dynamicParameterId", parameter.id,
                "value", "North"
        ));

        assertThat(response.path("result").isNull()).isTrue();
        assertThat(store.dynamicParameterValues(parameter.id)).anySatisfy(value -> {
            assertThat(value.dynamicParameterId).isEqualTo(parameter.id);
            assertThat(value.parameterName).isEqualTo(parameter.parameterName);
            assertThat(value.value).isEqualTo("North");
        });
    }

    @Test
    void updateDynamicParameterValueKeepsOriginalVoidResponseWhileSavingValue() throws Exception {
        DynamicParameterItem parameter = dynamicParameter("ValueUpdateDynamic" + System.nanoTime());
        DynamicParameterValueItem existing = dynamicParameterValue(parameter.id, "Before");
        existing.value = "After";

        JsonNode response = postAbp("/api/services/app/DynamicParameterValue/Update", existing);

        assertThat(response.path("result").isNull()).isTrue();
        assertThat(store.dynamicParameterValue(existing.id)).hasValueSatisfying(value -> {
            assertThat(value.parameterName).isEqualTo(parameter.parameterName);
            assertThat(value.value).isEqualTo("After");
        });
    }

    @Test
    void deleteDynamicParameterValueAcceptsOriginalDeleteRequestWithQueryId() throws Exception {
        DynamicParameterItem parameter = dynamicParameter("ValueDeleteDynamic" + System.nanoTime());
        DynamicParameterValueItem existing = dynamicParameterValue(parameter.id, "Before");

        JsonNode response = deleteAbp("/api/services/app/DynamicParameterValue/Delete", existing.id);

        assertThat(response.path("result").isNull()).isTrue();
        assertThat(store.dynamicParameterValue(existing.id)).isEmpty();
    }

    private DynamicParameterItem dynamicParameter(String parameterName) {
        DynamicParameterItem item = new DynamicParameterItem();
        item.parameterName = parameterName;
        item.displayName = parameterName;
        item.inputType = "SINGLE_LINE_STRING";
        item.permission = "";
        return store.saveDynamicParameter(item);
    }

    private DynamicParameterValueItem dynamicParameterValue(Integer dynamicParameterId, String value) {
        DynamicParameterValueItem item = new DynamicParameterValueItem();
        item.dynamicParameterId = dynamicParameterId;
        item.value = value;
        return store.saveDynamicParameterValue(item);
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

    private String adminToken() {
        return authService.authenticate("admin", "123qwe").orElseThrow().token();
    }
}
