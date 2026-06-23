package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.model.EditionItem;
import com.sgs.capability.model.SubscriptionPaymentItem;
import com.sgs.capability.model.TenantItem;
import com.sgs.capability.security.AuthService;
import com.sgs.capability.service.CapabilityStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "replica.store.path=target/test-data/payment-get-route-parity-store.json",
        "payment.stripe.is-active=true",
        "payment.paypal.is-active=true"
})
@AutoConfigureMockMvc
class PaymentGetRouteParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/payment-get-route-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset payment GET route parity test store", ex);
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
    void paymentInfoAcceptsOriginalGetQueryUpgradeEditionId() throws Exception {
        JsonNode result = getAbp(get("/api/services/app/Payment/GetPaymentInfo")
                .param("UpgradeEditionId", "3")).path("result");

        assertThat(result.path("edition").path("id").asInt()).isEqualTo(3);
        assertThat(result.path("additionalPrice").asText()).isNotBlank();
    }

    @Test
    void paymentInfoRejectsTenantWithoutEditionLikeOriginalService() throws Exception {
        Integer originalEditionId = store.tenant(1).map(tenant -> tenant.editionId).orElse(null);
        setTenantEdition(null);
        try {
            JsonNode response = getRaw(get("/api/services/app/Payment/GetPaymentInfo")
                    .param("UpgradeEditionId", "3"));

            assertFailed(response, "Tenant edition is not assigned");
        } finally {
            setTenantEdition(originalEditionId);
        }
    }

    @Test
    void paymentHistoryAndGatewaysAcceptOriginalGetQueryParameters() throws Exception {
        SubscriptionPaymentItem first = store.createPayment(2, 1, 30, 2, true, "ok", "error");
        store.createPayment(3, 1, 365, 1, false, "ok", "error");

        JsonNode history = getAbp(get("/api/services/app/Payment/GetPaymentHistory")
                .param("Sorting", "creationTime DESC")
                .param("MaxResultCount", "1")
                .param("SkipCount", "0")).path("result");
        assertThat(history.path("totalCount").asInt()).isGreaterThanOrEqualTo(2);
        assertThat(history.path("items")).hasSize(1);

        JsonNode gateways = getAbp(get("/api/services/app/Payment/GetActiveGateways")
                .param("RecurringPaymentsEnabled", "true")).path("result");
        assertThat(gateways).hasSize(1);
        assertThat(gateways.get(0).path("gatewayType").asInt()).isEqualTo(2);
        assertThat(gateways.get(0).has("name")).isFalse();

        assertThat(first.id).isNotNull();
    }

    @Test
    void paymentHistoryUsesCurrentTenantLikeOriginalService() throws Exception {
        store.createPayment(1, 2, 1, 30, 2, true, "ok", "error");
        SubscriptionPaymentItem tenantTwoPayment = store.createPayment(2, 2, 1, 30, 2, true, "ok", "error");

        JsonNode history = getAbpWithToken(get("/api/services/app/Payment/GetPaymentHistory")
                .param("Sorting", "creationTime DESC")
                .param("MaxResultCount", "20")
                .param("SkipCount", "0"), tenantToken(2)).path("result");

        assertThat(history.path("items")).hasSize(1);
        assertThat(history.path("items").get(0).path("id").asLong()).isEqualTo(tenantTwoPayment.id);
        assertThat(history.path("items").get(0).path("tenantId").asInt()).isEqualTo(2);
    }

    @Test
    void paymentHistorySortsByOriginalEditionDisplayNameAlias() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        EditionItem alpha = edition("Alpha Payment Sort " + suffix);
        EditionItem zeta = edition("Zeta Payment Sort " + suffix);
        SubscriptionPaymentItem firstByEdition = store.createPayment(1, alpha.id, 1, 30, 2, true, "ok", "error");
        SubscriptionPaymentItem firstByCreation = store.createPayment(1, zeta.id, 1, 30, 2, true, "ok", "error");

        JsonNode history = getAbp(get("/api/services/app/Payment/GetPaymentHistory")
                .param("Sorting", "editionDisplayName ASC")
                .param("MaxResultCount", "20")
                .param("SkipCount", "0")).path("result").path("items");

        assertThat(indexOfPayment(history, firstByEdition.id)).isLessThan(indexOfPayment(history, firstByCreation.id));
    }

    @Test
    void paymentHistoryGetRejectsOriginalPagedInputRangeViolations() throws Exception {
        assertFailed(getRaw(get("/api/services/app/Payment/GetPaymentHistory")
                .param("SkipCount", "0")
                .param("MaxResultCount", "0")), "Validation failed");
        assertFailed(getRaw(get("/api/services/app/Payment/GetPaymentHistory")
                .param("SkipCount", "0")
                .param("MaxResultCount", "1001")), "Validation failed");
        assertFailed(getRaw(get("/api/services/app/Payment/GetPaymentHistory")
                .param("SkipCount", "-1")
                .param("MaxResultCount", "10")), "Validation failed");
    }

    @Test
    void lastCompletedPaymentAcceptsOriginalGetWithoutBody() throws Exception {
        SubscriptionPaymentItem payment = store.createPayment(2, 1, 30, 2, true, "ok", "error");
        store.markPaymentStatus(payment.id, 5);

        JsonNode result = getAbp(get("/api/services/app/Payment/GetLastCompletedPayment")).path("result");

        assertThat(result.path("id").asLong()).isEqualTo(payment.id);
        assertThat(result.path("status").asInt()).isEqualTo(5);
    }

    @Test
    void lastCompletedPaymentUsesCurrentTenantLikeOriginalService() throws Exception {
        store.subscriptionPayments().forEach(payment -> store.markPaymentStatus(payment.id, 1));
        SubscriptionPaymentItem tenantTwoPayment = store.createPayment(2, 2, 1, 30, 2, true, "ok", "error");
        store.markPaymentStatus(tenantTwoPayment.id, 5);
        SubscriptionPaymentItem tenantOnePayment = store.createPayment(1, 2, 1, 30, 2, true, "ok", "error");
        store.markPaymentStatus(tenantOnePayment.id, 5);

        JsonNode result = getAbpWithToken(get("/api/services/app/Payment/GetLastCompletedPayment"), tenantToken(2))
                .path("result");

        assertThat(result.path("id").asLong()).isEqualTo(tenantTwoPayment.id);
        assertThat(result.path("tenantId").asInt()).isEqualTo(2);
    }

    @Test
    void paymentAndStripePaymentDetailsAcceptOriginalGetQueryParameters() throws Exception {
        SubscriptionPaymentItem payment = store.createPayment(2, 1, 30, 2, true, "ok", "error");

        JsonNode paymentResult = getAbp(get("/api/services/app/Payment/GetPayment")
                .param("paymentId", String.valueOf(payment.id))).path("result");
        assertThat(paymentResult.path("id").asLong()).isEqualTo(payment.id);

        String stripeSessionId = store.createStripePaymentSession(payment.id);
        JsonNode stripeResult = getAbp(get("/api/services/app/StripePayment/GetPayment")
                .param("StripeSessionId", stripeSessionId)).path("result");
        assertThat(stripeResult.path("id").asLong()).isEqualTo(payment.id);
        assertThat(stripeResult.path("externalPaymentId").asText()).isEqualTo(stripeSessionId);
    }

    @Test
    void stripePaymentGetRejectsUnknownSessionLikeOriginalService() throws Exception {
        String stripeSessionId = "cs_test_missing";

        JsonNode response = getRaw(get("/api/services/app/StripePayment/GetPayment")
                .param("StripeSessionId", stripeSessionId));

        assertFailed(response, "Cannot find any payment with sessionId " + stripeSessionId);
    }

    private JsonNode getAbp(MockHttpServletRequestBuilder request) throws Exception {
        JsonNode response = getAbpWithToken(request, adminToken());
        return response;
    }

    private JsonNode getAbpWithToken(MockHttpServletRequestBuilder request, String token) throws Exception {
        JsonNode response = getRawWithToken(request, token);
        assertThat(response.path("success").asBoolean()).isTrue();
        return response;
    }

    private JsonNode getRaw(MockHttpServletRequestBuilder request) throws Exception {
        return getRawWithToken(request, adminToken());
    }

    private JsonNode getRawWithToken(MockHttpServletRequestBuilder request, String token) throws Exception {
        String body = mockMvc.perform(request.header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    private void assertFailed(JsonNode response, String message) {
        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText()).isEqualTo(message);
    }

    private int indexOfPayment(JsonNode rows, Long paymentId) {
        for (int index = 0; index < rows.size(); index++) {
            if (rows.get(index).path("id").asLong() == paymentId) {
                return index;
            }
        }
        return -1;
    }

    private void setTenantEdition(Integer editionId) {
        TenantItem tenant = store.tenant(1).orElseThrow();
        tenant.editionId = editionId;
        tenant.isActive = true;
        store.updateTenant(tenant);
    }

    private EditionItem edition(String displayName) {
        EditionItem item = new EditionItem();
        item.name = displayName.replaceAll("\\s+", "");
        item.displayName = displayName;
        item.monthlyPrice = BigDecimal.TEN;
        return store.saveEdition(item, List.of());
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
