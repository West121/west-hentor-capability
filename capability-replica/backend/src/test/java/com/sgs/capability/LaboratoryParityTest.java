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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/laboratory-parity-store.json")
@AutoConfigureMockMvc
class LaboratoryParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/laboratory-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset laboratory parity test store", ex);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthService authService;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void createOrUpdateKeepsOriginalGuidResponseWhileSavingLaboratory() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        Map<String, Object> input = Map.of(
                "code", "LAB-" + suffix,
                "name", "实验室契约" + suffix,
                "leader", "Parity",
                "contactInfo", "13800000000",
                "address", "Local replica"
        );

        JsonNode response = postAbp("/api/services/app/Laboratory/CreateOrUpdate", input);

        assertThat(response.path("result").isTextual()).isTrue();
        String id = response.path("result").asText();
        assertThat(UUID.fromString(id).toString()).isEqualTo(id);
        JsonNode labs = getAbp("/api/services/app/Laboratory/List").path("result").path("list");
        assertThat(labs).anySatisfy(item -> {
            assertThat(item.path("id").asText()).isEqualTo(id);
            assertThat(item.path("code").asText()).isEqualTo(input.get("code"));
            assertThat(item.path("name").asText()).isEqualTo(input.get("name"));
        });
    }

    @Test
    void deleteLabAcceptsOriginalDeleteRequestWithQueryId() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        JsonNode created = postAbp("/api/services/app/Laboratory/CreateOrUpdate", Map.of(
                "code", "DLAB-" + suffix,
                "name", "删除实验室契约" + suffix,
                "leader", "Parity",
                "contactInfo", "13800000000",
                "address", "Local replica"
        ));
        String id = created.path("result").asText();

        JsonNode response = deleteAbp("/api/services/app/Laboratory/DeleteLab", "Id", id);

        assertThat(response.path("result").isNull()).isTrue();
        JsonNode labs = getAbp("/api/services/app/Laboratory/List").path("result").path("list");
        assertThat(labs).noneSatisfy(item -> assertThat(item.path("id").asText()).isEqualTo(id));
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

    private JsonNode getAbp(String url) throws Exception {
        String body = mockMvc.perform(get(url)
                        .header("Authorization", "Bearer " + adminToken()))
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
