package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.model.PermissionItem;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/role-edit-parity-store.json")
@AutoConfigureMockMvc
class RoleEditParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/role-edit-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset role edit parity test store", ex);
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
    void getRoleForEditReturnsOriginalFlatPermissionDtoShapeSortedByDisplayName() throws Exception {
        store.permissions().add(testPermission("Pages.Test.Z", "ZZZ role parity permission"));
        store.permissions().add(testPermission("Pages.Test.A", "AAA role parity permission"));

        JsonNode result = postAbp("/api/services/app/Role/GetRoleForEdit", Map.of()).path("result");

        assertThat(result.path("role").isObject()).isTrue();
        assertThat(result.path("role").path("displayName").isNull()).isTrue();
        assertThat(result.path("role").path("isDefault").asBoolean()).isFalse();
        assertThat(result.path("grantedPermissionNames")).isEmpty();

        JsonNode permissions = result.path("permissions");
        assertThat(indexOfPermission(permissions, "Pages.Test.A")).isLessThan(indexOfPermission(permissions, "Pages.Test.Z"));
        JsonNode testPermission = permissionNamed(permissions, "Pages.Test.A");
        assertThat(testPermission.has("parentName")).isTrue();
        assertThat(testPermission.has("description")).isTrue();
        assertThat(testPermission.has("isGrantedByDefault")).isTrue();
        assertThat(testPermission.path("isGrantedByDefault").asBoolean()).isFalse();
    }

    @Test
    void createOrUpdateRoleKeepsOriginalVoidResponseWhileSavingRole() throws Exception {
        JsonNode response = postAbp("/api/services/app/Role/CreateOrUpdateRole", Map.of(
                "role", Map.of(
                        "displayName", "Role Void Response",
                        "isDefault", false
                ),
                "grantedPermissionNames", List.of("Pages.AbilityQuery")
        ));

        assertThat(response.path("result").isNull()).isTrue();
        assertThat(store.roles("Role Void Response")).anySatisfy(role -> {
            assertThat(role.displayName).isEqualTo("Role Void Response");
            assertThat(role.grantedPermissionNames).containsExactly("Pages.AbilityQuery");
        });
    }

    @Test
    void createOrUpdateRoleRejectsMissingOriginalRequiredFields() throws Exception {
        JsonNode missingRole = postJson("/api/services/app/Role/CreateOrUpdateRole", Map.of(
                "grantedPermissionNames", List.of()
        ));

        assertValidationFailure(missingRole);

        JsonNode missingPermissions = postJson("/api/services/app/Role/CreateOrUpdateRole", Map.of(
                "role", Map.of(
                        "displayName", "Missing Permissions Role",
                        "isDefault", false
                )
        ));

        assertValidationFailure(missingPermissions);

        JsonNode missingDisplayName = postJson("/api/services/app/Role/CreateOrUpdateRole", Map.of(
                "role", Map.of("isDefault", false),
                "grantedPermissionNames", List.of()
        ));

        assertValidationFailure(missingDisplayName);
    }

    private PermissionItem testPermission(String name, String displayName) {
        PermissionItem item = new PermissionItem();
        item.name = name;
        item.displayName = displayName;
        item.description = null;
        item.parentName = "Pages.Test";
        item.level = 2;
        return item;
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

    private void assertValidationFailure(JsonNode response) {
        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText()).isEqualTo("Validation failed");
    }

    private String adminToken() {
        return authService.authenticate("admin", "123qwe").orElseThrow().token();
    }

    private int indexOfPermission(JsonNode permissions, String name) {
        for (int i = 0; i < permissions.size(); i++) {
            if (name.equals(permissions.get(i).path("name").asText())) {
                return i;
            }
        }
        throw new AssertionError("Permission not found: " + name);
    }

    private JsonNode permissionNamed(JsonNode permissions, String name) {
        return permissions.get(indexOfPermission(permissions, name));
    }
}
