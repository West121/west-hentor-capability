package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.model.OrganizationUnit;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/organization-unit-edit-parity-store.json")
@AutoConfigureMockMvc
class OrganizationUnitEditParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/organization-unit-edit-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset organization unit edit parity test store", ex);
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
    void organizationUnitWriteRoutesRejectDisplayNameLongerThanOriginalInputLimit() throws Exception {
        String longDisplayName = "o".repeat(129);

        JsonNode createResponse = postAbp("/api/services/app/OrganizationUnit/CreateOrganizationUnit", Map.of(
                "parentId", 1,
                "displayName", longDisplayName
        ));

        assertThat(createResponse.path("success").asBoolean()).isFalse();
        assertThat(createResponse.path("error").path("message").asText()).isEqualTo("Validation failed");

        OrganizationUnit existing = new OrganizationUnit();
        existing.displayName = "Org Unit Before Length Check";
        OrganizationUnit saved = store.saveOrganizationUnit(existing);

        JsonNode updateResponse = postAbp("/api/services/app/OrganizationUnit/UpdateOrganizationUnit", Map.of(
                "id", saved.id,
                "displayName", longDisplayName
        ));

        assertThat(updateResponse.path("success").asBoolean()).isFalse();
        assertThat(updateResponse.path("error").path("message").asText()).isEqualTo("Validation failed");
    }

    @Test
    void organizationUnitWriteRoutesRejectMissingRequiredDisplayName() throws Exception {
        JsonNode createResponse = postAbp("/api/services/app/OrganizationUnit/CreateOrganizationUnit", Map.of(
                "parentId", 1
        ));

        assertThat(createResponse.path("success").asBoolean()).isFalse();
        assertThat(createResponse.path("error").path("message").asText()).isEqualTo("Validation failed");

        OrganizationUnit existing = new OrganizationUnit();
        existing.displayName = "Org Unit Before Required Check";
        OrganizationUnit saved = store.saveOrganizationUnit(existing);

        JsonNode updateResponse = postAbp("/api/services/app/OrganizationUnit/UpdateOrganizationUnit", Map.of(
                "id", saved.id
        ));

        assertThat(updateResponse.path("success").asBoolean()).isFalse();
        assertThat(updateResponse.path("error").path("message").asText()).isEqualTo("Validation failed");
    }

    @Test
    void updateOrganizationUnitRejectsIdOutsideOriginalRange() throws Exception {
        JsonNode response = postAbp("/api/services/app/OrganizationUnit/UpdateOrganizationUnit", Map.of(
                "id", 0,
                "displayName", "Invalid Id Org Unit"
        ));

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText()).isEqualTo("Validation failed");
    }

    @Test
    void moveOrganizationUnitRejectsIdOutsideOriginalRange() throws Exception {
        JsonNode response = postAbp("/api/services/app/OrganizationUnit/MoveOrganizationUnit", Map.of(
                "id", 0,
                "newParentId", 1
        ));

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText()).isEqualTo("Validation failed");
    }

    @Test
    void organizationUnitMembershipWritesRejectOriginalRangeViolations() throws Exception {
        assertValidationFailure(postAbp("/api/services/app/OrganizationUnit/AddUsersToOrganizationUnit", Map.of(
                "organizationUnitId", 0,
                "userIds", java.util.List.of(1)
        )));

        assertValidationFailure(postAbp("/api/services/app/OrganizationUnit/AddRolesToOrganizationUnit", Map.of(
                "organizationUnitId", 0,
                "roleIds", java.util.List.of(1)
        )));

        assertValidationFailure(postAbp("/api/services/app/OrganizationUnit/RemoveUserFromOrganizationUnit", Map.of(
                "organizationUnitId", 1,
                "userId", 0
        )));

        assertValidationFailure(deleteAbp("/api/services/app/OrganizationUnit/RemoveUserFromOrganizationUnit",
                "UserId", "1", "OrganizationUnitId", "0"));

        assertValidationFailure(postAbp("/api/services/app/OrganizationUnit/RemoveRoleFromOrganizationUnit", Map.of(
                "organizationUnitId", 1,
                "roleId", 0
        )));

        assertValidationFailure(deleteAbp("/api/services/app/OrganizationUnit/RemoveRoleFromOrganizationUnit",
                "RoleId", "0", "OrganizationUnitId", "1"));
    }

    private JsonNode postAbp(String path, Object payload) throws Exception {
        String body = mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(payload)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    private JsonNode deleteAbp(String path, String... params) throws Exception {
        var request = delete(path).header("Authorization", "Bearer " + adminToken());
        for (int index = 0; index < params.length; index += 2) {
            request.param(params[index], params[index + 1]);
        }
        String body = mockMvc.perform(request)
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
