package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.model.TenantItem;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/account-tenant-availability-parity-store.json")
@AutoConfigureMockMvc
class AccountTenantAvailabilityParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/account-tenant-availability-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset account tenant-availability parity test store", ex);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    CapabilityStore store;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void isTenantAvailableReturnsInactiveStateWithoutTenantIdLikeOriginal() throws Exception {
        TenantItem tenant = new TenantItem();
        tenant.tenancyName = "inactivecheck" + System.nanoTime();
        tenant.name = tenant.tenancyName;
        tenant.adminEmailAddress = tenant.tenancyName + "@example.local";
        tenant.isActive = false;
        TenantItem created = store.createTenant(tenant);

        JsonNode result = isTenantAvailable(created.tenancyName);

        assertThat(result.path("state").asInt()).isEqualTo(2);
        assertThat(result.path("tenantId").isNull()).isTrue();
        assertThat(result.path("serverRootAddress").isNull()).isTrue();
    }

    @Test
    void isTenantAvailableReturnsConfiguredServerRootWithoutInventingTenantSubdomain() throws Exception {
        TenantItem tenant = new TenantItem();
        tenant.tenancyName = "activecheck" + System.nanoTime();
        tenant.name = tenant.tenancyName;
        tenant.adminEmailAddress = tenant.tenancyName + "@example.local";
        tenant.isActive = true;
        TenantItem created = store.createTenant(tenant);

        JsonNode result = isTenantAvailable(created.tenancyName);

        assertThat(result.path("state").asInt()).isEqualTo(1);
        assertThat(result.path("tenantId").asInt()).isEqualTo(created.id);
        assertThat(result.path("serverRootAddress").asText()).isEqualTo("http://localhost:9901/");
    }

    @Test
    void isTenantAvailableRejectsTenancyNameLongerThanOriginalInputLimit() throws Exception {
        String body = mockMvc.perform(post("/api/services/app/Account/IsTenantAvailable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("tenancyName", "t".repeat(65)))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText()).isEqualTo("Validation failed");
    }

    @Test
    void isTenantAvailableRejectsMissingRequiredTenancyName() throws Exception {
        String body = mockMvc.perform(post("/api/services/app/Account/IsTenantAvailable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText()).isEqualTo("Validation failed");
    }

    private JsonNode isTenantAvailable(String tenancyName) throws Exception {
        String body = mockMvc.perform(post("/api/services/app/Account/IsTenantAvailable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("tenancyName", tenancyName))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isTrue();
        return response.path("result");
    }
}
