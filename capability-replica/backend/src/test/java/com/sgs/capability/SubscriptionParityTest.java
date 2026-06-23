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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/subscription-parity-store.json")
@AutoConfigureMockMvc
class SubscriptionParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/subscription-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset subscription parity test store", ex);
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
    void recurringPaymentTogglesUpdateOnlyCurrentTenantLikeOriginalService() throws Exception {
        String tenantTwoToken = tenantToken(2);
        String tenantOneEndDate = tenantJson(1).path("subscriptionEndDateUtc").asText();
        String tenantTwoEndDate = tenantJson(2).path("subscriptionEndDateUtc").asText();

        JsonNode disabled = postAbpWithToken("/api/services/app/Subscription/DisableRecurringPayments", tenantTwoToken);

        assertThat(disabled.path("result").isNull()).isTrue();
        assertThat(tenantJson(1).path("subscriptionPaymentType").asInt(-1)).isEqualTo(1);
        assertThat(tenantJson(1).path("subscriptionEndDateUtc").asText()).isEqualTo(tenantOneEndDate);
        assertThat(tenantJson(2).path("subscriptionPaymentType").asInt(-1)).isEqualTo(2);
        assertThat(tenantJson(2).path("subscriptionEndDateUtc").asText()).isEqualTo(tenantTwoEndDate);

        JsonNode enabled = postAbpWithToken("/api/services/app/Subscription/EnableRecurringPayments", tenantTwoToken);

        assertThat(enabled.path("result").isNull()).isTrue();
        assertThat(tenantJson(1).path("subscriptionPaymentType").asInt(-1)).isEqualTo(1);
        assertThat(tenantJson(1).path("subscriptionEndDateUtc").asText()).isEqualTo(tenantOneEndDate);
        assertThat(tenantJson(2).path("subscriptionPaymentType").asInt(-1)).isEqualTo(1);
        assertThat(tenantJson(2).path("subscriptionEndDateUtc").isNull()).isTrue();
        assertThat(sessionTenant(tenantTwoToken).path("subscriptionPaymentType").asInt(-1)).isEqualTo(1);
        assertThat(sessionTenant(tenantTwoToken).path("subscriptionEndDateUtc").isNull()).isTrue();
    }

    private JsonNode tenantJson(int tenantId) {
        return objectMapper.valueToTree(store.tenant(tenantId).orElseThrow());
    }

    private JsonNode sessionTenant(String token) throws Exception {
        String body = mockMvc.perform(get("/api/services/app/Session/GetCurrentLoginInformations")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isTrue();
        return response.path("result").path("tenant");
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
