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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "replica.store.path=target/test-data/payment-gateway-configuration-parity-store.json",
        "payment.stripe.publishable-key=pk_configured_original",
        "payment.stripe.is-active=true",
        "payment.paypal.client-id=paypal-configured-client",
        "payment.paypal.demo-username=paypal-demo-user",
        "payment.paypal.demo-password=paypal-demo-password",
        "payment.paypal.is-active=false"
})
@AutoConfigureMockMvc
class PaymentGatewayConfigurationParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/payment-gateway-configuration-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset payment gateway configuration parity test store", ex);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthService authService;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void stripeConfigurationUsesConfiguredPublishableKeyLikeOriginalGatewayConfiguration() throws Exception {
        JsonNode result = getAbp("/api/services/app/StripePayment/GetConfiguration").path("result");

        assertThat(result.path("publishableKey").asText()).isEqualTo("pk_configured_original");
    }

    @Test
    void payPalConfigurationUsesConfiguredClientAndDemoCredentialsLikeOriginalGatewayConfiguration() throws Exception {
        JsonNode result = getAbp("/api/services/app/PayPalPayment/GetConfiguration").path("result");

        assertThat(result.path("clientId").asText()).isEqualTo("paypal-configured-client");
        assertThat(result.path("demoUsername").asText()).isEqualTo("paypal-demo-user");
        assertThat(result.path("demoPassword").asText()).isEqualTo("paypal-demo-password");
    }

    @Test
    void activeGatewaysUseConfiguredIsActiveAndOriginalModelShape() throws Exception {
        JsonNode gateways = getAbp("/api/services/app/Payment/GetActiveGateways").path("result");

        assertThat(gateways).hasSize(1);
        JsonNode stripe = gateways.get(0);
        assertThat(stripe.path("gatewayType").asInt()).isEqualTo(2);
        assertThat(stripe.path("supportsRecurringPayments").asBoolean()).isTrue();
        assertThat(stripe.has("name")).isFalse();
    }

    private JsonNode getAbp(String url) throws Exception {
        String body = mockMvc.perform(get(url).header("Authorization", "Bearer " + adminToken()))
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
