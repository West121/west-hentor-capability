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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/user-edit-parity-store.json")
@AutoConfigureMockMvc
class UserEditParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/user-edit-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset user edit parity test store", ex);
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
    void getUserForEditWithoutIdReturnsOriginalCreateDefaults() throws Exception {
        JsonNode result = postAbp("/api/services/app/User/GetUserForEdit", Map.of()).path("result");

        JsonNode user = result.path("user");
        assertThat(user.isObject()).isTrue();
        assertThat(user.path("isActive").asBoolean()).isTrue();
        assertThat(user.path("password").asText()).isEqualTo("qazwsxEDCRFV");
        assertThat(user.path("shouldChangePasswordOnNextLogin").asBoolean()).isFalse();
        assertThat(user.path("isTwoFactorEnabled").asBoolean()).isFalse();
        assertThat(user.path("isLockoutEnabled").asBoolean()).isFalse();
        assertThat(result.path("memberedOrganizationUnits")).isEmpty();
        assertThat(result.path("memberedLabs")).isEmpty();
    }

    @Test
    void getUserForEditReturnsOriginalUserRoleDtoShapeWithDefaultRoleAssigned() throws Exception {
        JsonNode result = postAbp("/api/services/app/User/GetUserForEdit", Map.of()).path("result");

        JsonNode admin = roleNamed(result.path("roles"), "Admin");
        assertThat(admin.path("roleId").asInt()).isEqualTo(1);
        assertThat(admin.path("roleName").asText()).isEqualTo("Admin");
        assertThat(admin.path("roleDisplayName").asText()).isEqualTo("管理员");
        assertThat(admin.path("isAssigned").asBoolean()).isTrue();
        assertThat(admin.path("inheritedFromOrganizationUnit").asBoolean()).isFalse();

        JsonNode query = roleNamed(result.path("roles"), "AbilityQuery");
        assertThat(query.path("isAssigned").asBoolean()).isFalse();
    }

    @Test
    void updateUserPermissionsRejectsOriginalDtoValidationViolations() throws Exception {
        JsonNode missingId = postAbpRaw("/api/services/app/User/UpdateUserPermissions", Map.of(
                "grantedPermissionNames", List.of()
        ));

        assertValidationFailure(missingId);

        JsonNode zeroId = postAbpRaw("/api/services/app/User/UpdateUserPermissions", Map.of(
                "id", 0,
                "grantedPermissionNames", List.of()
        ));

        assertValidationFailure(zeroId);

        JsonNode missingPermissions = postAbpRaw("/api/services/app/User/UpdateUserPermissions", Map.of(
                "id", 1
        ));

        assertValidationFailure(missingPermissions);
    }

    @Test
    void createOrUpdateUserKeepsOriginalVoidResponseWhileSavingUser() throws Exception {
        String userName = "void-user-" + System.nanoTime();
        JsonNode response = postAbp("/api/services/app/User/CreateOrUpdateUser", Map.of(
                "user", Map.of(
                        "name", "Void",
                        "surname", "Response",
                        "userName", userName,
                        "emailAddress", userName + "@example.local",
                        "phoneNumber", "13700000001",
                        "isActive", true,
                        "shouldChangePasswordOnNextLogin", false,
                        "isTwoFactorEnabled", false,
                        "isLockoutEnabled", true
                ),
                "assignedRoleNames", java.util.List.of("AbilityQuery"),
                "organizationUnits", java.util.List.of(),
                "labs", java.util.List.of(),
                "sendActivationEmail", false,
                "setRandomPassword", false
        ));

        assertThat(response.path("result").isNull()).isTrue();
        assertThat(store.filteredUsers(userName, null, java.util.List.of(), false)).anySatisfy(user -> {
            assertThat(user.userName).isEqualTo(userName);
            assertThat(user.assignedRoleNames).containsExactly("AbilityQuery");
        });
    }

    @Test
    void createOrUpdateUserUsesSubmittedPasswordForNewUsers() throws Exception {
        String userName = "password-user-" + System.nanoTime();
        postAbp("/api/services/app/User/CreateOrUpdateUser", Map.of(
                "user", Map.of(
                        "name", "Password",
                        "surname", "User",
                        "userName", userName,
                        "emailAddress", userName + "@example.local",
                        "phoneNumber", "13700000002",
                        "password", "qazwsxEDCRFV",
                        "isActive", true,
                        "shouldChangePasswordOnNextLogin", false,
                        "isTwoFactorEnabled", false,
                        "isLockoutEnabled", true
                ),
                "assignedRoleNames", java.util.List.of("AbilityQuery"),
                "organizationUnits", java.util.List.of(),
                "labs", java.util.List.of(),
                "sendActivationEmail", false,
                "setRandomPassword", false
        ));

        JsonNode login = postAbp("/api/TokenAuth/Authenticate", Map.of(
                "userNameOrEmailAddress", userName,
                "password", base64("qazwsxEDCRFV")
        )).path("result");

        assertThat(login.path("accessToken").asText()).contains(".");
        assertThat(login.path("shouldResetPassword").asBoolean()).isFalse();
    }

    @Test
    void createOrUpdateUserSetRandomPasswordIgnoresSubmittedPasswordForNewUsers() throws Exception {
        String userName = "random-password-user-" + System.nanoTime();
        String submittedPassword = "KnownPassword9!";
        postAbp("/api/services/app/User/CreateOrUpdateUser", Map.of(
                "user", Map.of(
                        "name", "Random",
                        "surname", "Password",
                        "userName", userName,
                        "emailAddress", userName + "@example.local",
                        "phoneNumber", "13700000003",
                        "password", submittedPassword,
                        "isActive", true,
                        "shouldChangePasswordOnNextLogin", false,
                        "isTwoFactorEnabled", false,
                        "isLockoutEnabled", true
                ),
                "assignedRoleNames", java.util.List.of("AbilityQuery"),
                "organizationUnits", java.util.List.of(),
                "labs", java.util.List.of(),
                "sendActivationEmail", false,
                "setRandomPassword", true
        ));

        com.sgs.capability.model.UserItem created = store.userByUserNameOrEmail(userName).orElseThrow();

        assertThat(store.passwordMatches(created.id, submittedPassword)).isFalse();
        assertThat(store.passwordMatches(created.id, "123qwe")).isFalse();
        assertThat(store.passwordMatches(created.id, "qazwsxEDCRFV")).isFalse();
        assertThat(created.password).isNull();
    }

    @Test
    void createOrUpdateUserNormalizesSurnameLikeOriginalUserEditDto() throws Exception {
        String userName = "surname-user-" + System.nanoTime();
        postAbp("/api/services/app/User/CreateOrUpdateUser", Map.of(
                "user", Map.of(
                        "name", "Surname",
                        "surname", "VisibleSurname",
                        "userName", userName,
                        "emailAddress", userName + "@example.local",
                        "phoneNumber", "13700000004",
                        "password", "qazwsxEDCRFV",
                        "isActive", true,
                        "shouldChangePasswordOnNextLogin", false,
                        "isTwoFactorEnabled", false,
                        "isLockoutEnabled", true
                ),
                "assignedRoleNames", java.util.List.of("AbilityQuery"),
                "organizationUnits", java.util.List.of(),
                "labs", java.util.List.of(),
                "sendActivationEmail", false,
                "setRandomPassword", false
        ));

        assertThat(store.userByUserNameOrEmail(userName)).hasValueSatisfying(user ->
                assertThat(user.surname).isEqualTo("-"));
    }

    @Test
    void createOrUpdateUserRejectsMissingEmailLikeOriginalUserEditDtoValidation() throws Exception {
        String userName = "missing-user-email-" + System.nanoTime();

        JsonNode response = postAbpRaw("/api/services/app/User/CreateOrUpdateUser", Map.of(
                "user", Map.of(
                        "name", "Missing",
                        "surname", "Email",
                        "userName", userName,
                        "phoneNumber", "13700000005",
                        "password", "qazwsxEDCRFV",
                        "isActive", true,
                        "shouldChangePasswordOnNextLogin", false,
                        "isTwoFactorEnabled", false,
                        "isLockoutEnabled", true
                ),
                "assignedRoleNames", java.util.List.of("AbilityQuery"),
                "organizationUnits", java.util.List.of(),
                "labs", java.util.List.of(),
                "sendActivationEmail", false,
                "setRandomPassword", false
        ));

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(store.userByUserNameOrEmail(userName)).isEmpty();
    }

    @Test
    void createOrUpdateUserRejectsMissingAssignedRoleNamesLikeOriginalInputValidation() throws Exception {
        String userName = "missing-user-roles-" + System.nanoTime();

        JsonNode response = postAbpRaw("/api/services/app/User/CreateOrUpdateUser", Map.of(
                "user", Map.of(
                        "name", "Missing",
                        "surname", "Roles",
                        "userName", userName,
                        "emailAddress", userName + "@example.local",
                        "phoneNumber", "13700000008",
                        "password", "qazwsxEDCRFV",
                        "isActive", true,
                        "shouldChangePasswordOnNextLogin", false,
                        "isTwoFactorEnabled", false,
                        "isLockoutEnabled", true
                ),
                "organizationUnits", java.util.List.of(),
                "labs", java.util.List.of(),
                "sendActivationEmail", false,
                "setRandomPassword", false
        ));

        assertValidationFailure(response);
        assertThat(store.userByUserNameOrEmail(userName)).isEmpty();
    }

    @Test
    void createOrUpdateUserRejectsPasswordLongerThanOriginalUserEditDtoLimit() throws Exception {
        String userName = "long-user-password-" + System.nanoTime();

        JsonNode response = postAbpRaw("/api/services/app/User/CreateOrUpdateUser", Map.of(
                "user", Map.of(
                        "name", "Long",
                        "surname", "Password",
                        "userName", userName,
                        "emailAddress", userName + "@example.local",
                        "phoneNumber", "13700000006",
                        "password", "a".repeat(33),
                        "isActive", true,
                        "shouldChangePasswordOnNextLogin", false,
                        "isTwoFactorEnabled", false,
                        "isLockoutEnabled", true
                ),
                "assignedRoleNames", java.util.List.of("AbilityQuery"),
                "organizationUnits", java.util.List.of(),
                "labs", java.util.List.of(),
                "sendActivationEmail", false,
                "setRandomPassword", false
        ));

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(store.userByUserNameOrEmail(userName)).isEmpty();
    }

    @Test
    void createOrUpdateUserRejectsFieldsLongerThanOriginalUserEditDtoLimits() throws Exception {
        java.util.Map<String, String> tooLongFields = new java.util.LinkedHashMap<>();
        tooLongFields.put("name", "n".repeat(65));
        tooLongFields.put("surname", "s".repeat(65));
        tooLongFields.put("userName", "u".repeat(257));
        tooLongFields.put("emailAddress", "e".repeat(245) + "@example.local");
        tooLongFields.put("phoneNumber", "1".repeat(25));

        for (java.util.Map.Entry<String, String> field : tooLongFields.entrySet()) {
            String emailAddress = "limited-user-" + field.getKey() + "-" + System.nanoTime() + "@example.local";
            java.util.Map<String, Object> user = new java.util.LinkedHashMap<>();
            user.put("name", "Limited");
            user.put("surname", "Field");
            user.put("userName", "limited-user-" + field.getKey() + "-" + System.nanoTime());
            user.put("emailAddress", emailAddress);
            user.put("phoneNumber", "13700000007");
            user.put("password", "qazwsxEDCRFV");
            user.put("isActive", true);
            user.put("shouldChangePasswordOnNextLogin", false);
            user.put("isTwoFactorEnabled", false);
            user.put("isLockoutEnabled", true);
            user.put(field.getKey(), field.getValue());

            JsonNode response = postAbpRaw("/api/services/app/User/CreateOrUpdateUser", Map.of(
                    "user", user,
                    "assignedRoleNames", java.util.List.of("AbilityQuery"),
                    "organizationUnits", java.util.List.of(),
                    "labs", java.util.List.of(),
                    "sendActivationEmail", false,
                    "setRandomPassword", false
            ));

            assertThat(response.path("success").asBoolean())
                    .as("field %s should fail validation", field.getKey())
                    .isFalse();
            assertThat(store.userByUserNameOrEmail(emailAddress)).isEmpty();
        }
    }

    @Test
    void resetUserPasswordUsesOriginalDefaultPasswordWithoutForcingPasswordChange() throws Exception {
        postAbp("/api/services/app/User/ResetUserPassword", Map.of("id", "2"));

        JsonNode login = postAbp("/api/TokenAuth/Authenticate", Map.of(
                "userNameOrEmailAddress", "query",
                "password", base64("qazwsxEDCRFV")
        )).path("result");

        assertThat(login.path("accessToken").asText()).contains(".");
        assertThat(login.path("shouldResetPassword").asBoolean()).isFalse();
    }

    private JsonNode postAbp(String url, Object payload) throws Exception {
        JsonNode response = postAbpRaw(url, payload);
        assertThat(response.path("success").asBoolean()).isTrue();
        return response;
    }

    private JsonNode postAbpRaw(String url, Object payload) throws Exception {
        String body = mockMvc.perform(post(url)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(payload)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode response = objectMapper.readTree(body);
        return response;
    }

    private void assertValidationFailure(JsonNode response) {
        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText()).isEqualTo("Validation failed");
    }

    private String adminToken() {
        return authService.authenticate("admin", "123qwe").orElseThrow().token();
    }

    private String base64(String raw) {
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private JsonNode roleNamed(JsonNode roles, String roleName) {
        for (JsonNode role : roles) {
            if (roleName.equals(role.path("roleName").asText())) {
                return role;
            }
        }
        throw new AssertionError("Role not found: " + roleName);
    }
}
