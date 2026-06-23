package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@SpringBootTest(properties = "replica.store.path=target/test-data/install-url-mode-store.json")
@AutoConfigureMockMvc
class InstallUrlModeParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/install-url-mode-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset install URL mode parity test store", ex);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void setupWithBlankServerUrlUsesOriginalSingleWebSiteRootAddressMode() throws Exception {
        JsonNode setup = postJson("/api/services/app/Install/Setup", Map.of(
                "connectionString", "Server=(local);Database=CapabilitySingleUrl;",
                "adminPassword", "123qwe",
                "webSiteUrl", "http://single.example/",
                "serverUrl", "",
                "defaultLanguage", "zh-Hans"
        ));
        assertThat(setup.path("success").asBoolean()).isTrue();

        JsonNode settings = postJson("/api/services/app/Install/GetAppSettingsJson", Map.of());

        JsonNode result = settings.path("result");
        assertThat(settings.path("success").asBoolean()).isTrue();
        assertThat(result.path("webSiteUrl").asText()).isEqualTo("http://single.example/");
        assertMissingOrJsonNull(result.get("serverSiteUrl"));
        assertMissingOrJsonNull(result.get("languages"));
    }

    private JsonNode postJson(String url, Object payload) throws Exception {
        String body = mockMvc.perform(post(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(payload)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    private void assertMissingOrJsonNull(JsonNode node) {
        assertThat(node == null || node.isNull()).isTrue();
    }
}
