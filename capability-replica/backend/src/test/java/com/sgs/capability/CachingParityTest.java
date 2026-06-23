package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.security.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/caching-parity-store.json")
@AutoConfigureMockMvc
class CachingParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/caching-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset caching parity test store", ex);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthService authService;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void getAllCachesReturnsOriginalCacheDtoNameOnlyShape() throws Exception {
        JsonNode items = getAbp("/api/services/app/Caching/GetAllCaches")
                .path("result")
                .path("items");

        assertThat(items).isNotEmpty();
        List<String> fields = new ArrayList<>();
        items.get(0).fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactly("name");
        assertThat(items.get(0).path("name").asText()).isNotBlank();
    }

    private JsonNode getAbp(String url) throws Exception {
        String body = mockMvc.perform(get(url).header("Authorization", "Bearer " + adminToken()))
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
