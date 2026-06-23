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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/notification-settings-parity-store.json")
@AutoConfigureMockMvc
class NotificationSettingsParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/notification-settings-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset notification settings parity test store", ex);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthService authService;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void getNotificationSettingsUsesOriginalOutputShapeWithoutLocalChannelToggles() throws Exception {
        JsonNode settings = getAbp("/api/services/app/Notification/GetNotificationSettings").path("result");

        assertThat(settings.has("receiveNotifications")).isTrue();
        assertThat(settings.has("notifications")).isTrue();
        assertThat(settings.has("userId")).isFalse();
        assertThat(settings.has("desktopNotifications")).isFalse();
        assertThat(settings.has("emailNotifications")).isFalse();
        assertThat(settings.has("smsNotifications")).isFalse();
    }

    @Test
    void updateNotificationSettingsKeepsOriginalVoidResponseWhileSavingSubscriptionSettings() throws Exception {
        Map<String, Object> input = Map.of(
                "receiveNotifications", false,
                "notifications", List.of(Map.of(
                        "name", "Capability.AbilityChanged",
                        "displayName", "能力表变更",
                        "isSubscribed", false
                ))
        );

        JsonNode response = postAbp("/api/services/app/Notification/UpdateNotificationSettings", input);

        assertThat(response.path("result").isNull()).isTrue();
        JsonNode settings = getAbp("/api/services/app/Notification/GetNotificationSettings").path("result");
        assertThat(settings.path("receiveNotifications").asBoolean()).isFalse();
        assertThat(settings.has("desktopNotifications")).isFalse();
        assertThat(settings.has("emailNotifications")).isFalse();
        assertThat(settings.has("smsNotifications")).isFalse();
        assertThat(settings.path("notifications").get(0).path("name").asText()).isEqualTo("Capability.AbilityChanged");
        assertThat(settings.path("notifications").get(0).path("isSubscribed").asBoolean()).isFalse();
    }

    @Test
    void updateNotificationSettingsRejectsOriginalSubscriptionDtoValidationViolations() throws Exception {
        JsonNode missingName = postRaw("/api/services/app/Notification/UpdateNotificationSettings", Map.of(
                "receiveNotifications", true,
                "notifications", List.of(Map.of("isSubscribed", true))
        ));

        assertValidationFailure(missingName);

        JsonNode longName = postRaw("/api/services/app/Notification/UpdateNotificationSettings", Map.of(
                "receiveNotifications", true,
                "notifications", List.of(Map.of(
                        "name", "n".repeat(97),
                        "isSubscribed", true
                ))
        ));

        assertValidationFailure(longName);
    }

    private JsonNode postAbp(String url, Object payload) throws Exception {
        JsonNode response = postRaw(url, payload);
        assertThat(response.path("success").asBoolean()).isTrue();
        return response;
    }

    private JsonNode postRaw(String url, Object payload) throws Exception {
        String body = mockMvc.perform(post(url)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(payload)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode response = objectMapper.readTree(body);
        return response;
    }

    private void assertValidationFailure(JsonNode response) {
        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText()).isEqualTo("Validation failed");
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

    private String adminToken() {
        return authService.authenticate("admin", "123qwe").orElseThrow().token();
    }
}
