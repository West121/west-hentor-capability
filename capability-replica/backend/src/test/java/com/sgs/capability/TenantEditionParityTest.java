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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/tenant-edition-parity-store.json")
@AutoConfigureMockMvc
class TenantEditionParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/tenant-edition-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset tenant edition parity test store", ex);
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
    void createEditionKeepsOriginalVoidResponseWhileSavingEdition() throws Exception {
        String displayName = "Void Edition " + System.nanoTime();

        JsonNode response = postAbp("/api/services/app/Edition/CreateEdition", Map.of(
                "edition", Map.of(
                        "name", "VoidEdition" + System.nanoTime(),
                        "displayName", displayName,
                        "monthlyPrice", 0,
                        "annualPrice", 0,
                        "trialDayCount", 14
                ),
                "featureValues", List.of()
        ));

        assertThat(response.path("result").isNull()).isTrue();
        assertThat(store.editions()).anySatisfy(edition -> {
            assertThat(edition.displayName).isEqualTo(displayName);
            assertThat(edition.trialDayCount).isEqualTo(14);
            assertThat(edition.isFree).isTrue();
        });
    }

    @Test
    void createEditionWithPaidExpiringEditionReturnsOriginalError() throws Exception {
        EditionItem paidExpiringEdition = paidEdition("Paid Expiring Edition " + System.nanoTime());
        String displayName = "Invalid Expiring Edition " + System.nanoTime();

        JsonNode response = postJson("/api/services/app/Edition/CreateEdition", Map.of(
                "edition", Map.of(
                        "name", "InvalidExpiringEdition" + System.nanoTime(),
                        "displayName", displayName,
                        "monthlyPrice", 0,
                        "annualPrice", 0,
                        "expiringEditionId", paidExpiringEdition.id
                ),
                "featureValues", List.of()
        ));

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText())
                .isEqualTo("Expiring edition must be a free edition");
        assertThat(store.editions()).noneSatisfy(edition -> assertThat(edition.displayName).isEqualTo(displayName));
    }

    @Test
    void getTenantsRejectsOriginalPagedInputRangeViolations() throws Exception {
        assertValidationFailure(postJson("/api/services/app/Tenant/GetTenants", Map.of(
                "skipCount", 0,
                "maxResultCount", 0
        )));
        assertValidationFailure(postJson("/api/services/app/Tenant/GetTenants", Map.of(
                "skipCount", 0,
                "maxResultCount", 1001
        )));
        assertValidationFailure(postJson("/api/services/app/Tenant/GetTenants", Map.of(
                "skipCount", -1,
                "maxResultCount", 10
        )));
        assertValidationFailure(getRaw("/api/services/app/Tenant/GetTenants", -1, 10));
    }

    @Test
    void getTenantsKeepsOriginalPagedDefaults() throws Exception {
        JsonNode tenants = postAbp("/api/services/app/Tenant/GetTenants", Map.of()).path("result");

        assertThat(tenants.path("totalCount").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(tenants.path("items").size()).isLessThanOrEqualTo(10);
    }

    @Test
    void updateEditionKeepsOriginalVoidResponseWhileSavingEdition() throws Exception {
        EditionItem existing = edition("Update Edition " + System.nanoTime());
        existing.displayName = "Updated Edition Display";
        existing.monthlyPrice = BigDecimal.valueOf(99);
        existing.annualPrice = BigDecimal.valueOf(999);

        JsonNode response = postAbp("/api/services/app/Edition/UpdateEdition", Map.of(
                "edition", existing,
                "featureValues", List.of()
        ));

        assertThat(response.path("result").isNull()).isTrue();
        assertThat(store.edition(existing.id)).hasValueSatisfying(edition -> {
            assertThat(edition.displayName).isEqualTo("Updated Edition Display");
            assertThat(edition.monthlyPrice).isEqualByComparingTo("99");
            assertThat(edition.isFree).isFalse();
        });
    }

    @Test
    void editionWriteRoutesRejectMissingOriginalRequiredFields() throws Exception {
        JsonNode missingCreateEdition = postJson("/api/services/app/Edition/CreateEdition", Map.of(
                "featureValues", List.of()
        ));

        assertValidationFailure(missingCreateEdition);

        JsonNode missingCreateFeatureValues = postJson("/api/services/app/Edition/CreateEdition", Map.of(
                "edition", Map.of("displayName", "Missing Feature Values Edition")
        ));

        assertValidationFailure(missingCreateFeatureValues);

        JsonNode missingCreateDisplayName = postJson("/api/services/app/Edition/CreateEdition", Map.of(
                "edition", Map.of("dailyPrice", 0),
                "featureValues", List.of()
        ));

        assertValidationFailure(missingCreateDisplayName);

        EditionItem existing = edition("Required Update Edition " + System.nanoTime());

        JsonNode missingUpdateEdition = postJson("/api/services/app/Edition/UpdateEdition", Map.of(
                "featureValues", List.of()
        ));

        assertValidationFailure(missingUpdateEdition);

        JsonNode missingUpdateFeatureValues = postJson("/api/services/app/Edition/UpdateEdition", Map.of(
                "edition", Map.of(
                        "id", existing.id,
                        "displayName", existing.displayName
                )
        ));

        assertValidationFailure(missingUpdateFeatureValues);

        JsonNode missingUpdateDisplayName = postJson("/api/services/app/Edition/UpdateEdition", Map.of(
                "edition", Map.of("id", existing.id),
                "featureValues", List.of()
        ));

        assertValidationFailure(missingUpdateDisplayName);
    }

    @Test
    void moveTenantsToAnotherEditionRejectsIdsOutsideOriginalRange() throws Exception {
        JsonNode zeroSource = postJson("/api/services/app/Edition/MoveTenantsToAnotherEdition", Map.of(
                "sourceEditionId", 0,
                "targetEditionId", 1
        ));

        assertValidationFailure(zeroSource);

        JsonNode missingTarget = postJson("/api/services/app/Edition/MoveTenantsToAnotherEdition", Map.of(
                "sourceEditionId", 1
        ));

        assertValidationFailure(missingTarget);
    }

    @Test
    void updateTenantFeaturesRejectsOriginalInputValidationViolations() throws Exception {
        TenantItem tenant = tenant("tenantfeatures" + System.nanoTime());

        JsonNode missingId = postJson("/api/services/app/Tenant/UpdateTenantFeatures", Map.of(
                "featureValues", List.of()
        ));

        assertValidationFailure(missingId);

        JsonNode zeroId = postJson("/api/services/app/Tenant/UpdateTenantFeatures", Map.of(
                "id", 0,
                "featureValues", List.of()
        ));

        assertValidationFailure(zeroId);

        JsonNode missingFeatureValues = postJson("/api/services/app/Tenant/UpdateTenantFeatures", Map.of(
                "id", tenant.id
        ));

        assertValidationFailure(missingFeatureValues);
    }

    @Test
    void createTenantKeepsOriginalVoidResponseWhileSavingTenant() throws Exception {
        String tenancyName = "voidtenant" + System.nanoTime();

        JsonNode response = postAbp("/api/services/app/Tenant/CreateTenant", Map.of(
                "tenancyName", tenancyName,
                "name", "Void Tenant",
                "adminEmailAddress", tenancyName + "@example.local",
                "isActive", true
        ));

        assertThat(response.path("result").isNull()).isTrue();
        assertThat(store.tenants(tenancyName, null, false, 0, 10).items).anySatisfy(tenant -> {
            assertThat(tenant.tenancyName).isEqualTo(tenancyName);
            assertThat(tenant.name).isEqualTo("Void Tenant");
            assertThat(tenant.isActive).isTrue();
        });
    }

    @Test
    void createTenantRejectsMissingAdminEmailInsteadOfInventingLocalAddress() throws Exception {
        String tenancyName = "missingemailtenant" + System.nanoTime();

        JsonNode response = postJson("/api/services/app/Tenant/CreateTenant", Map.of(
                "tenancyName", tenancyName,
                "name", "Missing Email Tenant",
                "isActive", true
        ));

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(store.tenants(tenancyName, null, false, 0, 10).items).isEmpty();
    }

    @Test
    void tenantWriteRoutesRejectNamesLongerThanOriginalInputLimits() throws Exception {
        String longTenancyName = "t".repeat(65);

        JsonNode createResponse = postJson("/api/services/app/Tenant/CreateTenant", Map.of(
                "tenancyName", longTenancyName,
                "name", "Tenant Name Length Check",
                "adminEmailAddress", "tenant-length@example.local",
                "isActive", true
        ));

        assertThat(createResponse.path("success").asBoolean()).isFalse();
        assertThat(createResponse.path("error").path("message").asText()).isEqualTo("Validation failed");
        assertThat(store.tenants(longTenancyName, null, false, 0, 10).items).isEmpty();

        TenantItem existing = tenant("tenantnamelength" + System.nanoTime());

        JsonNode updateResponse = postJson("/api/services/app/Tenant/UpdateTenant", Map.of(
                "id", existing.id,
                "tenancyName", existing.tenancyName,
                "name", "n".repeat(129),
                "isActive", true
        ));

        assertThat(updateResponse.path("success").asBoolean()).isFalse();
        assertThat(updateResponse.path("error").path("message").asText()).isEqualTo("Validation failed");
    }

    @Test
    void createTenantRejectsOtherFieldsLongerThanOriginalInputLimits() throws Exception {
        JsonNode emailResponse = postJson("/api/services/app/Tenant/CreateTenant", Map.of(
                "tenancyName", "emailfieldlimit" + System.nanoTime(),
                "name", "Tenant Email Field Limit",
                "adminEmailAddress", "a".repeat(245) + "@example.local",
                "isActive", true
        ));

        assertThat(emailResponse.path("success").asBoolean()).isFalse();
        assertThat(emailResponse.path("error").path("message").asText()).isEqualTo("Validation failed");

        JsonNode passwordResponse = postJson("/api/services/app/Tenant/CreateTenant", Map.of(
                "tenancyName", "passwordfieldlimit" + System.nanoTime(),
                "name", "Tenant Password Field Limit",
                "adminEmailAddress", "password-field-limit@example.local",
                "adminPassword", "p".repeat(129),
                "isActive", true
        ));

        assertThat(passwordResponse.path("success").asBoolean()).isFalse();
        assertThat(passwordResponse.path("error").path("message").asText()).isEqualTo("Validation failed");

        JsonNode connectionResponse = postJson("/api/services/app/Tenant/CreateTenant", Map.of(
                "tenancyName", "connectionfieldlimit" + System.nanoTime(),
                "name", "Tenant Connection Field Limit",
                "adminEmailAddress", "connection-field-limit@example.local",
                "connectionString", "c".repeat(1025),
                "isActive", true
        ));

        assertThat(connectionResponse.path("success").asBoolean()).isFalse();
        assertThat(connectionResponse.path("error").path("message").asText()).isEqualTo("Validation failed");
    }

    @Test
    void createTenantRejectsTenancyNameOutsideOriginalRegex() throws Exception {
        JsonNode response = postJson("/api/services/app/Tenant/CreateTenant", Map.of(
                "tenancyName", "1invalid",
                "name", "Invalid Tenancy Pattern",
                "adminEmailAddress", "invalid-tenancy-pattern@example.local",
                "isActive", true
        ));

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText()).isEqualTo("Validation failed");
        assertThat(store.tenants("1invalid", null, false, 0, 10).items).isEmpty();
    }

    @Test
    void updateTenantRejectsMissingOriginalRequiredFields() throws Exception {
        TenantItem existing = tenant("tenantrequired" + System.nanoTime());

        JsonNode missingName = postJson("/api/services/app/Tenant/UpdateTenant", Map.of(
                "id", existing.id,
                "tenancyName", existing.tenancyName,
                "isActive", true
        ));

        assertThat(missingName.path("success").asBoolean()).isFalse();
        assertThat(missingName.path("error").path("message").asText()).isEqualTo("Validation failed");

        JsonNode missingTenancyName = postJson("/api/services/app/Tenant/UpdateTenant", Map.of(
                "id", existing.id,
                "name", existing.name,
                "isActive", true
        ));

        assertThat(missingTenancyName.path("success").asBoolean()).isFalse();
        assertThat(missingTenancyName.path("error").path("message").asText()).isEqualTo("Validation failed");
    }

    @Test
    void updateTenantKeepsOriginalVoidResponseWhileSavingTenant() throws Exception {
        TenantItem existing = tenant("updatetenant" + System.nanoTime());
        existing.name = "Updated Tenant";
        existing.isActive = false;

        JsonNode response = postAbp("/api/services/app/Tenant/UpdateTenant", existing);

        assertThat(response.path("result").isNull()).isTrue();
        assertThat(store.tenant(existing.id)).hasValueSatisfying(tenant -> {
            assertThat(tenant.tenancyName).isEqualTo(existing.tenancyName);
            assertThat(tenant.name).isEqualTo("Updated Tenant");
            assertThat(tenant.isActive).isFalse();
        });
    }

    @Test
    void deleteEditionWithSubscribedTenantsReturnsOriginalError() throws Exception {
        EditionItem usedEdition = edition("Used Edition " + System.nanoTime());
        TenantItem tenant = tenant("usededitiontenant" + System.nanoTime());
        tenant.editionId = usedEdition.id;
        store.updateTenant(tenant);

        JsonNode response = deleteAbp("/api/services/app/Edition/DeleteEdition", Map.of("Id", String.valueOf(usedEdition.id)));

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText())
                .isEqualTo("There are tenants subscribed to this edition. Please assign a different edition to them and then delete this edition.");
        assertThat(store.edition(usedEdition.id)).isPresent();
    }

    @Test
    void editionComboboxIncludesOriginalDefaultItemAndSelectionState() throws Exception {
        JsonNode items = getAbp("/api/services/app/Edition/GetEditionComboboxItems").path("result");

        assertThat(items.get(0).path("value").asText()).isEmpty();
        assertThat(items.get(0).path("displayText").asText()).isEqualTo("Not assigned");
        assertThat(items.get(0).path("isFree").isNull()).isTrue();
        assertThat(items.get(0).path("isSelected").asBoolean()).isTrue();
    }

    private EditionItem edition(String displayName) {
        EditionItem item = new EditionItem();
        item.name = displayName.replaceAll("\\s+", "");
        item.displayName = displayName;
        item.monthlyPrice = BigDecimal.ZERO;
        item.annualPrice = BigDecimal.ZERO;
        return store.saveEdition(item, List.of());
    }

    private EditionItem paidEdition(String displayName) {
        EditionItem item = new EditionItem();
        item.name = displayName.replaceAll("\\s+", "");
        item.displayName = displayName;
        item.monthlyPrice = BigDecimal.TEN;
        item.annualPrice = BigDecimal.valueOf(100);
        return store.saveEdition(item, List.of());
    }

    private TenantItem tenant(String tenancyName) {
        TenantItem item = new TenantItem();
        item.tenancyName = tenancyName;
        item.name = tenancyName;
        item.adminEmailAddress = tenancyName + "@example.local";
        item.isActive = true;
        return store.createTenant(item);
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

    private JsonNode postJson(String url, Object payload) throws Exception {
        String body = mockMvc.perform(post(url)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(payload)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    private JsonNode deleteAbp(String url, Map<String, String> params) throws Exception {
        var request = delete(url).header("Authorization", "Bearer " + adminToken());
        params.forEach(request::param);
        String body = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    private JsonNode getAbp(String url) throws Exception {
        String body = mockMvc.perform(get(url)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isTrue();
        return response;
    }

    private JsonNode getRaw(String url, int skipCount, int maxResultCount) throws Exception {
        String body = mockMvc.perform(get(url)
                        .header("Authorization", "Bearer " + adminToken())
                        .param("SkipCount", String.valueOf(skipCount))
                        .param("MaxResultCount", String.valueOf(maxResultCount)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    private void assertValidationFailure(JsonNode response) {
        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText()).isEqualTo("Validation failed");
    }

    private String adminToken() {
        return authService.authenticate("admin", "123qwe").orElseThrow().token();
    }
}
