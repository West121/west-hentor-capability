package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.model.SubscriptionPaymentItem;
import com.sgs.capability.model.SystemSettingsItem;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/invoice-parity-store.json")
@AutoConfigureMockMvc
class InvoiceParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/invoice-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset invoice parity test store", ex);
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
    void createInvoiceKeepsOriginalVoidResponseWhileCreatingReadableInvoice() throws Exception {
        ensureCompleteTenantInvoiceSettings();
        SubscriptionPaymentItem payment = store.createPayment(null, 1, 30, 2,
                false, "http://localhost/success", "http://localhost/error");

        JsonNode response = postAbp("/api/services/app/Invoice/CreateInvoice", Map.of(
                "subscriptionPaymentId", payment.id
        ));

        assertThat(response.path("result").isNull()).isTrue();
        JsonNode invoice = postAbp("/api/services/app/Invoice/GetInvoiceInfo", Map.of(
                "id", String.valueOf(payment.id)
        )).path("result");
        assertThat(invoice.path("subscriptionPaymentId").asLong()).isEqualTo(payment.id);
        assertThat(invoice.path("invoiceNo").asText()).startsWith("INV-");
        assertThat(invoice.path("amount").decimalValue()).isEqualByComparingTo(payment.amount);
        assertThat(invoice.path("editionDisplayName").asText()).isEqualTo(payment.editionDisplayName);
    }

    @Test
    void getInvoiceInfoRejectsPaymentsFromAnotherTenantLikeOriginalService() throws Exception {
        SubscriptionPaymentItem payment = store.createPayment(null, 1, 30, 2,
                false, "http://localhost/success", "http://localhost/error");
        payment.tenantId = 2;
        store.createInvoice(payment.id);

        String body = mockMvc.perform(post("/api/services/app/Invoice/GetInvoiceInfo")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("id", String.valueOf(payment.id)))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText()).isEqualTo("This invoice is not yours!");
    }

    @Test
    void getInvoiceInfoRejectsPaymentsWithoutInvoiceLikeOriginalService() throws Exception {
        SubscriptionPaymentItem payment = store.createPayment(null, 1, 30, 2,
                false, "http://localhost/success", "http://localhost/error");

        String body = mockMvc.perform(post("/api/services/app/Invoice/GetInvoiceInfo")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("id", String.valueOf(payment.id)))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText())
                .isEqualTo("There is no invoice for this payment !");
    }

    @Test
    void createInvoiceRejectsDuplicateInvoiceLikeOriginalService() throws Exception {
        ensureCompleteTenantInvoiceSettings();
        SubscriptionPaymentItem payment = store.createPayment(null, 1, 30, 2,
                false, "http://localhost/success", "http://localhost/error");
        store.createInvoice(payment.id);

        String body = mockMvc.perform(post("/api/services/app/Invoice/CreateInvoice")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("subscriptionPaymentId", payment.id))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText())
                .isEqualTo("Invoice is already generated for this payment.");
    }

    @Test
    void createInvoiceRejectsMissingTenantInvoiceInfoLikeOriginalService() throws Exception {
        SystemSettingsItem.TenantSettings settings = store.tenantSettings();
        settings.billing.taxVatNo = "";
        store.updateTenantSettings(1, settings);
        SubscriptionPaymentItem payment = store.createPayment(null, 1, 30, 2,
                false, "http://localhost/success", "http://localhost/error");

        String body = mockMvc.perform(post("/api/services/app/Invoice/CreateInvoice")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("subscriptionPaymentId", payment.id))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText())
                .isEqualTo("Invoice info is missing or not completed");
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

    private void ensureCompleteTenantInvoiceSettings() {
        SystemSettingsItem.TenantSettings settings = store.tenantSettings();
        settings.billing.legalName = "SGS Tenant Replica";
        settings.billing.address = "Local tenant environment";
        settings.billing.taxVatNo = "LOCAL-TAX-001";
        store.updateTenantSettings(1, settings);
    }

    private String adminToken() {
        return authService.authenticate("admin", "123qwe").orElseThrow().token();
    }
}
