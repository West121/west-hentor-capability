package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.security.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/delete-route-method-parity-store.json")
@AutoConfigureMockMvc
class DeleteRouteMethodParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/delete-route-method-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset delete route method parity test store", ex);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthService authService;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void remainingOriginalDeleteRoutesAcceptTheirGeneratedQueryParameters() throws Exception {
        assertDeleteOk("/api/services/app/DashboardCustomization/DeletePage",
                "Id", "missing-page", "DashboardName", "TenantDashboard", "Application", "Frontend");
        assertDeleteOk("/api/services/app/Edition/DeleteEdition", "Id", "0");
        assertDeleteOk("/api/services/app/Language/DeleteLanguage", "Id", "0");
        assertDeleteOk("/api/services/app/MyFavorite/DeleteMyFavorite", "Id", "missing-favorite");
        assertDeleteOk("/api/services/app/MyFavorite/RemoveItem", "Id", "missing-ability");
        assertDeleteOk("/api/services/app/Notification/DeleteNotification", "Id", "missing-notification");
        assertDeleteOk("/api/services/app/Notification/DeleteAllUserNotifications", "State", "ALL");
        assertDeleteOk("/api/services/app/OrganizationUnit/DeleteOrganizationUnit", "Id", "0");
        assertDeleteOk("/api/services/app/OrganizationUnit/RemoveUserFromOrganizationUnit",
                "UserId", "1", "OrganizationUnitId", "1");
        assertDeleteOk("/api/services/app/OrganizationUnit/RemoveRoleFromOrganizationUnit",
                "RoleId", "1", "OrganizationUnitId", "1");
        assertDeleteOk("/api/services/app/Role/DeleteRole", "Id", "0");
        assertDeleteOk("/api/services/app/Tenant/DeleteTenant", "Id", "0");
        assertDeleteOk("/api/services/app/User/DeleteUser", "Id", "0");
    }

    @Test
    void userDelegationRemoveDelegationDeleteReturnsOriginalOwnershipErrorForMissingDelegation() throws Exception {
        String body = mockMvc.perform(delete("/api/services/app/UserDelegation/RemoveDelegation")
                        .header("Authorization", "Bearer " + adminToken())
                        .param("Id", "0"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText())
                .isEqualTo("Only source user can delete a user delegation !");
    }

    private void assertDeleteOk(String url, String... params) throws Exception {
        var request = delete(url).header("Authorization", "Bearer " + adminToken());
        for (int index = 0; index < params.length; index += 2) {
            request.param(params[index], params[index + 1]);
        }
        String body = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isTrue();
        assertThat(response.path("result").isNull()).isTrue();
    }

    private String adminToken() {
        return authService.authenticate("admin", "123qwe").orElseThrow().token();
    }
}
