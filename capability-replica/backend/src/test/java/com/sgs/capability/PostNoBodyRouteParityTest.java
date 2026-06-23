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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/post-no-body-route-parity-store.json")
@AutoConfigureMockMvc
class PostNoBodyRouteParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/post-no-body-route-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset POST no-body route parity test store", ex);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthService authService;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void copiedReadRoutesAcceptOriginalPostWithoutBody() throws Exception {
        assertThat(postAbp("/api/services/app/Account/BackToImpersonator").path("result").path("impersonationToken").asText())
                .isNotBlank();
        assertThat(postAbp("/api/services/app/Ability/FindAllAblibities").path("result").isArray()).isTrue();
        assertThat(postAbp("/api/services/app/AbilityProperty/AbilityPropertyList").path("result").isArray()).isTrue();

        JsonNode statistics = postAbp("/api/services/app/Dashboard/Statistics").path("result");
        assertThat(statistics.path("abilityCount").isNumber()).isTrue();

        assertThat(postAbp("/api/services/app/Dashboard/OrgCount").path("result").path("items").isArray()).isTrue();
        assertThat(postAbp("/api/services/app/Dashboard/ChangeCountInWeek").path("result").path("items").isArray()).isTrue();
        assertThat(postAbp("/api/services/app/Install/CheckDatabase").path("result").path("isDatabaseExist").isBoolean()).isTrue();
        assertThat(postAbp("/api/services/app/Laboratory/List").path("result").path("list").isArray()).isTrue();
        assertThat(postAbp("/api/services/app/WebLog/DownloadWebLogs").path("result").path("fileName").asText())
                .isEqualTo("WebSiteLogs.zip");
    }

    private JsonNode postAbp(String url) throws Exception {
        String body = mockMvc.perform(post(url).header("Authorization", "Bearer " + adminToken()))
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
