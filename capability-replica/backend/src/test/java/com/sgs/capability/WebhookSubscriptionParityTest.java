package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.model.WebhookSubscriptionItem;
import com.sgs.capability.security.AuthService;
import com.sgs.capability.service.CapabilityStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/webhook-subscription-parity-store.json")
@AutoConfigureMockMvc
class WebhookSubscriptionParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/webhook-subscription-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset webhook subscription parity test store", ex);
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
    void addSubscriptionKeepsOriginalVoidResponseWhileSavingSubscription() throws Exception {
        String webhookUri = "https://example.local/webhook/add-" + System.nanoTime();

        JsonNode response = postAbp("/api/services/app/WebhookSubscription/AddSubscription", Map.of(
                "webhookUri", webhookUri,
                "isActive", true,
                "webhooks", List.of("App.TestWebhook"),
                "headers", Map.of("X-Parity", "add")
        ));

        assertThat(response.path("result").isNull()).isTrue();
        assertThat(store.webhookSubscriptions()).anySatisfy(subscription -> {
            assertThat(subscription.webhookUri).isEqualTo(webhookUri);
            assertThat(subscription.isActive).isTrue();
            assertThat(subscription.webhooks).containsExactly("App.TestWebhook");
            assertThat(subscription.headers).containsEntry("X-Parity", "add");
        });
    }

    @Test
    void updateSubscriptionKeepsOriginalVoidResponseWhileSavingSubscription() throws Exception {
        WebhookSubscriptionItem existing = webhookSubscription("https://example.local/webhook/before-" + System.nanoTime());
        existing.webhookUri = "https://example.local/webhook/after-" + System.nanoTime();
        existing.isActive = false;
        existing.headers = new LinkedHashMap<>(Map.of("X-Parity", "update"));

        JsonNode response = postAbp("/api/services/app/WebhookSubscription/UpdateSubscription", existing);

        assertThat(response.path("result").isNull()).isTrue();
        assertThat(store.webhookSubscription(existing.id.toString())).hasValueSatisfying(subscription -> {
            assertThat(subscription.webhookUri).isEqualTo(existing.webhookUri);
            assertThat(subscription.isActive).isFalse();
            assertThat(subscription.webhooks).containsExactly("App.TestWebhook");
            assertThat(subscription.headers).containsEntry("X-Parity", "update");
        });
    }

    private WebhookSubscriptionItem webhookSubscription(String webhookUri) {
        WebhookSubscriptionItem item = new WebhookSubscriptionItem();
        item.webhookUri = webhookUri;
        item.isActive = true;
        item.webhooks = List.of("App.TestWebhook");
        item.headers = new LinkedHashMap<>();
        return store.saveWebhookSubscription(item);
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

    private String adminToken() {
        return authService.authenticate("admin", "123qwe").orElseThrow().token();
    }
}
