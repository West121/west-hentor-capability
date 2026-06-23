package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.security.AuthService;
import com.sgs.capability.service.CapabilityStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/appservice-direct-method-smoke-store.json")
@AutoConfigureMockMvc
class AppServiceDirectMethodSmokeParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/appservice-direct-method-smoke-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset AppService direct method smoke test store", ex);
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
    void decompiledAppServiceMethodsWithPreviouslyIndirectCoverageExposeAbpRoutes() throws Exception {
        long newRegistrationPaymentId = paidPaymentId();
        long upgradePaymentId = paidPaymentId();
        long extendPaymentId = paidPaymentId();
        long stripePaymentId = store.createPayment(2, 1, 30, 2, true, "ok", "error").id;
        String stripeSessionId = store.createStripePaymentSession(stripePaymentId);

        List<RouteCall> calls = List.of(
                getRoute("/api/services/app/AuditLog/GetEntityHistoryObjectTypes"),
                postJson("/api/services/app/User/ResetUserSpecificPermissions", Map.of("id", "2")),
                postJson("/api/services/app/User/UnlockUser", Map.of("id", "2")),
                getRoute("/api/services/app/UserLink/GetRecentlyUsedLinkedUsers"),
                getRoute("/api/services/app/UserLogin/GetRecentUserLoginAttempts"),
                getRoute("/api/services/app/UserDelegation/GetActiveUserDelegations"),
                postJson("/api/services/app/Caching/ClearCache", Map.of("id", "AbpZeroTenantCache")),
                postRoute("/api/services/app/Caching/ClearAllCaches"),
                postJson("/api/services/app/Chat/MarkAllUnreadMessagesOfUserAsRead", Map.of("userId", 2, "tenantId", 1)),
                getRoute("/api/services/app/CommonLookup/GetDefaultEditionName"),
                getRoute("/api/services/app/HostSettings/GetAbilitySettings"),
                postJson("/api/services/app/DemoUiComponents/SendAndGetSelectedCountries", Map.of(
                        "selectedCountries", List.of(Map.of("name", "China", "value", "CN"))
                )),
                getRoute("/api/services/app/DynamicEntityParameterDefinition/GetAllAllowedInputTypeNames"),
                getRoute("/api/services/app/DynamicEntityParameterDefinition/GetAllEntities"),
                postJson("/api/services/app/Tenant/ResetTenantSpecificFeatures", Map.of("id", "1")),
                postJson("/api/services/app/Tenant/UnlockTenantAdmin", Map.of("id", "1")),
                getRoute("/api/services/app/HostDashboard/GetRecentTenantsData"),
                postJson("/api/services/app/Payment/GetPaymentAsync", Map.of("id", String.valueOf(newRegistrationPaymentId))),
                postParam("/api/services/app/Payment/NewRegistrationSucceed", "paymentId", String.valueOf(newRegistrationPaymentId)),
                postParam("/api/services/app/Payment/UpgradeSucceed", "paymentId", String.valueOf(upgradePaymentId)),
                postParam("/api/services/app/Payment/ExtendSucceed", "paymentId", String.valueOf(extendPaymentId)),
                postJson("/api/services/app/StripePayment/GetPaymentAsync", Map.of("stripeSessionId", stripeSessionId)),
                postJson("/api/services/app/StripePayment/CreatePaymentSession", Map.of("paymentId", stripePaymentId)),
                postRoute("/api/services/app/Notification/SetAllNotificationsAsRead"),
                getRoute("/api/services/app/OrganizationUnit/GetOrganizationUnits"),
                getRoute("/api/services/app/TenantDashboard/GetDailySales"),
                getRoute("/api/services/app/TenantDashboard/GetProfitShare"),
                getRoute("/api/services/app/TenantDashboard/GetGeneralStats"),
                postJson("/api/services/app/WebhookSubscription/ActivateWebhookSubscription", Map.of("subscriptionId", "missing", "isActive", false)),
                getRoute("/api/services/app/WebhookSubscription/GetAllAvailableWebhooks"),
                getRoute("/api/services/app/Ability/GetMyOrgSetting"),
                getRoute("/api/services/app/Ability/GetAllUnits"),
                postJson("/api/services/app/AbilityQuery/FindHistory", Map.of("id", "missing")),
                postRoute("/api/services/app/UiCustomizationSettings/UseSystemDefaultSettings")
        );

        for (RouteCall call : calls) {
            JsonNode response = abp(call);
            assertThat(response.has("success"))
                    .as("Expected ABP envelope for %s %s", call.method, call.route)
                    .isTrue();
        }
    }

    private long paidPaymentId() {
        long paymentId = store.createPayment(2, 1, 30, 2, true, "ok", "error").id;
        store.markPaymentStatus(paymentId, 2);
        return paymentId;
    }

    private JsonNode abp(RouteCall call) throws Exception {
        String body = mockMvc.perform(request(call).header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    private MockHttpServletRequestBuilder request(RouteCall call) throws Exception {
        MockHttpServletRequestBuilder request = switch (call.method) {
            case "GET" -> get(call.route);
            case "POST" -> post(call.route);
            default -> throw new IllegalArgumentException("Unsupported method " + call.method);
        };
        call.params.forEach(request::param);
        if (call.body != null) {
            request.contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(call.body));
        }
        return request;
    }

    private RouteCall getRoute(String route) {
        return new RouteCall("GET", route, Map.of(), null);
    }

    private RouteCall postRoute(String route) {
        return new RouteCall("POST", route, Map.of(), null);
    }

    private RouteCall postJson(String route, Object body) {
        return new RouteCall("POST", route, Map.of(), body);
    }

    private RouteCall postParam(String route, String name, String value) {
        return new RouteCall("POST", route, Map.of(name, value), null);
    }

    private String adminToken() {
        return authService.authenticate("admin", "123qwe").orElseThrow().token();
    }

    private record RouteCall(String method, String route, Map<String, String> params, Object body) {
    }
}
