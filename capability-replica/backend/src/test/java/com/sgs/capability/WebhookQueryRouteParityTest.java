package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.model.WebhookSendAttemptItem;
import com.sgs.capability.model.WebhookSubscriptionItem;
import com.sgs.capability.security.AuthService;
import com.sgs.capability.service.CapabilityStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/webhook-query-route-parity-store.json")
@AutoConfigureMockMvc
class WebhookQueryRouteParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/webhook-query-route-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset webhook query route parity test store", ex);
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
    void subscriptionRoutesAcceptOriginalQueryContracts() throws Exception {
        WebhookSubscriptionItem subscription = webhookSubscription();

        JsonNode detail = getAbp(get("/api/services/app/WebhookSubscription/GetSubscription")
                .param("subscriptionId", subscription.id.toString())).path("result");
        assertThat(detail.path("id").asText()).isEqualTo(subscription.id.toString());

        JsonNode subscribed = getAbp(post("/api/services/app/WebhookSubscription/IsSubscribed")
                .param("webhookName", "App.TestWebhook")).path("result");
        assertThat(subscribed.asBoolean()).isTrue();

        JsonNode filtered = getAbp(get("/api/services/app/WebhookSubscription/GetAllSubscriptionsIfFeaturesGranted")
                .param("webhookName", "App.TestWebhook")).path("result").path("items");
        assertThat(filtered).anySatisfy(item -> assertThat(item.path("id").asText()).isEqualTo(subscription.id.toString()));
    }

    @Test
    void sendAttemptRoutesAcceptOriginalGetQueryContracts() throws Exception {
        WebhookSubscriptionItem subscription = webhookSubscription();
        store.publishTestWebhook();
        WebhookSendAttemptItem attempt = store.webhookSendAttempts().stream()
                .filter(item -> subscription.id.equals(item.webhookSubscriptionId))
                .findFirst()
                .orElseThrow();

        JsonNode attempts = getAbp(get("/api/services/app/WebhookSendAttempt/GetAllSendAttempts")
                .param("SubscriptionId", subscription.id.toString())
                .param("MaxResultCount", "1")
                .param("SkipCount", "0")).path("result");
        assertThat(attempts.path("totalCount").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(attempts.path("items")).hasSize(1);

        JsonNode eventAttempts = getAbp(get("/api/services/app/WebhookSendAttempt/GetAllSendAttemptsOfWebhookEvent")
                .param("Id", attempt.webhookEventId.toString())).path("result").path("items");
        assertThat(eventAttempts).anySatisfy(item -> assertThat(item.path("id").asText()).isEqualTo(attempt.id.toString()));

        int retryCountBeforeResend = attempt.retryCount;
        getAbp(post("/api/services/app/WebhookSendAttempt/Resend")
                .param("sendAttemptId", attempt.id.toString()));
        assertThat(store.webhookSendAttempts().stream()
                .filter(item -> attempt.id.equals(item.id))
                .findFirst()
                .orElseThrow()
                .retryCount).isEqualTo(retryCountBeforeResend + 1);
    }

    @Test
    void sendAttemptsRejectOriginalPagedInputRangeViolations() throws Exception {
        assertValidationFailure(raw(get("/api/services/app/WebhookSendAttempt/GetAllSendAttempts")
                .param("SkipCount", "0")
                .param("MaxResultCount", "0")));

        assertValidationFailure(raw(post("/api/services/app/WebhookSendAttempt/GetAllSendAttempts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of(
                        "skipCount", 0,
                        "maxResultCount", 1001
                )))));

        assertValidationFailure(raw(get("/api/services/app/WebhookSendAttempt/GetAllSendAttempts")
                .param("SkipCount", "-1")
                .param("MaxResultCount", "10")));
    }

    private WebhookSubscriptionItem webhookSubscription() {
        WebhookSubscriptionItem item = new WebhookSubscriptionItem();
        item.webhookUri = "https://example.local/webhook/query-" + System.nanoTime();
        item.isActive = true;
        item.webhooks = List.of("App.TestWebhook");
        item.headers = new LinkedHashMap<>();
        return store.saveWebhookSubscription(item);
    }

    private JsonNode getAbp(MockHttpServletRequestBuilder request) throws Exception {
        String body = mockMvc.perform(request.header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isTrue();
        return response;
    }

    private void assertValidationFailure(JsonNode response) {
        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText()).isEqualTo("Validation failed");
    }

    private JsonNode raw(MockHttpServletRequestBuilder request) throws Exception {
        String body = mockMvc.perform(request.header("Authorization", "Bearer " + adminToken()))
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
