package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.security.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/dashboard-customization-get-route-parity-store.json")
@AutoConfigureMockMvc
class DashboardCustomizationGetRouteParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/dashboard-customization-get-route-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset dashboard customization GET route parity test store", ex);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthService authService;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void dashboardCustomizationReadRoutesAcceptOriginalGetQueryParameters() throws Exception {
        JsonNode dashboard = getAbp(get("/api/services/app/DashboardCustomization/GetUserDashboard")
                .param("DashboardName", "HostDashboard")
                .param("Application", "Angular")).path("result");
        assertThat(dashboard.path("dashboardName").asText()).isEqualTo("HostDashboard");
        assertThat(dashboard.path("application").asText()).isEqualTo("Angular");
        assertThat(dashboard.path("pages").get(0).path("widgets")).anySatisfy(widget ->
                assertThat(widget.path("widgetId").asText()).startsWith("Widgets_Host_"));

        JsonNode definition = getAbp(get("/api/services/app/DashboardCustomization/GetDashboardDefinition")
                .param("DashboardName", "HostDashboard")
                .param("Application", "Angular")).path("result");
        assertThat(definition.path("name").asText()).isEqualTo("HostDashboard");
        assertThat(definition.path("widgets")).anySatisfy(widget ->
                assertThat(widget.path("id").asText()).startsWith("Widgets_Host_"));

        JsonNode widgets = getAbp(get("/api/services/app/DashboardCustomization/GetAllWidgetDefinitions")
                .param("DashboardName", "HostDashboard")
                .param("Application", "Angular")).path("result");
        assertThat(widgets).anySatisfy(widget -> assertThat(widget.path("id").asText()).isEqualTo("Widgets_Host_TopStats"));
        assertThat(widgets).allSatisfy(widget ->
                assertThat(widget.path("id").asText()).startsWith("Widgets_Host_"));
    }

    @Test
    void dashboardCustomizationSettingNameRouteAcceptsOriginalGetQueryParameter() throws Exception {
        JsonNode settingName = getAbp(get("/api/services/app/DashboardCustomization/GetSettingName")
                .param("application", "Angular")).path("result");

        assertThat(settingName.asText()).contains("DashboardCustomization");
        assertThat(settingName.asText()).contains("Angular");
    }

    @Test
    void dashboardDefinitionRejectsUnknownDashboardLikeOriginalService() throws Exception {
        String body = mockMvc.perform(get("/api/services/app/DashboardCustomization/GetDashboardDefinition")
                        .header("Authorization", "Bearer " + adminToken())
                        .param("DashboardName", "MissingDashboard")
                        .param("Application", "Angular"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText())
                .isEqualTo("Unknown Dashboard: MissingDashboard.");
    }

    @Test
    void userDashboardRejectsUnknownDashboardLikeOriginalService() throws Exception {
        String body = mockMvc.perform(get("/api/services/app/DashboardCustomization/GetUserDashboard")
                        .header("Authorization", "Bearer " + adminToken())
                        .param("DashboardName", "MissingDashboard")
                        .param("Application", "Angular"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText())
                .isEqualTo("Unknown Dashboard: MissingDashboard.");
    }

    @Test
    void widgetDefinitionsRejectUnknownDashboardLikeOriginalService() throws Exception {
        String body = mockMvc.perform(get("/api/services/app/DashboardCustomization/GetAllWidgetDefinitions")
                        .header("Authorization", "Bearer " + adminToken())
                        .param("DashboardName", "MissingDashboard")
                        .param("Application", "Angular"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText())
                .isEqualTo("Unknown Dashboard: MissingDashboard.");
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

    private String adminToken() {
        return authService.authenticate("admin", "123qwe").orElseThrow().token();
    }
}
