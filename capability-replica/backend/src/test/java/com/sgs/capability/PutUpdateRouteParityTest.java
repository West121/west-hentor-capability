package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.dto.FindAbilityRequest;
import com.sgs.capability.model.*;
import com.sgs.capability.security.AuthService;
import com.sgs.capability.service.CapabilityStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/put-update-route-parity-store.json")
@AutoConfigureMockMvc
class PutUpdateRouteParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/put-update-route-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset PUT update route parity test store", ex);
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
    void businessUpdateRoutesAcceptOriginalPutContracts() throws Exception {
        Ability ability = store.saveAbility(ability("PUT-ABILITY-" + System.nanoTime(), "重量法"));
        ability.methodName = "PUT容量法";

        assertThat(abp(putJson("/api/services/app/Ability/UpdateAbility", ability)).path("result").isNull()).isTrue();
        assertThat(store.getAbility(ability.id.toString())).hasValueSatisfying(saved ->
                assertThat(saved.methodName).isEqualTo("PUT容量法"));

        OrganizationUnit unit = new OrganizationUnit();
        unit.displayName = "PUT Org Before";
        unit = store.saveOrganizationUnit(unit);
        unit.displayName = "PUT Org After";

        JsonNode orgResponse = abp(putJson("/api/services/app/OrganizationUnit/UpdateOrganizationUnit", unit));
        assertThat(orgResponse.path("result").path("displayName").asText()).isEqualTo("PUT Org After");
    }

    @Test
    void profileAndSessionUpdateRoutesAcceptOriginalPutContracts() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        Map<String, Object> profile = Map.of(
                "name", "PutProfile" + suffix,
                "surname", "Parity",
                "userName", "admin",
                "emailAddress", "put-profile-" + suffix + "@example.local",
                "phoneNumber", "138" + suffix.substring(Math.max(0, suffix.length() - 8)),
                "engName", "Put Profile"
        );

        assertThat(abp(putJson("/api/services/app/Profile/UpdateCurrentUserProfile", profile)).path("result").isNull()).isTrue();
        JsonNode savedProfile = abp(get("/api/services/app/Profile/GetCurrentUserProfileForEdit")).path("result");
        assertThat(savedProfile.path("name").asText()).isEqualTo(profile.get("name"));

        assertThat(abp(putJson("/api/services/app/Profile/UpdateProfilePicture", Map.of(
                "fileToken", "data:image/png;base64,UE5H",
                "x", 0,
                "y", 0,
                "width", 1,
                "height", 1
        ))).path("result").isNull()).isTrue();
        assertThat(abp(get("/api/services/app/Profile/GetProfilePicture")).path("result").path("profilePicture").asText())
                .startsWith("data:image/png;base64");

        assertThat(abp(putNoBody("/api/services/app/Profile/UpdateGoogleAuthenticatorKey"))
                .path("result").path("qrCodeSetupImageUrl").asText()).startsWith("data:image/svg+xml;base64,");

        assertThat(abp(putNoBody("/api/services/app/Session/UpdateUserSignInToken"))
                .path("result").path("signInToken").asText()).isNotBlank();
    }

    @Test
    void dynamicParameterUpdateRoutesAcceptOriginalPutContracts() throws Exception {
        DynamicParameterItem parameter = dynamicParameter("PutDynamic" + System.nanoTime());
        parameter.displayName = "Updated By PUT";
        parameter.inputType = "COMBOBOX";

        assertThat(abp(putJson("/api/services/app/DynamicParameter/Update", parameter)).path("result").isNull()).isTrue();
        assertThat(store.dynamicParameter(parameter.id)).hasValueSatisfying(saved ->
                assertThat(saved.displayName).isEqualTo("Updated By PUT"));

        DynamicParameterValueItem value = dynamicParameterValue(parameter.id, "Before");
        value.value = "After";

        assertThat(abp(putJson("/api/services/app/DynamicParameterValue/Update", value)).path("result").isNull()).isTrue();
        assertThat(store.dynamicParameterValue(value.id)).hasValueSatisfying(saved ->
                assertThat(saved.value).isEqualTo("After"));

        DynamicParameterItem entityParameter = dynamicParameter("PutEntityDynamic" + System.nanoTime());
        EntityDynamicParameterItem mapping = entityDynamicParameter("Capability.PutEntity", entityParameter.id);
        mapping.entityFullName = "Capability.PutEntityRenamed";

        assertThat(abp(putJson("/api/services/app/EntityDynamicParameter/Update", mapping)).path("result").isNull()).isTrue();
        assertThat(store.entityDynamicParameter(mapping.id)).hasValueSatisfying(saved ->
                assertThat(saved.entityFullName).isEqualTo("Capability.PutEntityRenamed"));

        EntityDynamicParameterValueItem entityValue = entityDynamicParameterValue(mapping.id, "entity-put", "Before");
        entityValue.value = "After";

        assertThat(abp(putJson("/api/services/app/EntityDynamicParameterValue/Update", entityValue)).path("result").isNull()).isTrue();
        assertThat(store.entityDynamicParameterValue(entityValue.id)).hasValueSatisfying(saved ->
                assertThat(saved.value).isEqualTo("After"));
    }

    @Test
    void administrationUpdateRoutesAcceptOriginalPutContracts() throws Exception {
        JsonNode permissions = abp(putJson("/api/services/app/User/UpdateUserPermissions", Map.of(
                "id", 1,
                "grantedPermissionNames", List.of("Pages.Administration.Users")
        )));
        assertThat(permissions.path("result").isNull()).isTrue();
        assertThat(store.userSpecificPermissionNames(1L)).contains("Pages.Administration.Users");

        String key = "Put.Language." + System.nanoTime();
        assertThat(abp(putJson("/api/services/app/Language/UpdateLanguageText", Map.of(
                "languageName", "zh-Hans",
                "sourceName", "CapabilityTable",
                "key", key,
                "baseValue", "PUT language base",
                "targetValue", "PUT语言"
        ))).path("result").isNull()).isTrue();
        assertThat(store.languageTexts("zh-Hans", key, 0, 10).items).anySatisfy(text ->
                assertThat(text.targetValue).isEqualTo("PUT语言"));

        WebhookSubscriptionItem subscription = webhookSubscription("https://example.local/webhook/put-before");
        subscription.webhookUri = "https://example.local/webhook/put-after";

        assertThat(abp(putJson("/api/services/app/WebhookSubscription/UpdateSubscription", subscription)).path("result").isNull()).isTrue();
        assertThat(store.webhookSubscription(subscription.id.toString())).hasValueSatisfying(saved ->
                assertThat(saved.webhookUri).isEqualTo("https://example.local/webhook/put-after"));

        TenantItem tenant = tenant("puttenant" + System.nanoTime());
        tenant.name = "PUT Tenant";
        tenant.isActive = false;

        assertThat(abp(putJson("/api/services/app/Tenant/UpdateTenant", tenant)).path("result").isNull()).isTrue();
        assertThat(store.tenant(tenant.id)).hasValueSatisfying(saved ->
                assertThat(saved.name).isEqualTo("PUT Tenant"));

        String featureName = store.features().get(0).name;
        assertThat(abp(putJson("/api/services/app/Tenant/UpdateTenantFeatures", Map.of(
                "id", tenant.id,
                "featureValues", List.of(nameValue(featureName, "false"))
        ))).path("result").isNull()).isTrue();
        assertThat(store.tenantFeatureValues(tenant.id)).anySatisfy(feature -> {
            assertThat(feature.name).isEqualTo(featureName);
            assertThat(feature.value).isEqualTo("false");
        });

        EditionItem edition = edition("PUT Edition " + System.nanoTime());
        edition.displayName = "PUT Edition Updated";
        edition.monthlyPrice = BigDecimal.valueOf(88);

        assertThat(abp(putJson("/api/services/app/Edition/UpdateEdition", Map.of(
                "edition", edition,
                "featureValues", List.of()
        ))).path("result").isNull()).isTrue();
        assertThat(store.edition(edition.id)).hasValueSatisfying(saved ->
                assertThat(saved.displayName).isEqualTo("PUT Edition Updated"));
    }

    private Ability ability(String marker, String methodName) {
        return objectMapper.convertValue(abilityPayload(marker, methodName), Ability.class);
    }

    private Map<String, Object> abilityPayload(String marker, String methodName) {
        return Map.ofEntries(
                entry("orgId", 2),
                entry("typeName", "矿石"),
                entry("samplingName", marker),
                entry("testItem", marker),
                entry("methodName", methodName),
                entry("methodEngName", "Put method"),
                entry("standardNo", marker),
                entry("cycleWorkingDay", "5"),
                entry("massRequired", "100"),
                entry("sizeRequired", "0.074"),
                entry("detectionLimit", "0.01%"),
                entry("price", "100")
        );
    }

    private DynamicParameterItem dynamicParameter(String parameterName) {
        DynamicParameterItem item = new DynamicParameterItem();
        item.parameterName = parameterName;
        item.displayName = parameterName;
        item.inputType = "SINGLE_LINE_STRING";
        item.permission = "";
        return store.saveDynamicParameter(item);
    }

    private DynamicParameterValueItem dynamicParameterValue(Integer dynamicParameterId, String value) {
        DynamicParameterValueItem item = new DynamicParameterValueItem();
        item.dynamicParameterId = dynamicParameterId;
        item.value = value;
        return store.saveDynamicParameterValue(item);
    }

    private EntityDynamicParameterItem entityDynamicParameter(String entityFullName, Integer dynamicParameterId) {
        EntityDynamicParameterItem item = new EntityDynamicParameterItem();
        item.entityFullName = entityFullName;
        item.dynamicParameterId = dynamicParameterId;
        return store.saveEntityDynamicParameter(item);
    }

    private EntityDynamicParameterValueItem entityDynamicParameterValue(Integer entityDynamicParameterId,
                                                                        String entityId,
                                                                        String value) {
        EntityDynamicParameterValueItem item = new EntityDynamicParameterValueItem();
        item.entityDynamicParameterId = entityDynamicParameterId;
        item.entityId = entityId;
        item.value = value;
        return store.saveEntityDynamicParameterValue(item);
    }

    private WebhookSubscriptionItem webhookSubscription(String webhookUri) {
        WebhookSubscriptionItem item = new WebhookSubscriptionItem();
        item.webhookUri = webhookUri;
        item.isActive = true;
        item.webhooks = List.of("App.TestWebhook");
        item.headers = new LinkedHashMap<>();
        return store.saveWebhookSubscription(item);
    }

    private TenantItem tenant(String tenancyName) {
        TenantItem item = new TenantItem();
        item.tenancyName = tenancyName;
        item.name = tenancyName;
        item.adminEmailAddress = tenancyName + "@example.local";
        item.isActive = true;
        return store.createTenant(item);
    }

    private EditionItem edition(String displayName) {
        EditionItem item = new EditionItem();
        item.name = displayName.replaceAll("\\s+", "");
        item.displayName = displayName;
        item.monthlyPrice = BigDecimal.ZERO;
        item.annualPrice = BigDecimal.ZERO;
        return store.saveEdition(item, List.of());
    }

    private NameValueItem nameValue(String name, String value) {
        NameValueItem item = new NameValueItem();
        item.name = name;
        item.value = value;
        return item;
    }

    private MockHttpServletRequestBuilder putJson(String url, Object payload) throws Exception {
        return put(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(payload));
    }

    private MockHttpServletRequestBuilder putNoBody(String url) {
        return put(url);
    }

    private JsonNode abp(MockHttpServletRequestBuilder request) throws Exception {
        String body = mockMvc.perform(request.header("Authorization", "Bearer " + adminToken()))
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
