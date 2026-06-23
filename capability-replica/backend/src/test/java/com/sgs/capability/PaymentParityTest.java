package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.model.EditionItem;
import com.sgs.capability.model.TenantItem;
import com.sgs.capability.security.AuthService;
import com.sgs.capability.service.CapabilityStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/payment-parity-store.json")
@AutoConfigureMockMvc
class PaymentParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/payment-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset payment parity test store", ex);
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
    void upgradeSubscriptionCostsLessThanMinAmountUsesOriginalEditionIdParameter() throws Exception {
        setTenantEdition(1);
        String targetEditionDisplayName = store.edition(3).orElseThrow().displayName;

        JsonNode response = postAbp("/api/services/app/Payment/UpgradeSubscriptionCostsLessThenMinAmount", Map.of(
                "editionId", 3
        ));

        assertThat(response.path("result").isNull()).isTrue();
        assertThat(store.tenant(1)).hasValueSatisfying(tenant -> {
            assertThat(tenant.editionId).isEqualTo(3);
            assertThat(tenant.editionDisplayName).isEqualTo(targetEditionDisplayName);
        });
    }

    @Test
    void upgradeSubscriptionCostsLessThanMinAmountUsesCurrentTenantLikeOriginalService() throws Exception {
        Integer tenantOneEdition = store.tenant(1).orElseThrow().editionId;
        TenantItem tenantTwo = store.tenant(2).orElseThrow();
        Integer tenantTwoEdition = tenantTwo.editionId;
        boolean tenantTwoActive = tenantTwo.isActive;
        setTenantEdition(1);
        try {
            tenantTwo.editionId = 1;
            tenantTwo.isActive = true;
            store.updateTenant(tenantTwo);

            JsonNode response = postQueryAbpWithToken("/api/services/app/Payment/UpgradeSubscriptionCostsLessThenMinAmount",
                    tenantToken(2), "editionId", "3");

            assertThat(response.path("result").isNull()).isTrue();
            assertThat(store.tenant(1)).hasValueSatisfying(tenant -> assertThat(tenant.editionId).isEqualTo(1));
            assertThat(store.tenant(2)).hasValueSatisfying(tenant -> assertThat(tenant.editionId).isEqualTo(3));
        } finally {
            setTenantEdition(tenantOneEdition);
            TenantItem restoreTenantTwo = store.tenant(2).orElseThrow();
            restoreTenantTwo.editionId = tenantTwoEdition;
            restoreTenantTwo.isActive = tenantTwoActive;
            store.updateTenant(restoreTenantTwo);
        }
    }

    @Test
    void switchBetweenFreeEditionsRejectsTenantWithoutEditionLikeOriginalService() throws Exception {
        EditionItem targetFreeEdition = freeEdition("Free Switch Target " + System.nanoTime());
        setTenantEdition(null);

        JsonNode response = postQueryRaw("/api/services/app/Payment/SwitchBetweenFreeEditions",
                "upgradeEditionId", String.valueOf(targetFreeEdition.id));

        assertFailed(response, "tenant.EditionId can not be null");
        assertThat(store.tenant(1)).hasValueSatisfying(tenant -> assertThat(tenant.editionId).isNull());
    }

    @Test
    void switchBetweenFreeEditionsRejectsCurrentPaidEditionLikeOriginalService() throws Exception {
        EditionItem currentPaidEdition = paidEdition("Paid Current Switch " + System.nanoTime());
        EditionItem targetFreeEdition = freeEdition("Free Target Switch " + System.nanoTime());
        setTenantEdition(currentPaidEdition.id);

        JsonNode response = postQueryRaw("/api/services/app/Payment/SwitchBetweenFreeEditions",
                "upgradeEditionId", String.valueOf(targetFreeEdition.id));

        assertFailed(response, "You can only switch between free editions. Current edition if not free");
        assertThat(store.tenant(1)).hasValueSatisfying(tenant -> assertThat(tenant.editionId).isEqualTo(currentPaidEdition.id));
    }

    @Test
    void switchBetweenFreeEditionsRejectsTargetPaidEditionLikeOriginalService() throws Exception {
        EditionItem currentFreeEdition = freeEdition("Free Current Switch " + System.nanoTime());
        EditionItem targetPaidEdition = paidEdition("Paid Target Switch " + System.nanoTime());
        setTenantEdition(currentFreeEdition.id);

        JsonNode response = postQueryRaw("/api/services/app/Payment/SwitchBetweenFreeEditions",
                "upgradeEditionId", String.valueOf(targetPaidEdition.id));

        assertFailed(response, "You can only switch between free editions. Target edition if not free");
        assertThat(store.tenant(1)).hasValueSatisfying(tenant -> assertThat(tenant.editionId).isEqualTo(currentFreeEdition.id));
    }

    @Test
    void switchBetweenFreeEditionsUsesCurrentTenantLikeOriginalService() throws Exception {
        Integer tenantOneEdition = store.tenant(1).orElseThrow().editionId;
        TenantItem tenantTwo = store.tenant(2).orElseThrow();
        Integer tenantTwoEdition = tenantTwo.editionId;
        boolean tenantTwoActive = tenantTwo.isActive;
        EditionItem currentFreeEdition = freeEdition("Tenant Two Free Current " + System.nanoTime());
        EditionItem targetFreeEdition = freeEdition("Tenant Two Free Target " + System.nanoTime());
        setTenantEdition(2);
        try {
            tenantTwo.editionId = currentFreeEdition.id;
            tenantTwo.isActive = true;
            store.updateTenant(tenantTwo);

            JsonNode response = postQueryAbpWithToken("/api/services/app/Payment/SwitchBetweenFreeEditions",
                    tenantToken(2), "upgradeEditionId", String.valueOf(targetFreeEdition.id));

            assertThat(response.path("result").isNull()).isTrue();
            assertThat(store.tenant(1)).hasValueSatisfying(tenant -> assertThat(tenant.editionId).isEqualTo(2));
            assertThat(store.tenant(2)).hasValueSatisfying(tenant -> assertThat(tenant.editionId).isEqualTo(targetFreeEdition.id));
        } finally {
            setTenantEdition(tenantOneEdition);
            TenantItem restoreTenantTwo = store.tenant(2).orElseThrow();
            restoreTenantTwo.editionId = tenantTwoEdition;
            restoreTenantTwo.isActive = tenantTwoActive;
            store.updateTenant(restoreTenantTwo);
        }
    }

    @Test
    void createUpgradePaymentRejectsTenantWithoutEditionLikeOriginalService() throws Exception {
        Integer originalEditionId = store.tenant(1).map(tenant -> tenant.editionId).orElse(null);
        int originalPaymentCount = store.subscriptionPayments().size();
        setTenantEdition(null);
        try {
            JsonNode response = postRaw("/api/services/app/Payment/CreatePayment", Map.of(
                    "editionId", 3,
                    "editionPaymentType", 2,
                    "paymentPeriodType", 30,
                    "subscriptionPaymentGatewayType", 2,
                    "recurringPaymentEnabled", false,
                    "successUrl", "ok",
                    "errorUrl", "error"
            ));

            assertFailed(response, "Can not upgrade subscription since tenant has no edition assigned.");
            assertThat(store.subscriptionPayments()).hasSize(originalPaymentCount);
        } finally {
            setTenantEdition(originalEditionId);
        }
    }

    @Test
    void createRecurringPaymentSetsTenantPaymentTypeLikeOriginalService() throws Exception {
        TenantItem tenant = store.tenant(1).orElseThrow();
        tenant.subscriptionPaymentType = 2;
        store.updateTenant(tenant);

        JsonNode response = postAbp("/api/services/app/Payment/CreatePayment", Map.of(
                "editionId", 2,
                "editionPaymentType", 1,
                "paymentPeriodType", 30,
                "subscriptionPaymentGatewayType", 2,
                "recurringPaymentEnabled", true,
                "successUrl", "ok",
                "errorUrl", "error"
        ));

        assertThat(response.path("result").asLong()).isPositive();
        assertThat(store.tenant(1)).hasValueSatisfying(updated ->
                assertThat(updated.subscriptionPaymentType).isEqualTo(1));
    }

    @Test
    void createPaymentUsesCurrentTenantLikeOriginalService() throws Exception {
        TenantItem tenantTwo = store.tenant(2).orElseThrow();
        tenantTwo.subscriptionPaymentType = 2;
        store.updateTenant(tenantTwo);

        JsonNode response = postAbpWithToken("/api/services/app/Payment/CreatePayment", tenantToken(2), Map.of(
                "editionId", 2,
                "editionPaymentType", 1,
                "paymentPeriodType", 30,
                "subscriptionPaymentGatewayType", 2,
                "recurringPaymentEnabled", true,
                "successUrl", "ok",
                "errorUrl", "error"
        ));

        long paymentId = response.path("result").asLong();
        assertThat(store.payment(paymentId)).hasValueSatisfying(payment ->
                assertThat(payment.tenantId).isEqualTo(2));
        assertThat(store.tenant(1)).hasValueSatisfying(updated ->
                assertThat(updated.subscriptionPaymentType).isEqualTo(1));
        assertThat(store.tenant(2)).hasValueSatisfying(updated ->
                assertThat(updated.subscriptionPaymentType).isEqualTo(1));
    }

    @Test
    void paymentInfoUsesCurrentTenantLikeOriginalService() throws Exception {
        Integer tenantOneEdition = store.tenant(1).orElseThrow().editionId;
        TenantItem tenantTwo = store.tenant(2).orElseThrow();
        Integer tenantTwoEdition = tenantTwo.editionId;
        boolean tenantTwoActive = tenantTwo.isActive;
        setTenantEdition(null);
        try {
            tenantTwo.editionId = 2;
            tenantTwo.isActive = true;
            store.updateTenant(tenantTwo);

            JsonNode response = postAbpWithToken("/api/services/app/Payment/GetPaymentInfo", tenantToken(2), Map.of());

            assertThat(response.path("result").path("edition").path("id").asInt()).isEqualTo(2);
        } finally {
            setTenantEdition(tenantOneEdition);
            TenantItem restoreTenantTwo = store.tenant(2).orElseThrow();
            restoreTenantTwo.editionId = tenantTwoEdition;
            restoreTenantTwo.isActive = tenantTwoActive;
            store.updateTenant(restoreTenantTwo);
        }
    }

    @Test
    void paymentHistoryRejectsOriginalPagedInputRangeViolations() throws Exception {
        assertFailed(postRaw("/api/services/app/Payment/GetPaymentHistory", Map.of(
                "skipCount", 0,
                "maxResultCount", 0
        )), "Validation failed");
        assertFailed(postRaw("/api/services/app/Payment/GetPaymentHistory", Map.of(
                "skipCount", 0,
                "maxResultCount", 1001
        )), "Validation failed");
        assertFailed(postRaw("/api/services/app/Payment/GetPaymentHistory", Map.of(
                "skipCount", -1,
                "maxResultCount", 10
        )), "Validation failed");
    }

    @Test
    void paymentHistoryKeepsOriginalPagedDefaults() throws Exception {
        store.createPayment(2, 1, 30, 2, true, "ok", "error");

        JsonNode history = postAbp("/api/services/app/Payment/GetPaymentHistory", Map.of()).path("result");

        assertThat(history.path("totalCount").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(history.path("items").size()).isLessThanOrEqualTo(10);
    }

    @Test
    void paymentSuccessCallbackRejectsNotPaidPaymentLikeOriginalService() throws Exception {
        Integer originalEditionId = store.tenant(1).orElseThrow().editionId;
        setTenantEdition(1);
        try {
            long paymentId = store.createPayment(3, 1, 30, 2, true, "ok", "error").id;

            JsonNode response = postQueryRaw("/api/services/app/Payment/BuyNowSucceed",
                    "paymentId", String.valueOf(paymentId));

            assertFailed(response, "Your payment is not completed !");
            assertThat(store.payment(paymentId)).hasValueSatisfying(payment -> assertThat(payment.status).isEqualTo(1));
            assertThat(store.tenant(1)).hasValueSatisfying(tenant -> assertThat(tenant.editionId).isEqualTo(1));
        } finally {
            setTenantEdition(originalEditionId);
        }
    }

    @Test
    void paymentCallbackRoutesAcceptOriginalPostQueryParameters() throws Exception {
        long completedPaymentId = store.createPayment(2, 1, 30, 2, true, "ok", "error").id;
        store.markPaymentStatus(completedPaymentId, 2);
        postQueryAbp("/api/services/app/Payment/BuyNowSucceed", "paymentId", String.valueOf(completedPaymentId));
        assertThat(store.payment(completedPaymentId)).hasValueSatisfying(payment -> assertThat(payment.status).isEqualTo(5));

        long failedPaymentId = store.createPayment(2, 1, 30, 2, true, "ok", "error").id;
        postQueryAbp("/api/services/app/Payment/PaymentFailed", "paymentId", String.valueOf(failedPaymentId));
        assertThat(store.payment(failedPaymentId)).hasValueSatisfying(payment -> assertThat(payment.status).isEqualTo(3));

        EditionItem targetFreeEdition = freeEdition("Callback Free Switch " + System.nanoTime());
        setTenantEdition(1);
        postQueryAbp("/api/services/app/Payment/SwitchBetweenFreeEditions",
                "upgradeEditionId", String.valueOf(targetFreeEdition.id));
        assertThat(store.tenant(1)).hasValueSatisfying(tenant -> assertThat(tenant.editionId).isEqualTo(targetFreeEdition.id));

        postQueryAbp("/api/services/app/Payment/UpgradeSubscriptionCostsLessThenMinAmount", "editionId", "2");
        assertThat(store.tenant(1)).hasValueSatisfying(tenant -> assertThat(tenant.editionId).isEqualTo(2));
    }

    @Test
    void payPalConfirmPaymentAcceptsOriginalPostQueryParameters() throws Exception {
        Integer originalEditionId = store.tenant(1).orElseThrow().editionId;
        setTenantEdition(1);
        long paymentId = store.createPayment(3, 1, 30, 1, false, "ok", "error").id;

        try {
            postTwoQueryAbp("/api/services/app/PayPalPayment/ConfirmPayment",
                    "paymentId", String.valueOf(paymentId), "paypalOrderId", "PAYPAL-ORDER-42");

            assertThat(store.payment(paymentId)).hasValueSatisfying(payment -> {
                assertThat(payment.status).isEqualTo(2);
                assertThat(payment.gateway).isEqualTo(1);
                assertThat(payment.externalPaymentId).isEqualTo("PAYPAL-ORDER-42");
            });
            assertThat(store.tenant(1)).hasValueSatisfying(tenant -> assertThat(tenant.editionId).isEqualTo(1));
        } finally {
            setTenantEdition(originalEditionId);
        }
    }

    @Test
    void stripeConfirmPaymentSetsPaidButNotDoneLikeOriginalService() throws Exception {
        Integer originalEditionId = store.tenant(1).orElseThrow().editionId;
        setTenantEdition(1);
        long paymentId = store.createPayment(3, 1, 30, 2, true, "ok", "error").id;
        String stripeSessionId = store.createStripePaymentSession(paymentId);
        try {
            JsonNode response = postAbp("/api/services/app/StripePayment/ConfirmPayment", Map.of(
                    "stripeSessionId", stripeSessionId
            ));

            assertThat(response.path("result").isNull()).isTrue();
            assertThat(store.payment(paymentId)).hasValueSatisfying(payment -> assertThat(payment.status).isEqualTo(2));
            assertThat(getAbp("/api/services/app/StripePayment/GetPaymentResult", "PaymentId", String.valueOf(paymentId))
                    .path("result").path("paymentDone").asBoolean()).isFalse();
            assertThat(store.tenant(1)).hasValueSatisfying(tenant -> assertThat(tenant.editionId).isEqualTo(1));
        } finally {
            setTenantEdition(originalEditionId);
        }
    }

    @Test
    void cancelPaymentKeepsPaidPaymentStatusLikeOriginalSetAsCancelled() throws Exception {
        long paymentId = store.createPayment(2, 1, 30, 2, false, "ok", "error").id;
        store.markPaymentPaid(paymentId);

        postAbp("/api/services/app/Payment/CancelPayment", Map.of(
                "paymentId", "PAY-" + paymentId,
                "gateway", 2
        ));

        assertThat(store.payment(paymentId)).hasValueSatisfying(payment -> {
            assertThat(payment.status).isEqualTo(2);
            assertThat(payment.statusName).isEqualTo("Paid");
        });
    }

    @Test
    void gatewayConfirmPaymentKeepsCompletedPaymentStatusLikeOriginalSetAsPaid() throws Exception {
        long paypalPaymentId = store.createPayment(1, 1, 30, 1, false, "ok", "error").id;
        store.markPaymentStatus(paypalPaymentId, 5);

        postTwoQueryAbp("/api/services/app/PayPalPayment/ConfirmPayment",
                "paymentId", String.valueOf(paypalPaymentId), "paypalOrderId", "PAYPAL-COMPLETED-42");

        assertThat(store.payment(paypalPaymentId)).hasValueSatisfying(payment -> {
            assertThat(payment.status).isEqualTo(5);
            assertThat(payment.statusName).isEqualTo("Completed");
            assertThat(payment.externalPaymentId).isEqualTo("PAYPAL-COMPLETED-42");
        });

        long stripePaymentId = store.createPayment(1, 1, 30, 2, true, "ok", "error").id;
        String stripeSessionId = store.createStripePaymentSession(stripePaymentId);
        store.markPaymentStatus(stripePaymentId, 5);

        postAbp("/api/services/app/StripePayment/ConfirmPayment", Map.of(
                "stripeSessionId", stripeSessionId
        ));

        assertThat(store.payment(stripePaymentId)).hasValueSatisfying(payment -> {
            assertThat(payment.status).isEqualTo(5);
            assertThat(payment.statusName).isEqualTo("Completed");
        });
    }

    @Test
    void stripePaymentResultAcceptsOriginalGetQueryPaymentId() throws Exception {
        long paymentId = store.createPayment(2, 1, 30, 2, true, "ok", "error").id;
        store.createStripePaymentSession(paymentId);

        JsonNode pending = getAbp("/api/services/app/StripePayment/GetPaymentResult", "PaymentId", String.valueOf(paymentId));
        assertThat(pending.path("result").path("paymentDone").asBoolean()).isFalse();

        store.markPaymentStatus(paymentId, 5);
        JsonNode completed = getAbp("/api/services/app/StripePayment/GetPaymentResult", "PaymentId", String.valueOf(paymentId));
        assertThat(completed.path("result").path("paymentDone").asBoolean()).isTrue();
    }

    @Test
    void stripePaymentResultRejectsPaymentWithoutStripeSessionLikeOriginalService() throws Exception {
        long paymentId = store.createPayment(2, 1, 30, 2, true, "ok", "error").id;

        JsonNode response = getRaw("/api/services/app/StripePayment/GetPaymentResult", "PaymentId", String.valueOf(paymentId));

        assertFailed(response, "Stripe session information for the payment transaction could not be found.");
    }

    @Test
    void stripeWebHooksRejectsUnsignedPayloadLikeOriginalController() throws Exception {
        mockMvc.perform(post("/Stripe/WebHooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"checkout.session.completed\"}"))
                .andExpect(status().isBadRequest());
    }

    private JsonNode postAbp(String url, Object payload) throws Exception {
        JsonNode response = postRaw(url, payload);
        assertThat(response.path("success").asBoolean()).isTrue();
        return response;
    }

    private JsonNode postRaw(String url, Object payload) throws Exception {
        return postRawWithToken(url, adminToken(), payload);
    }

    private JsonNode postRawWithToken(String url, String token, Object payload) throws Exception {
        String body = mockMvc.perform(post(url)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(payload)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    private JsonNode postAbpWithToken(String url, String token, Object payload) throws Exception {
        JsonNode response = postRawWithToken(url, token, payload);
        assertThat(response.path("success").asBoolean()).isTrue();
        return response;
    }

    private JsonNode postQueryAbp(String url, String name, String value) throws Exception {
        JsonNode response = postQueryRaw(url, name, value);
        assertThat(response.path("success").asBoolean()).isTrue();
        return response;
    }

    private JsonNode postQueryRaw(String url, String name, String value) throws Exception {
        return postQueryRawWithToken(url, adminToken(), name, value);
    }

    private JsonNode postQueryAbpWithToken(String url, String token, String name, String value) throws Exception {
        JsonNode response = postQueryRawWithToken(url, token, name, value);
        assertThat(response.path("success").asBoolean()).isTrue();
        return response;
    }

    private JsonNode postQueryRawWithToken(String url, String token, String name, String value) throws Exception {
        String body = mockMvc.perform(post(url)
                        .header("Authorization", "Bearer " + token)
                        .param(name, value))
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

    private EditionItem freeEdition(String displayName) {
        EditionItem item = new EditionItem();
        item.name = displayName.replaceAll("\\s+", "");
        item.displayName = displayName;
        item.dailyPrice = BigDecimal.ZERO;
        item.weeklyPrice = BigDecimal.ZERO;
        item.monthlyPrice = BigDecimal.ZERO;
        item.annualPrice = BigDecimal.ZERO;
        return store.saveEdition(item, java.util.List.of());
    }

    private EditionItem paidEdition(String displayName) {
        EditionItem item = new EditionItem();
        item.name = displayName.replaceAll("\\s+", "");
        item.displayName = displayName;
        item.monthlyPrice = BigDecimal.TEN;
        item.annualPrice = BigDecimal.valueOf(100);
        return store.saveEdition(item, java.util.List.of());
    }

    private void setTenantEdition(Integer editionId) {
        TenantItem tenant = store.tenant(1).orElseThrow();
        tenant.editionId = editionId;
        tenant.isActive = true;
        store.updateTenant(tenant);
    }

    private JsonNode postTwoQueryAbp(String url, String firstName, String firstValue, String secondName, String secondValue) throws Exception {
        String body = mockMvc.perform(post(url)
                        .header("Authorization", "Bearer " + adminToken())
                        .param(firstName, firstValue)
                        .param(secondName, secondValue))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isTrue();
        return response;
    }

    private JsonNode getAbp(String url, String name, String value) throws Exception {
        JsonNode response = getRaw(url, name, value);
        assertThat(response.path("success").asBoolean()).isTrue();
        return response;
    }

    private JsonNode getRaw(String url, String name, String value) throws Exception {
        String body = mockMvc.perform(get(url)
                        .header("Authorization", "Bearer " + adminToken())
                        .param(name, value))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
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
