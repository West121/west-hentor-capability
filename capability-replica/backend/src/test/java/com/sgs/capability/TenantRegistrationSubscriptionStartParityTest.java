package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.model.EditionItem;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/tenant-registration-start-parity-store.json")
@AutoConfigureMockMvc
class TenantRegistrationSubscriptionStartParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/tenant-registration-start-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset tenant registration parity test store", ex);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    CapabilityStore store;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void paidStartWithFreeEditionReturnsOriginalSubscriptionStartError() throws Exception {
        EditionItem freeEdition = freeEdition("Free Start Edition " + System.nanoTime());

        JsonNode response = registerTenant(Map.of(
                "tenancyName", "paidfree" + System.nanoTime(),
                "name", "Paid Free Tenant",
                "adminEmailAddress", "paid-free@example.local",
                "adminPassword", "123qwe",
                "editionId", freeEdition.id,
                "subscriptionStartType", 3
        ));

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText())
                .isEqualTo("This is a free edition and cannot be subscribed as paid !");
    }

    @Test
    void registrationWithoutEditionIsRejectedWhenOriginalHasAnyEditionDefined() throws Exception {
        paidEdition("Paid Defined Edition " + System.nanoTime(), 0);

        JsonNode response = registerTenant(Map.of(
                "tenancyName", "noedition" + System.nanoTime(),
                "name", "No Edition Tenant",
                "adminEmailAddress", "no-edition@example.local",
                "adminPassword", "123qwe",
                "subscriptionStartType", 1
        ));

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText())
                .isEqualTo("Tenant registration is not allowed without edition because there are editions defined !");
    }

    @Test
    void trialStartWithoutTrialDaysReturnsOriginalSubscriptionStartError() throws Exception {
        EditionItem noTrialEdition = paidEdition("No Trial Edition " + System.nanoTime(), 0);

        JsonNode response = registerTenant(Map.of(
                "tenancyName", "trialnone" + System.nanoTime(),
                "name", "Trial None Tenant",
                "adminEmailAddress", "trial-none@example.local",
                "adminPassword", "123qwe",
                "editionId", noTrialEdition.id,
                "subscriptionStartType", 2
        ));

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText())
                .isEqualTo("Trial is not available for this edition !");
    }

    @Test
    void freeStartWithPaidEditionReturnsOriginalSubscriptionStartError() throws Exception {
        EditionItem paidEdition = paidEdition("Paid Start Edition " + System.nanoTime(), 7);

        JsonNode response = registerTenant(Map.of(
                "tenancyName", "freepaid" + System.nanoTime(),
                "name", "Free Paid Tenant",
                "adminEmailAddress", "free-paid@example.local",
                "adminPassword", "123qwe",
                "editionId", paidEdition.id,
                "subscriptionStartType", 1
        ));

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText())
                .isEqualTo("This is not a free edition !");
    }

    @Test
    void dailyPricedEditionIsNotFreeLikeOriginalSubscribableEdition() throws Exception {
        EditionItem dailyPaidEdition = dailyPaidEdition("Daily Paid Edition " + System.nanoTime());

        JsonNode response = registerTenant(Map.of(
                "tenancyName", "dailyfree" + System.nanoTime(),
                "name", "Daily Free Tenant",
                "adminEmailAddress", "daily-free@example.local",
                "adminPassword", "123qwe",
                "editionId", dailyPaidEdition.id,
                "subscriptionStartType", 1
        ));

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText())
                .isEqualTo("This is not a free edition !");
    }

    private EditionItem freeEdition(String displayName) {
        EditionItem item = new EditionItem();
        item.name = displayName.replaceAll("\\s+", "");
        item.displayName = displayName;
        return store.saveEdition(item, List.of());
    }

    private EditionItem dailyPaidEdition(String displayName) {
        EditionItem item = new EditionItem();
        item.name = displayName.replaceAll("\\s+", "");
        item.displayName = displayName;
        item.dailyPrice = BigDecimal.valueOf(5);
        return store.saveEdition(item, List.of());
    }

    private EditionItem paidEdition(String displayName, int trialDayCount) {
        EditionItem item = new EditionItem();
        item.name = displayName.replaceAll("\\s+", "");
        item.displayName = displayName;
        item.monthlyPrice = BigDecimal.TEN;
        item.annualPrice = BigDecimal.valueOf(100);
        item.trialDayCount = trialDayCount;
        return store.saveEdition(item, List.of());
    }

    private JsonNode registerTenant(Object payload) throws Exception {
        String body = mockMvc.perform(post("/api/services/app/TenantRegistration/RegisterTenant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(payload)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }
}
