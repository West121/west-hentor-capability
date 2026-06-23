package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.security.AuthService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/ui-customization-parity-store.json")
@AutoConfigureMockMvc
class UiCustomizationParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/ui-customization-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset UI customization parity test store", ex);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthService authService;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void updateUiManagementSettingsKeepsOriginalVoidResponseWhileSavingTheme() throws Exception {
        Map<String, Object> input = themeInput("theme2", "boxed", "top", "dark", true);

        JsonNode response = postAbp("/api/services/app/UiCustomizationSettings/UpdateUiManagementSettings", input);

        assertThat(response.path("result").isNull()).isTrue();
        JsonNode theme = themeNamed("theme2");
        assertThat(theme.path("isActive").asBoolean()).isTrue();
        assertThat(theme.path("layout").path("layoutType").asText()).isEqualTo("boxed");
        assertThat(theme.path("menu").path("position").asText()).isEqualTo("top");
        assertThat(theme.path("menu").path("asideSkin").asText()).isEqualTo("dark");
        assertThat(theme.path("footer").path("fixedFooter").asBoolean()).isTrue();
    }

    @Test
    void updateDefaultUiManagementSettingsKeepsOriginalVoidResponseWhileSavingThemeDefaults() throws Exception {
        Map<String, Object> input = themeInput("theme3", "fluid", "left", "light", true);

        JsonNode response = postAbp("/api/services/app/UiCustomizationSettings/UpdateDefaultUiManagementSettings", input);

        assertThat(response.path("result").isNull()).isTrue();
        JsonNode theme = themeNamed("theme3");
        assertThat(theme.path("layout").path("layoutType").asText()).isEqualTo("fluid");
        assertThat(theme.path("menu").path("position").asText()).isEqualTo("left");
        assertThat(theme.path("menu").path("asideSkin").asText()).isEqualTo("light");
        assertThat(theme.path("footer").path("fixedFooter").asBoolean()).isTrue();
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
        JsonNode themes = getAbp("/api/services/app/UiCustomizationSettings/GetUiManagementSettings").path("result");
        for (JsonNode theme : themes) {
            if (themeName.equals(theme.path("theme").asText())) {
                return theme;
            }
        }
        throw new AssertionError("Theme not found: " + themeName);
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

    private JsonNode getAbp(String url) throws Exception {
        String body = mockMvc.perform(get(url)
                        .header("Authorization", "Bearer " + adminToken()))
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
