package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.model.SubscriptionPaymentItem;
import com.sgs.capability.security.AuthService;
import com.sgs.capability.service.CapabilityStore;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/payment-has-any-parity-store.json")
@AutoConfigureMockMvc
class PaymentHasAnyPaymentParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/payment-has-any-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset payment has-any parity test store", ex);
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
    void hasAnyPaymentOnlyCountsOriginalCompletedPayments() throws Exception {
        store.subscriptionPayments().forEach(payment -> store.markPaymentStatus(payment.id, 1));
        SubscriptionPaymentItem unpaid = store.createPayment(2, 1, 30, 2, true, "ok", "error");

        assertThat(postAbp("/api/services/app/Payment/HasAnyPayment").path("result").asBoolean()).isFalse();

        store.markPaymentStatus(unpaid.id, 5);
        assertThat(postAbp("/api/services/app/Payment/HasAnyPayment").path("result").asBoolean()).isTrue();
    }

    @Test
    void hasAnyPaymentUsesCurrentTenantLikeOriginalService() throws Exception {
        store.subscriptionPayments().forEach(payment -> store.markPaymentStatus(payment.id, 1));
        SubscriptionPaymentItem tenantOnePayment = store.createPayment(1, 2, 1, 30, 2, true, "ok", "error");
        store.markPaymentStatus(tenantOnePayment.id, 5);

        assertThat(postAbpWithToken("/api/services/app/Payment/HasAnyPayment", tenantToken(2))
                .path("result").asBoolean()).isFalse();

        SubscriptionPaymentItem tenantTwoPayment = store.createPayment(2, 2, 1, 30, 2, true, "ok", "error");
        store.markPaymentStatus(tenantTwoPayment.id, 5);
        assertThat(postAbpWithToken("/api/services/app/Payment/HasAnyPayment", tenantToken(2))
                .path("result").asBoolean()).isTrue();
    }

    private JsonNode postAbp(String url) throws Exception {
        return postAbpWithToken(url, adminToken());
    }

    private JsonNode postAbpWithToken(String url, String token) throws Exception {
        String body = mockMvc.perform(post(url).header("Authorization", "Bearer " + token))
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

    private String tenantToken(int tenantId) throws Exception {
        String impersonationToken = authService.createImpersonationToken(1L, tenantId);
        String body = mockMvc.perform(get("/api/TokenAuth/ImpersonatedAuthenticate")
                        .param("impersonationToken", impersonationToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("result").path("accessToken").asText();
    }
}
