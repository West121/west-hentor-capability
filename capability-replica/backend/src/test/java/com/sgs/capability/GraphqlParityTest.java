package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/graphql-parity-store.json")
@AutoConfigureMockMvc
class GraphqlParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/graphql-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset GraphQL parity test store", ex);
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
    void graphqlRolesOrganizationUnitsAndUsersMatchOriginalQueryContainer() throws Exception {
        JsonNode roles = graphql(adminToken(), """
                { roles(name: "Admin") { id name displayName isStatic isDefault tenantId } }
                """);
        JsonNode role = roles.path("data").path("roles").get(0);
        assertThat(role.path("name").asText()).isEqualTo("Admin");
        assertThat(role.path("displayName").asText()).isEqualTo("管理员");
        assertThat(role.path("isStatic").asBoolean()).isTrue();
        assertThat(role.path("isDefault").asBoolean()).isTrue();
        assertThat(role.path("tenantId").isNull()).isTrue();

        JsonNode organizationUnits = graphql(adminToken(), """
                { organizationUnits(code: "00001.00002") { id code displayName tenantId } }
                """);
        JsonNode organizationUnit = organizationUnits.path("data").path("organizationUnits").get(0);
        assertThat(organizationUnit.path("id").asLong()).isEqualTo(2L);
        assertThat(organizationUnit.path("displayName").asText()).isEqualTo("化学检测");
        assertThat(organizationUnit.path("tenantId").isNull()).isTrue();

        JsonNode users = graphql(adminToken(), """
                { users(filter: "query", skipCount: 0, MaxResultCount: 1) {
                    totalCount items { id userName emailAddress isActive roles { id name displayName } organizationUnits { id code displayName } }
                  } }
                """);
        JsonNode result = users.path("data").path("users");
        JsonNode user = result.path("items").get(0);
        assertThat(result.path("totalCount").asInt()).isEqualTo(1);
        assertThat(user.path("userName").asText()).isEqualTo("query");
        assertThat(user.path("roles").get(0).path("name").asText()).isEqualTo("AbilityQuery");
        assertThat(user.path("organizationUnits").get(0).path("code").asText()).isEqualTo("00001.00002");
    }

    @Test
    void graphqlFieldAuthorizationUsesOriginalPermissionMessage() throws Exception {
        JsonNode response = graphql(queryUserToken(), """
                { roles { id name } }
                """);

        JsonNode error = response.path("errors").get(0);
        assertThat(error.path("message").asText())
                .isEqualTo("[ERR001] You don't have permission to access this resource! You need to be granted access the permission Pages.Administration.Roles.");
        assertThat(response.path("data").path("roles").isNull()).isTrue();
    }

    @Test
    void graphqlUserNestedRolesAndOrganizationUnitsOnlyRequireUserPermission() throws Exception {
        JsonNode response = graphql(userManagerToken(), """
                { users(filter: "admin", MaxResultCount: 1) {
                    items { userName roles { id name } organizationUnits { id code } }
                  } }
                """);

        assertThat(response.has("errors")).isFalse();
        JsonNode user = response.path("data").path("users").path("items").get(0);
        assertThat(user.path("userName").asText()).isEqualTo("admin");
        assertThat(user.path("roles").get(0).path("name").asText()).isEqualTo("Admin");
        assertThat(user.path("organizationUnits").get(0).path("code").asText()).isEqualTo("00001");
    }

    @Test
    void graphqlReturnsOnlySelectedFieldsLikeOriginalGraphqlRuntime() throws Exception {
        JsonNode response = graphql(adminToken(), """
                { users(filter: "admin", MaxResultCount: 1) {
                    items { userName roles { name } }
                  } }
                """);

        JsonNode user = response.path("data").path("users").path("items").get(0);
        assertThat(fieldNames(user)).containsExactly("userName", "roles");
        assertThat(fieldNames(user.path("roles").get(0))).containsExactly("name");
    }

    @Test
    void graphqlAcceptsVariablesLikeOriginalGraphqlServerTransport() throws Exception {
        JsonNode response = graphql(adminToken(), """
                query($roleName: String!, $take: Int!) {
                  roles(name: $roleName) { name displayName }
                  users(filter: "admin", MaxResultCount: $take) { items { userName } }
                }
                """, Map.of("roleName", "AbilityQuery", "take", 1));

        JsonNode roles = response.path("data").path("roles");
        assertThat(roles).hasSize(1);
        JsonNode role = roles.get(0);
        assertThat(role.path("name").asText()).isEqualTo("AbilityQuery");
        assertThat(role.path("displayName").asText()).isEqualTo("能力查询");
        assertThat(response.path("data").path("users").path("items")).hasSize(1);
    }

    @Test
    void graphqlUsesOperationNameWhenMultipleOperationsArePosted() throws Exception {
        JsonNode response = graphql(adminToken(), """
                query LoadRoles {
                  roles { name }
                }
                query LoadOrganizationUnits {
                  organizationUnits(code: "00001.00002") { code displayName }
                }
                """, Map.of(), "LoadOrganizationUnits");

        assertThat(response.path("data").has("roles")).isFalse();
        JsonNode organizationUnit = response.path("data").path("organizationUnits").get(0);
        assertThat(organizationUnit.path("code").asText()).isEqualTo("00001.00002");
        assertThat(organizationUnit.path("displayName").asText()).isEqualTo("化学检测");
    }

    @Test
    void graphqlGetTransportAcceptsQueryVariablesAndOperationName() throws Exception {
        String responseBody = mockMvc.perform(get("/graphql")
                        .header("Authorization", "Bearer " + adminToken())
                        .param("query", """
                                query LoadRoles {
                                  roles { name }
                                }
                                query LoadOrganizationUnit($code: String!) {
                                  organizationUnits(code: $code) { code displayName }
                                }
                                """)
                        .param("operationName", "LoadOrganizationUnit")
                        .param("variables", "{\"code\":\"00001.00002\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode response = objectMapper.readTree(responseBody);
        assertThat(response.path("data").has("roles")).isFalse();
        JsonNode organizationUnit = response.path("data").path("organizationUnits").get(0);
        assertThat(organizationUnit.path("code").asText()).isEqualTo("00001.00002");
        assertThat(organizationUnit.path("displayName").asText()).isEqualTo("化学检测");
    }

    @Test
    void graphqlSupportsAliasesLikeOriginalGraphqlRuntime() throws Exception {
        JsonNode response = graphql(adminToken(), """
                {
                  adminRoles: roles(name: "Admin") { roleName: name label: displayName }
                  accountUsers: users(filter: "admin", MaxResultCount: 1) {
                    count: totalCount
                    rows: items { login: userName assignedRoles: roles { roleName: name } }
                  }
                }
                """);

        assertThat(response.path("data").has("roles")).isFalse();
        JsonNode role = response.path("data").path("adminRoles").get(0);
        assertThat(fieldNames(role)).containsExactly("roleName", "label");
        assertThat(role.path("roleName").asText()).isEqualTo("Admin");
        assertThat(role.path("label").asText()).isEqualTo("管理员");

        JsonNode accountUsers = response.path("data").path("accountUsers");
        assertThat(accountUsers.path("count").asInt()).isEqualTo(1);
        JsonNode user = accountUsers.path("rows").get(0);
        assertThat(fieldNames(user)).containsExactly("login", "assignedRoles");
        assertThat(user.path("login").asText()).isEqualTo("admin");
        assertThat(user.path("assignedRoles").get(0).path("roleName").asText()).isEqualTo("Admin");
    }

    @Test
    void graphqlSupportsSchemaIntrospectionLikeOriginalGraphqlRuntime() throws Exception {
        JsonNode response = graphql(adminToken(), """
                {
                  __schema {
                    queryType { name fields { name } }
                    mutationType { name }
                  }
                  __type(name: "UserType") {
                    name
                    fields { name }
                  }
                }
                """);

        assertThat(response.has("errors")).isFalse();
        JsonNode schema = response.path("data").path("__schema");
        assertThat(schema.path("queryType").path("name").asText()).isEqualTo("QueryContainer");
        assertThat(schema.path("mutationType").isNull()).isTrue();
        assertThat(names(schema.path("queryType").path("fields")))
                .contains("roles", "organizationUnits", "users");

        JsonNode userType = response.path("data").path("__type");
        assertThat(userType.path("name").asText()).isEqualTo("UserType");
        assertThat(names(userType.path("fields")))
                .contains("id", "userName", "emailAddress", "roles", "organizationUnits");
    }

    @Test
    void graphqlPlaygroundUsesOriginalUiRoute() throws Exception {
        mockMvc.perform(get("/ui/playground"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/graphql")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("GraphQL Playground")));
    }

    private JsonNode graphql(String token, String query) throws Exception {
        return graphql(token, query, Map.of());
    }

    private JsonNode graphql(String token, String query, Map<String, Object> variables) throws Exception {
        return graphql(token, query, variables, null);
    }

    private JsonNode graphql(String token, String query, Map<String, Object> variables, String operationName) throws Exception {
        Map<String, Object> requestBody = new java.util.LinkedHashMap<>();
        requestBody.put("query", query);
        requestBody.put("variables", variables);
        if (operationName != null) {
            requestBody.put("operationName", operationName);
        }
        String responseBody = mockMvc.perform(post("/graphql")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(requestBody)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(responseBody);
    }

    private List<String> fieldNames(JsonNode node) {
        List<String> names = new java.util.ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private List<String> names(JsonNode nodes) {
        List<String> names = new java.util.ArrayList<>();
        nodes.forEach(node -> names.add(node.path("name").asText()));
        return names;
    }

    private String adminToken() {
        return authService.authenticate("admin", "123qwe").orElseThrow().token();
    }

    private String queryUserToken() {
        return authService.authenticate("query", "123qwe").orElseThrow().token();
    }

    private String userManagerToken() {
        RoleItem role = new RoleItem();
        role.name = "GraphqlUserManager";
        role.displayName = "GraphQL user manager";
        store.saveRole(role, List.of("Pages.Administration.Users"));

        UserItem user = new UserItem();
        user.name = "GraphQL";
        user.surname = "UserManager";
        user.userName = "graphql-user-manager";
        user.emailAddress = "graphql-user-manager@example.local";
        user.phoneNumber = "13700000001";
        user.isActive = true;
        user.isEmailConfirmed = true;
        user.creationTime = LocalDateTime.now();
        store.saveUser(user, List.of(role.name), List.of(), List.of());

        return authService.authenticate(user.userName, "123qwe").orElseThrow().token();
    }
}
