package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.model.EditionItem;
import com.sgs.capability.model.SubscriptionPaymentItem;
import com.sgs.capability.security.AuthService;
import com.sgs.capability.service.CapabilityStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/tenant-registration-editions-select-parity-store.json")
@AutoConfigureMockMvc
class TenantRegistrationEditionsForSelectParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/tenant-registration-editions-select-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset tenant registration editions-select parity test store", ex);
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
    void getEditionReturnsOriginalEmptyAdditionalDataDictionary() throws Exception {
        JsonNode edition = getAbp("/api/services/app/TenantRegistration/GetEdition?editionId=1")
                .path("result");

        assertThat(edition.path("additionalData").isObject()).isTrue();
        assertThat(edition.path("additionalData").size()).isZero();
    }

    @Test
    void loggedInTenantOnlySeesHigherPricedUpgradeEditionsInMonthlyPriceOrder() throws Exception {
        store.subscriptionPayments().forEach(payment -> store.markPaymentStatus(payment.id, 1));
        EditionItem current = paidEdition("Current Upgrade Base " + System.nanoTime(), 100);
        EditionItem cheaper = paidEdition("Cheaper Upgrade Option " + System.nanoTime(), 80);
        EditionItem equal = paidEdition("Equal Upgrade Option " + System.nanoTime(), 100);
        EditionItem expensive = paidEdition("First Upgrade Option " + System.nanoTime(), 150);
        SubscriptionPaymentItem completed = store.createPayment(current.id, 1, 30, 2, true, "ok", "error");
        store.markPaymentStatus(completed.id, 5);

        List<String> displayNames = editionDisplayNames(getAbp("/api/services/app/TenantRegistration/GetEditionsForSelect"));

        assertThat(displayNames)
                .doesNotContain(current.displayName, cheaper.displayName, equal.displayName)
                .containsSubsequence(expensive.displayName, "标准版", "企业版");
    }

    private EditionItem paidEdition(String displayName, int monthlyPrice) {
        EditionItem item = new EditionItem();
        item.name = displayName.replaceAll("\\s+", "");
        item.displayName = displayName;
        item.monthlyPrice = BigDecimal.valueOf(monthlyPrice);
        item.annualPrice = BigDecimal.valueOf(monthlyPrice * 10L);
        return store.saveEdition(item, List.of());
    }

    private List<String> editionDisplayNames(JsonNode response) {
        List<String> names = new ArrayList<>();
        JsonNode editions = response.path("result").path("editionsWithFeatures");
        StreamSupport.stream(editions.spliterator(), false)
                .map(node -> node.path("edition").path("displayName").asText())
                .forEach(names::add);
        return names;
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
