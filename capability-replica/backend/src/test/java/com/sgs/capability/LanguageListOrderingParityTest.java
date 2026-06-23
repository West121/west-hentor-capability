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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/language-list-ordering-store.json")
@AutoConfigureMockMvc
class LanguageListOrderingParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/language-list-ordering-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset language list ordering parity test store", ex);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthService authService;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void getLanguagesSortsItemsByDisplayNameLikeOriginalService() throws Exception {
        JsonNode response = getAbp("/api/services/app/Language/GetLanguages");

        JsonNode result = response.path("result");
        assertThat(response.path("success").asBoolean()).isTrue();
        assertThat(result.path("defaultLanguageName").asText()).isEqualTo("zh-Hans");
        assertThat(result.path("languages")).hasSize(2);
        assertThat(result.path("languages").get(0).path("displayName").asText()).isEqualTo("English");
        assertThat(result.path("languages").get(1).path("displayName").asText()).isEqualTo("简体中文");
    }

    private JsonNode getAbp(String url) throws Exception {
        String body = mockMvc.perform(get(url)
                        .header("Authorization", "Bearer " + adminToken()))
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
