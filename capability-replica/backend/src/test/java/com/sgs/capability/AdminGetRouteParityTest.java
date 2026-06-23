package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.model.EditionItem;
import com.sgs.capability.model.LanguageItem;
import com.sgs.capability.model.RoleItem;
import com.sgs.capability.model.TenantItem;
import com.sgs.capability.model.UserItem;
import com.sgs.capability.security.AuthService;
import com.sgs.capability.service.CapabilityStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/admin-get-route-parity-store.json")
@AutoConfigureMockMvc
class AdminGetRouteParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/admin-get-route-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset admin GET route parity test store", ex);
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
    void roleReadRoutesAcceptOriginalGetQueryParameters() throws Exception {
        RoleItem adminRole = store.roles(null).stream()
                .filter(role -> "Admin".equals(role.name))
                .findFirst()
                .orElseThrow();

        JsonNode roles = getAbp(get("/api/services/app/Role/GetRoles")
                .param("Permissions", "Pages.Administration.Users")
                .param("Permissions", "Pages.Administration.Roles")).path("result").path("items");
        assertThat(roles).anySatisfy(role -> assertThat(role.path("id").asInt()).isEqualTo(adminRole.id));

        JsonNode edit = getAbp(get("/api/services/app/Role/GetRoleForEdit")
                .param("Id", adminRole.id.toString())).path("result");
        assertThat(edit.path("role").path("id").asInt()).isEqualTo(adminRole.id);
        assertThat(edit.path("permissions").isArray()).isTrue();
    }

    @Test
    void userReadRoutesAcceptOriginalGetQueryParameters() throws Exception {
        RoleItem adminRole = store.roles(null).stream()
                .filter(role -> "Admin".equals(role.name))
                .findFirst()
                .orElseThrow();
        UserItem adminUser = store.users("admin", 0, 10).items.stream().findFirst().orElseThrow();

        JsonNode users = getAbp(get("/api/services/app/User/GetUsers")
                .param("Filter", "admin")
                .param("Permissions", "Pages.Administration.Users")
                .param("Role", adminRole.id.toString())
                .param("OnlyLockedUsers", "false")
                .param("Sorting", "userName ASC")
                .param("MaxResultCount", "10")
                .param("SkipCount", "0")).path("result");
        assertThat(users.path("totalCount").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(users.path("items")).anySatisfy(user -> assertThat(user.path("id").asLong()).isEqualTo(adminUser.id));

        JsonNode file = getAbp(get("/api/services/app/User/GetUsersToExcel")
                .param("Filter", "admin")
                .param("Permissions", "Pages.Administration.Users")
                .param("Role", adminRole.id.toString())
                .param("OnlyLockedUsers", "false")
                .param("Sorting", "userName ASC")).path("result");
        assertThat(file.path("fileName").asText()).isEqualTo("UserList.xlsx");
        assertThat(file.path("fileToken").asText()).isNotBlank();

        JsonNode edit = getAbp(get("/api/services/app/User/GetUserForEdit")
                .param("Id", adminUser.id.toString())).path("result");
        assertThat(edit.path("user").path("id").asLong()).isEqualTo(adminUser.id);
        assertThat(edit.path("roles").isArray()).isTrue();

        JsonNode permissions = getAbp(get("/api/services/app/User/GetUserPermissionsForEdit")
                .param("Id", adminUser.id.toString())).path("result");
        assertThat(permissions.path("permissions").isArray()).isTrue();
        assertThat(permissions.path("grantedPermissionNames").isArray()).isTrue();
    }

    @Test
    void userReadRouteDefaultsToOriginalNameSorting() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        UserItem zulu = user("sort_z_" + suffix, "Zulu " + suffix);
        UserItem alpha = user("sort_a_" + suffix, "Alpha " + suffix);

        JsonNode items = getAbp(get("/api/services/app/User/GetUsers")
                .param("Filter", suffix)
                .param("MaxResultCount", "10")
                .param("SkipCount", "0")).path("result").path("items");

        assertThat(items.get(0).path("id").asLong()).isEqualTo(alpha.id);
        assertThat(items.get(items.size() - 1).path("id").asLong()).isEqualTo(zulu.id);
    }

    @Test
    void languageReadRouteAcceptsOriginalGetQueryId() throws Exception {
        LanguageItem language = store.languages().stream().findFirst().orElseThrow();

        JsonNode edit = getAbp(get("/api/services/app/Language/GetLanguageForEdit")
                .param("Id", language.id.toString())).path("result");

        assertThat(edit.path("language").path("id").asInt()).isEqualTo(language.id);
    }

    @Test
    void tenantReadRoutesAcceptOriginalGetQueryParameters() throws Exception {
        EditionItem edition = edition("GET Tenant Edition " + System.nanoTime());
        TenantItem tenant = tenant("gettenant" + System.nanoTime(), edition.id);

        JsonNode tenants = getAbp(get("/api/services/app/Tenant/GetTenants")
                .param("Filter", tenant.tenancyName)
                .param("SubscriptionEndDateStart", "2026-01-01T00:00:00.000Z")
                .param("SubscriptionEndDateEnd", "2026-12-31T23:59:59.000Z")
                .param("CreationDateStart", "2026-01-01T00:00:00.000Z")
                .param("CreationDateEnd", "2026-12-31T23:59:59.000Z")
                .param("EditionId", edition.id.toString())
                .param("EditionIdSpecified", "true")
                .param("Sorting", "tenancyName ASC")
                .param("MaxResultCount", "10")
                .param("SkipCount", "0")).path("result");
        assertThat(tenants.path("items")).anySatisfy(item -> assertThat(item.path("id").asInt()).isEqualTo(tenant.id));

        JsonNode edit = getAbp(get("/api/services/app/Tenant/GetTenantForEdit")
                .param("Id", tenant.id.toString())).path("result");
        assertThat(edit.path("id").asInt()).isEqualTo(tenant.id);

        JsonNode features = getAbp(get("/api/services/app/Tenant/GetTenantFeaturesForEdit")
                .param("Id", tenant.id.toString())).path("result");
        assertThat(features.path("featureValues").isArray()).isTrue();
        assertThat(features.path("features").isArray()).isTrue();
    }

    @Test
    void tenantReadRouteSortsByOriginalEditionDisplayNameAlias() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        EditionItem zeta = edition("Zeta Tenant Sort " + suffix);
        EditionItem alpha = edition("Alpha Tenant Sort " + suffix);
        TenantItem firstByName = tenant("a_tenant_sort_" + suffix, zeta.id);
        TenantItem firstByEdition = tenant("z_tenant_sort_" + suffix, alpha.id);

        JsonNode items = getAbp(get("/api/services/app/Tenant/GetTenants")
                .param("Filter", suffix)
                .param("Sorting", "editionDisplayName ASC")
                .param("MaxResultCount", "10")
                .param("SkipCount", "0")).path("result").path("items");

        assertThat(items.get(0).path("id").asInt()).isEqualTo(firstByEdition.id);
        assertThat(items.get(items.size() - 1).path("id").asInt()).isEqualTo(firstByName.id);
    }

    @Test
    void editionReadRoutesAcceptOriginalGetQueryParameters() throws Exception {
        EditionItem edition = edition("GET Edition " + System.nanoTime());
        tenant("editioncount" + System.nanoTime(), edition.id);

        JsonNode edit = getAbp(get("/api/services/app/Edition/GetEditionForEdit")
                .param("Id", edition.id.toString())).path("result");
        assertThat(edit.path("edition").path("id").asInt()).isEqualTo(edition.id);
        assertThat(edit.path("features").isArray()).isTrue();

        JsonNode count = getAbp(get("/api/services/app/Edition/GetTenantCount")
                .param("editionId", edition.id.toString())).path("result");
        assertThat(count.asInt()).isEqualTo(1);

        EditionItem freeEdition = edition("GET Free Combo " + System.nanoTime());
        EditionItem paidEdition = paidEdition("GET Paid Combo " + System.nanoTime());
        JsonNode combobox = getAbp(get("/api/services/app/Edition/GetEditionComboboxItems")
                .param("selectedEditionId", freeEdition.id.toString())
                .param("addAllItem", "true")
                .param("onlyFreeItems", "true")).path("result");
        assertThat(combobox.get(0).path("displayText").asText()).isEqualTo("全部");
        assertThat(combobox).anySatisfy(item -> assertThat(item.path("value").asText()).isEqualTo(String.valueOf(freeEdition.id)));
        assertThat(combobox).noneSatisfy(item -> assertThat(item.path("value").asText()).isEqualTo(String.valueOf(paidEdition.id)));
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

    private TenantItem tenant(String tenancyName, Integer editionId) {
        TenantItem item = new TenantItem();
        item.tenancyName = tenancyName;
        item.name = tenancyName;
        item.adminEmailAddress = tenancyName + "@example.local";
        item.editionId = editionId;
        item.isActive = true;
        return store.createTenant(item);
    }

    private UserItem user(String userName, String name) {
        UserItem user = new UserItem();
        user.userName = userName;
        user.name = name;
        user.surname = "Sort";
        user.emailAddress = userName + "@example.local";
        user.phoneNumber = "13700009999";
        user.isActive = true;
        return store.saveUser(user, List.of("User"), List.of(), List.of());
    }

    private JsonNode getAbp(MockHttpServletRequestBuilder request) throws Exception {
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
