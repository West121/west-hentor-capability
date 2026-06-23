package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.model.SystemSettingsItem;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/settings-ui-method-route-parity-store.json")
@AutoConfigureMockMvc
class SettingsUiMethodRouteParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/settings-ui-method-route-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset settings/UI method route parity test store", ex);
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
    void hostAndTenantSettingUpdatesAcceptOriginalPutContracts() throws Exception {
        SystemSettingsItem.HostSettings host = SystemSettingsItem.defaultHostSettings();
        host.email.defaultFromAddress = "host-put@example.local";
        host.billing.legalName = "Host Put Legal";

        JsonNode hostResponse = abp(putJson("/api/services/app/HostSettings/UpdateAllSettings", host));
        assertThat(hostResponse.path("result").isNull()).isTrue();
        assertThat(store.hostSettings().email.defaultFromAddress).isEqualTo("host-put@example.local");

        SystemSettingsItem.AbilitySettings ability = new SystemSettingsItem.AbilitySettings();
        ability.description = "Ability PUT parity " + System.nanoTime();

        JsonNode abilityResponse = abp(putJson("/api/services/app/HostSettings/UpdateAbilitySettings", ability));
        assertThat(abilityResponse.path("result").isNull()).isTrue();
        assertThat(store.abilitySettings().description).isEqualTo(ability.description);

        SystemSettingsItem.TenantSettings tenant = SystemSettingsItem.defaultTenantSettings();
        tenant.email.defaultFromDisplayName = "Tenant Put";
        tenant.billing.legalName = "Tenant Put Legal";

        JsonNode tenantResponse = abp(putJson("/api/services/app/TenantSettings/UpdateAllSettings", tenant));
        assertThat(tenantResponse.path("result").isNull()).isTrue();
        assertThat(store.tenantSettings().email.defaultFromDisplayName).isEqualTo("Tenant Put");
    }

    @Test
    void uiCustomizationUpdatesAcceptOriginalPutAndThemePostQueryContracts() throws Exception {
        JsonNode change = abp(post("/api/services/app/UiCustomizationSettings/ChangeThemeWithDefaultValues")
                .param("themeName", "theme2"));
        assertThat(change.path("result").isNull()).isTrue();
        assertThat(themeNamed("theme2").path("isActive").asBoolean()).isTrue();

        Map<String, Object> current = themeInput("theme2", "boxed", "top", "dark", true);
        JsonNode currentResponse = abp(putJson("/api/services/app/UiCustomizationSettings/UpdateUiManagementSettings", current));
        assertThat(currentResponse.path("result").isNull()).isTrue();
        assertThat(themeNamed("theme2").path("layout").path("layoutType").asText()).isEqualTo("boxed");

        Map<String, Object> defaults = themeInput("theme3", "fluid", "left", "light", true);
        JsonNode defaultResponse = abp(putJson("/api/services/app/UiCustomizationSettings/UpdateDefaultUiManagementSettings", defaults));
        assertThat(defaultResponse.path("result").isNull()).isTrue();
        assertThat(themeNamed("theme3").path("footer").path("fixedFooter").asBoolean()).isTrue();
    }

    private MockHttpServletRequestBuilder putJson(String url, Object payload) throws Exception {
        return put(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(payload));
    }

    private Map<String, Object> themeInput(String theme, String layoutType, String menuPosition,
                                           String asideSkin, boolean fixedFooter) {
        return Map.of(
                "theme", theme,
                "layout", Map.of("layoutType", layoutType),
                "header", Map.of(
                        "desktopFixedHeader", true,
                        "mobileFixedHeader", true,
                        "headerSkin", "light",
                        "minimizeDesktopHeaderType", "none",
                        "headerMenuArrows", true
                ),
                "subHeader", Map.of(
                        "fixedSubHeader", false,
                        "subheaderStyle", "solid"
                ),
                "menu", Map.of(
                        "position", menuPosition,
                        "asideSkin", asideSkin,
                        "fixedAside", true,
                        "allowAsideMinimizing", true,
                        "defaultMinimizedAside", false,
                        "submenuToggle", "accordion",
                        "searchActive", true
                ),
                "footer", Map.of("fixedFooter", fixedFooter)
        );
    }

    private JsonNode themeNamed(String themeName) throws Exception {
        JsonNode themes = abp(get("/api/services/app/UiCustomizationSettings/GetUiManagementSettings")).path("result");
        for (JsonNode theme : themes) {
            if (themeName.equals(theme.path("theme").asText())) {
                return theme;
            }
        }
        throw new AssertionError("Theme not found: " + themeName);
    }

    private JsonNode abp(MockHttpServletRequestBuilder request) throws Exception {
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
