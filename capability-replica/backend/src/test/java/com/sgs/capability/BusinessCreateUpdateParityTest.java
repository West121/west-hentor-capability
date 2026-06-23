package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.model.SampleType;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/business-create-update-parity-store.json")
@AutoConfigureMockMvc
class BusinessCreateUpdateParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/business-create-update-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset business create/update parity test store", ex);
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
    void laboratoryCreateOrUpdateRejectsDuplicateCodeWithOriginalMessage() throws Exception {
        String code = "DUP-LAB-" + System.nanoTime();
        assertOk(postAbp("/api/services/app/Laboratory/CreateOrUpdate", Map.of(
                "code", code,
                "name", "Duplicate Lab One",
                "leader", "Parity",
                "contactInfo", "13800000000",
                "address", "Local replica"
        )));

        JsonNode response = postAbp("/api/services/app/Laboratory/CreateOrUpdate", Map.of(
                "code", code,
                "name", "Duplicate Lab Two",
                "leader", "Parity",
                "contactInfo", "13800000000",
                "address", "Local replica"
        ));

        assertFailed(response, code + "已存在");
    }

    @Test
    void laboratoryCreateOrUpdateRejectsOriginalRequiredFieldViolations() throws Exception {
        assertFailed(postAbp("/api/services/app/Laboratory/CreateOrUpdate", Map.of(
                "name", "Missing code lab"
        )), "Validation failed");

        assertFailed(postAbp("/api/services/app/Laboratory/CreateOrUpdate", Map.of(
                "code", "MISSING-NAME-" + System.nanoTime()
        )), "Validation failed");
    }

    @Test
    void sampleTypeCreateOrUpdateRejectsDuplicateNameWithOriginalMessage() throws Exception {
        String name = "重复样品类型" + System.nanoTime();
        assertOk(postAbp("/api/services/app/SampleType/CreateOrUpdate", Map.of(
                "displayName", name,
                "orgId", 2
        )));

        JsonNode response = postAbp("/api/services/app/SampleType/CreateOrUpdate", Map.of(
                "displayName", name,
                "orgId", 2
        ));

        assertFailed(response, name + "已存在");
    }

    @Test
    void sampleCreateOrUpdateRejectsDuplicateNameWithOriginalMessage() throws Exception {
        SampleType type = sampleType("重复样品所属类型" + System.nanoTime());
        String name = "重复样品" + System.nanoTime();
        assertOk(postAbp("/api/services/app/Sample/CreateOrUpdate", Map.of(
                "displayName", name,
                "engName", name,
                "alias", name,
                "typeId", type.id.toString()
        )));

        JsonNode response = postAbp("/api/services/app/Sample/CreateOrUpdate", Map.of(
                "displayName", name,
                "engName", name,
                "alias", name,
                "typeId", type.id.toString()
        ));

        assertFailed(response, name + "已存在");
    }

    private SampleType sampleType(String displayName) {
        SampleType type = new SampleType();
        type.displayName = displayName;
        type.orgId = 2L;
        return store.saveSampleType(type);
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
        return objectMapper.readTree(body);
    }

    private void assertOk(JsonNode response) {
        assertThat(response.path("success").asBoolean()).isTrue();
    }

    private void assertFailed(JsonNode response, String message) {
        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText()).isEqualTo(message);
    }

    private String adminToken() {
        return authService.authenticate("admin", "123qwe").orElseThrow().token();
    }
}
