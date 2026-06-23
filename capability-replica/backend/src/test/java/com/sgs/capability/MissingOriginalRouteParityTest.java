package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.security.AuthService;
import com.sgs.capability.service.CapabilityStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/missing-original-route-parity-store.json")
@AutoConfigureMockMvc
class MissingOriginalRouteParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/missing-original-route-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset missing original route parity test store", ex);
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
    void tokenAuthTestNotificationAcceptsOriginalGetQueryContract() throws Exception {
        String message = "Original test notification " + System.nanoTime();

        JsonNode response = abp(get("/api/TokenAuth/TestNotification")
                .param("message", message)
                .param("severity", "Warn"));

        assertThat(response.path("result").isNull()).isTrue();
        assertThat(store.userNotifications(1L, message, "ALL", 0, 10).items).anySatisfy(notification -> {
            assertThat(notification.notificationName).isEqualTo("App.SimpleMessage");
            assertThat(notification.message).isEqualTo(message);
            assertThat(notification.severity).isEqualTo("Warn");
            assertThat(notification.readState).isZero();
        });
    }

    private JsonNode abp(MockHttpServletRequestBuilder request) throws Exception {
        String body = mockMvc.perform(request.header("Authorization", "Bearer " + adminToken()))
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
