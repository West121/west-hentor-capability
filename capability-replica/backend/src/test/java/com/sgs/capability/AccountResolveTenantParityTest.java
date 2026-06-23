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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/account-resolve-tenant-parity-store.json")
@AutoConfigureMockMvc
class AccountResolveTenantParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/account-resolve-tenant-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset account resolve tenant parity test store", ex);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthService authService;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void blankEncryptedParameterReturnsCurrentSessionTenantIdLikeOriginalAccountAppService() throws Exception {
        JsonNode response = postResolveTenantId(Map.of("c", ""));

        assertThat(response.path("success").asBoolean()).isTrue();
        assertThat(response.path("result").asInt()).isEqualTo(1);
    }

    private JsonNode postResolveTenantId(Object payload) throws Exception {
        String body = mockMvc.perform(post("/api/services/app/Account/ResolveTenantId")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(payload)))
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
