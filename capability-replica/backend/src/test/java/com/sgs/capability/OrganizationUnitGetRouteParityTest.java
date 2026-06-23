package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.model.OrganizationUnit;
import com.sgs.capability.model.RoleItem;
import com.sgs.capability.model.UserItem;
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

@SpringBootTest(properties = "replica.store.path=target/test-data/organization-unit-get-route-parity-store.json")
@AutoConfigureMockMvc
class OrganizationUnitGetRouteParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/organization-unit-get-route-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset organization unit GET route parity test store", ex);
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
    void organizationUnitMembershipRoutesAcceptOriginalGetQueryParameters() throws Exception {
        // Original generated client sends these read calls as GET with PascalCase query keys.
        JsonNode firstUserPage = getAbp(get("/api/services/app/OrganizationUnit/GetOrganizationUnitUsers")
                .param("Id", "2")
                .param("Sorting", "userName ASC")
                .param("MaxResultCount", "1")
                .param("SkipCount", "0")).path("result");

        assertThat(firstUserPage.path("totalCount").asInt()).isEqualTo(2);
        assertThat(firstUserPage.path("items")).hasSize(1);
        long firstUserId = firstUserPage.path("items").get(0).path("id").asLong();

        JsonNode secondUserPage = getAbp(get("/api/services/app/OrganizationUnit/GetOrganizationUnitUsers")
                .param("Id", "2")
                .param("Sorting", "userName ASC")
                .param("MaxResultCount", "1")
                .param("SkipCount", "1")).path("result");

        assertThat(secondUserPage.path("items")).hasSize(1);
        assertThat(secondUserPage.path("items").get(0).path("id").asLong()).isNotEqualTo(firstUserId);

        JsonNode rolePage = getAbp(get("/api/services/app/OrganizationUnit/GetOrganizationUnitRoles")
                .param("Id", "2")
                .param("Sorting", "displayName ASC")
                .param("MaxResultCount", "1")
                .param("SkipCount", "0")).path("result");

        assertThat(rolePage.path("totalCount").asInt()).isEqualTo(1);
        assertThat(rolePage.path("items")).hasSize(1);
        assertThat(rolePage.path("items").get(0).path("name").asText()).isEqualTo("AbilityQuery");
    }

    @Test
    void organizationUnitMembershipRoutesSortByOriginalAliases() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        OrganizationUnit unit = new OrganizationUnit();
        unit.displayName = "Sorting Org " + suffix;
        unit = store.saveOrganizationUnit(unit);

        UserItem zUser = user("z_org_user_" + suffix, unit.id);
        UserItem aUser = user("a_org_user_" + suffix, unit.id);
        RoleItem zRole = role("ZOrgRole" + suffix, "Z Org Role " + suffix, unit.id);
        RoleItem aRole = role("AOrgRole" + suffix, "A Org Role " + suffix, unit.id);

        JsonNode users = getAbp(get("/api/services/app/OrganizationUnit/GetOrganizationUnitUsers")
                .param("Id", String.valueOf(unit.id))
                .param("Sorting", "userName ASC")
                .param("MaxResultCount", "10")
                .param("SkipCount", "0")).path("result").path("items");

        assertThat(users.get(0).path("id").asLong()).isEqualTo(aUser.id);
        assertThat(users.get(users.size() - 1).path("id").asLong()).isEqualTo(zUser.id);

        JsonNode roles = getAbp(get("/api/services/app/OrganizationUnit/GetOrganizationUnitRoles")
                .param("Id", String.valueOf(unit.id))
                .param("Sorting", "displayName ASC")
                .param("MaxResultCount", "10")
                .param("SkipCount", "0")).path("result").path("items");

        assertThat(roles.get(0).path("id").asLong()).isEqualTo(aRole.id.longValue());
        assertThat(roles.get(roles.size() - 1).path("id").asLong()).isEqualTo(zRole.id.longValue());
    }

    @Test
    void organizationUnitMembershipRoutesRejectOriginalDtoRangeViolations() throws Exception {
        assertValidationFailure(getRaw(get("/api/services/app/OrganizationUnit/GetOrganizationUnitUsers")
                .param("MaxResultCount", "10")
                .param("SkipCount", "0")));

        assertValidationFailure(getRaw(get("/api/services/app/OrganizationUnit/GetOrganizationUnitUsers")
                .param("Id", "0")
                .param("MaxResultCount", "10")
                .param("SkipCount", "0")));

        assertValidationFailure(getRaw(get("/api/services/app/OrganizationUnit/GetOrganizationUnitRoles")
                .param("Id", "2")
                .param("MaxResultCount", "0")
                .param("SkipCount", "0")));

        assertValidationFailure(getRaw(get("/api/services/app/OrganizationUnit/GetOrganizationUnitRoles")
                .param("Id", "2")
                .param("MaxResultCount", "10")
                .param("SkipCount", "-1")));
    }

    @Test
    void organizationUnitFindRoutesRejectOriginalPagedInputRangeViolations() throws Exception {
        assertValidationFailure(getRaw(post("/api/services/app/OrganizationUnit/FindUsers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of(
                        "organizationUnitId", 2,
                        "skipCount", 0,
                        "maxResultCount", 0
                )))));

        assertValidationFailure(getRaw(post("/api/services/app/OrganizationUnit/FindUsers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of(
                        "organizationUnitId", 2,
                        "skipCount", 0,
                        "maxResultCount", 1001
                )))));

        assertValidationFailure(getRaw(post("/api/services/app/OrganizationUnit/FindRoles")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of(
                        "organizationUnitId", 2,
                        "skipCount", -1,
                        "maxResultCount", 10
                )))));
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

    private JsonNode getRaw(MockHttpServletRequestBuilder request) throws Exception {
        String body = mockMvc.perform(request.header("Authorization", "Bearer " + adminToken()))
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

    private UserItem user(String userName, Long organizationUnitId) {
        UserItem user = new UserItem();
        user.userName = userName;
        user.name = userName;
        user.surname = "Member";
        user.emailAddress = userName + "@example.test";
        user.isActive = true;
        return store.saveUser(user, List.of(), List.of(organizationUnitId), List.of());
    }

    private RoleItem role(String name, String displayName, Long organizationUnitId) {
        RoleItem role = new RoleItem();
        role.name = name;
        role.displayName = displayName;
        role.organizationUnits = List.of(organizationUnitId);
        return store.saveRole(role, List.of());
    }

    private String adminToken() {
        return authService.authenticate("admin", "123qwe").orElseThrow().token();
    }
}
