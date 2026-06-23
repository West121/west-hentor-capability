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
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/permission-authorization-parity-store.json")
@AutoConfigureMockMvc
class PermissionAuthorizationParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/permission-authorization-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset permission authorization parity test store", ex);
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
    void getAllPermissionsDoesNotRequireRoleManagementPermissionLikeOriginalAppService() throws Exception {
        String token = userManagerWithoutRoleManagementToken();

        JsonNode response = objectMapper.readTree(mockMvc.perform(get("/api/services/app/Permission/GetAllPermissions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));

        assertThat(response.path("success").asBoolean()).isTrue();
        JsonNode permissions = response.path("result").path("items");
        assertThat(permissions).isNotEmpty();
        assertThat(permissionNamed(permissions, "Pages.Administration.Users.ChangePermissions").path("level").asInt()).isEqualTo(3);
    }

    @Test
    void protectedRoutesRejectLocalMockAccessTokenLikeOriginalJwtMiddleware() throws Exception {
        JsonNode response = objectMapper.readTree(mockMvc.perform(get("/api/services/app/Permission/GetAllPermissions")
                        .header("Authorization", "Bearer mock-access-token"))
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isTrue();
    }

    private String userManagerWithoutRoleManagementToken() {
        RoleItem role = new RoleItem();
        role.name = "UserPermissionEditor";
        role.displayName = "User permission editor";
        store.saveRole(role, List.of("Pages.Administration.Users.ChangePermissions"));

        UserItem user = new UserItem();
        user.name = "Permission";
        user.surname = "Editor";
        user.userName = "permission-editor";
        user.emailAddress = "permission-editor@example.local";
        user.phoneNumber = "13700000000";
        user.isActive = true;
        user.isEmailConfirmed = true;
        user.isLockoutEnabled = true;
        user.creationTime = LocalDateTime.now();
        store.saveUser(user, List.of(role.name), List.of(), List.of());

        return authService.authenticate(user.userName, "123qwe").orElseThrow().token();
    }

    private JsonNode permissionNamed(JsonNode permissions, String name) {
        for (JsonNode permission : permissions) {
            if (name.equals(permission.path("name").asText())) {
                return permission;
            }
        }
        throw new AssertionError("Permission not found: " + name);
    }
}
