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

@SpringBootTest(properties = "replica.store.path=target/test-data/payment-default-gateway-configuration-parity-store.json")
@AutoConfigureMockMvc
class PaymentDefaultGatewayConfigurationParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/payment-default-gateway-configuration-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset payment default gateway configuration parity test store", ex);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthService authService;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void activeGatewaysDefaultToOriginalAppSettingsActiveFlags() throws Exception {
        JsonNode gateways = getAbp("/api/services/app/Payment/GetActiveGateways").path("result");

        assertThat(gateways).hasSize(2);
        assertThat(gateways.get(0).path("gatewayType").asInt()).isEqualTo(1);
        assertThat(gateways.get(1).path("gatewayType").asInt()).isEqualTo(2);
    }

    @Test
    void paymentGatewayConfigurationDefaultsKeepOriginalBlankCredentials() throws Exception {
        JsonNode stripe = getAbp("/api/services/app/StripePayment/GetConfiguration").path("result");
        JsonNode paypal = getAbp("/api/services/app/PayPalPayment/GetConfiguration").path("result");

        assertThat(stripe.path("publishableKey").asText()).isEmpty();
        assertThat(paypal.path("clientId").asText()).isEmpty();
        assertThat(paypal.path("demoUsername").asText()).isEmpty();
        assertThat(paypal.path("demoPassword").asText()).isEmpty();
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
