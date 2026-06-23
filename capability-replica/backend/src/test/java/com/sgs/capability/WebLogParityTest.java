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
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "replica.store.path=target/test-data/weblog-parity-store.json",
        "replica.logs.path=target/test-data/weblog-empty-logs"
})
@AutoConfigureMockMvc
class WebLogParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/weblog-parity-store.json"));
            deleteTree(Path.of("target/test-data/weblog-empty-logs"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset web log parity test data", ex);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthService authService;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void latestWebLogsReturnsEmptyLinesWhenLogFolderDoesNotExist() throws Exception {
        JsonNode lines = getAbp("/api/services/app/WebLog/GetLatestWebLogs")
                .path("result")
                .path("latestWebLogLines");

        assertThat(lines).isEmpty();
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

    private static void deleteTree(Path path) throws Exception {
        if (!Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            for (Path item : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(item);
            }
        }
    }
}
