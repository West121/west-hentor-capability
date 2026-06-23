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
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/session-tenant-context-parity-store.json")
@AutoConfigureMockMvc
class SessionTenantContextParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/session-tenant-context-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset session tenant context parity test store", ex);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthService authService;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void impersonatedAuthenticateCarriesTargetTenantIntoSessionAndSignInToken() throws Exception {
        String impersonationToken = authService.createImpersonationToken(1L, 2);
        String accessToken = getAbp("/api/TokenAuth/ImpersonatedAuthenticate?impersonationToken=" + impersonationToken)
                .path("result")
                .path("accessToken")
                .asText();

        JsonNode session = getAbp("/api/services/app/Session/GetCurrentLoginInformations", accessToken).path("result");
        JsonNode signInToken = postAbp("/api/services/app/Session/UpdateUserSignInToken", accessToken).path("result");

        assertThat(session.path("tenant").path("id").asInt()).isEqualTo(2);
        assertThat(session.path("tenant").path("tenancyName").asText()).isEqualTo("trial");
        assertThat(signInToken.path("encodedTenantId").asText()).isEqualTo(base64("2"));
    }

    private JsonNode getAbp(String url) throws Exception {
        String body = mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isTrue();
        return response;
    }

    private JsonNode getAbp(String url, String accessToken) throws Exception {
        String body = mockMvc.perform(get(url).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isTrue();
        return response;
    }

    private JsonNode postAbp(String url, String accessToken) throws Exception {
        String body = mockMvc.perform(post(url).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isTrue();
        return response;
    }

    private String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
