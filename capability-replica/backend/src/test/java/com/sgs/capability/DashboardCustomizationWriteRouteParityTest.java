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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/dashboard-customization-write-route-parity-store.json")
@AutoConfigureMockMvc
class DashboardCustomizationWriteRouteParityTest {
    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/dashboard-customization-write-route-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset dashboard customization write route parity test store", ex);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthService authService;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void dashboardCustomizationWriteRoutesRejectUnknownDashboardLikeOriginalService() throws Exception {
        assertUnknownDashboard(postJson("/api/services/app/DashboardCustomization/SavePage",
                Map.of("dashboardName", "MissingDashboard", "application", "Angular", "pages", List.of())));
        assertUnknownDashboard(postJson("/api/services/app/DashboardCustomization/RenamePage",
                Map.of("dashboardName", "MissingDashboard", "application", "Angular", "id", "missing-page", "name", "Renamed")));
        assertUnknownDashboard(postJson("/api/services/app/DashboardCustomization/AddNewPage",
                Map.of("dashboardName", "MissingDashboard", "application", "Angular", "name", "New Page")));
        assertUnknownDashboard(postJson("/api/services/app/DashboardCustomization/AddWidget",
                Map.of("dashboardName", "MissingDashboard", "application", "Angular",
                        "pageId", "missing-page", "widgetId", "Widgets_Tenant_TopStats", "width", 6, "height", 4)));
        assertUnknownDashboard(postJson("/api/services/app/DashboardCustomization/DeletePage",
                Map.of("dashboardName", "MissingDashboard", "application", "Angular", "id", "missing-page")));
        assertUnknownDashboard(delete("/api/services/app/DashboardCustomization/DeletePage")
                .param("DashboardName", "MissingDashboard")
                .param("Application", "Angular")
                .param("Id", "missing-page"));
    }

    @Test
    void addWidgetRejectsMissingPageLikeOriginalSingleLookup() throws Exception {
        JsonNode beforePages = tenantDashboard().path("pages");
        String missingPageId = "missing-page";

        assertFailed(postJson("/api/services/app/DashboardCustomization/AddWidget",
                Map.of("dashboardName", "TenantDashboard", "application", "Angular", "pageId", missingPageId,
                        "widgetId", "Widgets_Tenant_TopStats", "width", 6, "height", 4)),
                "Sequence contains no matching element");

        JsonNode afterPages = tenantDashboard().path("pages");
        assertThat(afterPages).hasSize(beforePages.size());
        assertNoPageId(afterPages, missingPageId);
    }

    @Test
    void savePageRejectsMissingPageLikeOriginalRemoveAt() throws Exception {
        JsonNode beforePages = tenantDashboard().path("pages");
        String missingPageId = "missing-page";

        assertFailed(postJson("/api/services/app/DashboardCustomization/SavePage",
                Map.of("dashboardName", "TenantDashboard", "application", "Angular",
                        "pages", List.of(Map.of("id", missingPageId, "name", "Ignored", "widgets", List.of())))),
                "Index was out of range. Must be non-negative and less than the size of the collection. (Parameter 'index')");

        JsonNode afterPages = tenantDashboard().path("pages");
        assertThat(afterPages).hasSize(beforePages.size());
        assertNoPageId(afterPages, missingPageId);
    }

    @Test
    void deletePageRestoresDefaultDashboardWhenLastPageIsRemovedLikeOriginalService() throws Exception {
        JsonNode initialDashboard = tenantDashboard();
        String defaultPageId = initialDashboard.path("pages").get(0).path("id").asText();

        JsonNode addPageResult = assertOk(postJson("/api/services/app/DashboardCustomization/AddNewPage",
                Map.of("dashboardName", "TenantDashboard", "application", "Angular", "name", "Scratch Page")));
        String scratchPageId = addPageResult.path("pageId").asText();

        assertOk(postJson("/api/services/app/DashboardCustomization/DeletePage",
                Map.of("dashboardName", "TenantDashboard", "application", "Angular", "id", defaultPageId)));
        JsonNode onlyScratchPage = tenantDashboard().path("pages");
        assertThat(onlyScratchPage).hasSize(1);
        assertThat(onlyScratchPage.get(0).path("id").asText()).isEqualTo(scratchPageId);
        assertThat(onlyScratchPage.get(0).path("name").asText()).isEqualTo("Scratch Page");

        assertOk(postJson("/api/services/app/DashboardCustomization/DeletePage",
                Map.of("dashboardName", "TenantDashboard", "application", "Angular", "id", scratchPageId)));
        JsonNode restoredPages = tenantDashboard().path("pages");
        assertThat(restoredPages).hasSize(1);
        assertThat(restoredPages.get(0).path("name").asText()).isEqualTo("Default");
        assertThat(restoredPages.get(0).path("widgets")).anySatisfy(widget ->
                assertThat(widget.path("widgetId").asText()).isEqualTo("Widgets_Tenant_TopStats"));
    }

    @Test
    void addNewPagePreservesBlankNameLikeOriginalService() throws Exception {
        JsonNode addPageResult = assertOk(postJson("/api/services/app/DashboardCustomization/AddNewPage",
                Map.of("dashboardName", "TenantDashboard", "application", "Angular", "name", "")));
        String pageId = addPageResult.path("pageId").asText();

        JsonNode pages = tenantDashboard().path("pages");
        JsonNode addedPage = findPage(pages, pageId);
        String actualName = addedPage.path("name").asText();
        assertOk(postJson("/api/services/app/DashboardCustomization/DeletePage",
                Map.of("dashboardName", "TenantDashboard", "application", "Angular", "id", pageId)));
        assertThat(actualName).isEmpty();
    }

    @Test
    void renamePagePreservesBlankNameLikeOriginalService() throws Exception {
        JsonNode defaultPage = tenantDashboard().path("pages").get(0);
        String pageId = defaultPage.path("id").asText();
        String originalName = defaultPage.path("name").asText();

        assertOk(postJson("/api/services/app/DashboardCustomization/RenamePage",
                Map.of("dashboardName", "TenantDashboard", "application", "Angular", "id", pageId, "name", "")));
        String actualName = tenantDashboard().path("pages").get(0).path("name").asText();
        assertOk(postJson("/api/services/app/DashboardCustomization/RenamePage",
                Map.of("dashboardName", "TenantDashboard", "application", "Angular", "id", pageId, "name", originalName)));

        assertThat(actualName).isEmpty();
    }

    @Test
    void savePagePreservesExistingPageNameLikeOriginalService() throws Exception {
        JsonNode initialDashboard = tenantDashboard();
        String defaultPageId = initialDashboard.path("pages").get(0).path("id").asText();

        assertOk(postJson("/api/services/app/DashboardCustomization/SavePage",
                Map.of("dashboardName", "TenantDashboard", "application", "Angular",
                        "pages", List.of(Map.of("id", defaultPageId, "name", "Client Name", "widgets", List.of())))));

        JsonNode savedPage = tenantDashboard().path("pages").get(0);
        assertThat(savedPage.path("id").asText()).isEqualTo(defaultPageId);
        assertThat(savedPage.path("name").asText()).isEqualTo("Default");
        assertThat(savedPage.path("widgets")).isEmpty();
    }

    @Test
    void addWidgetUsesOriginalTopLeftPlacementLikeOriginalService() throws Exception {
        JsonNode initialDashboard = tenantDashboard();
        JsonNode defaultPage = initialDashboard.path("pages").get(0);
        String defaultPageId = defaultPage.path("id").asText();
        int expectedPositionY = maxWidgetBottom(defaultPage.path("widgets"));

        JsonNode addedWidget = assertOk(postJson("/api/services/app/DashboardCustomization/AddWidget",
                Map.of("dashboardName", "TenantDashboard", "application", "Angular", "pageId", defaultPageId,
                        "widgetId", "Widgets_Tenant_SalesSummary", "width", 8, "height", 5)));

        assertThat(addedWidget.path("positionX").asInt()).isZero();
        assertThat(addedWidget.path("positionY").asInt()).isEqualTo(expectedPositionY);

        JsonNode savedWidgets = tenantDashboard().path("pages").get(0).path("widgets");
        assertThat(savedWidgets.get(savedWidgets.size() - 1).path("positionX").asInt()).isZero();
        assertThat(savedWidgets.get(savedWidgets.size() - 1).path("positionY").asInt()).isEqualTo(expectedPositionY);
    }

    @Test
    void addWidgetStacksBelowLowestExistingWidgetLikeOriginalService() throws Exception {
        JsonNode initialDashboard = tenantDashboard();
        String defaultPageId = initialDashboard.path("pages").get(0).path("id").asText();
        List<Map<String, Object>> customWidgets = List.of(
                Map.of("widgetId", "Widgets_Tenant_TopStats", "width", 6, "height", 3, "positionX", 0, "positionY", 10),
                Map.of("widgetId", "Widgets_Tenant_SalesSummary", "width", 6, "height", 2, "positionX", 6, "positionY", 1)
        );

        assertOk(postJson("/api/services/app/DashboardCustomization/SavePage",
                Map.of("dashboardName", "TenantDashboard", "application", "Angular",
                        "pages", List.of(Map.of("id", defaultPageId, "name", "Ignored", "widgets", customWidgets)))));

        JsonNode addedWidget = assertOk(postJson("/api/services/app/DashboardCustomization/AddWidget",
                Map.of("dashboardName", "TenantDashboard", "application", "Angular", "pageId", defaultPageId,
                        "widgetId", "Widgets_Tenant_MemberActivity", "width", 4, "height", 4)));

        assertThat(addedWidget.path("positionY").asInt()).isEqualTo(13);

        JsonNode savedWidgets = tenantDashboard().path("pages").get(0).path("widgets");
        assertThat(savedWidgets.get(savedWidgets.size() - 1).path("positionY").asInt()).isEqualTo(13);
    }

    @Test
    void addWidgetKeepsInputSizeIncludingZeroLikeOriginalService() throws Exception {
        JsonNode initialDashboard = tenantDashboard();
        String defaultPageId = initialDashboard.path("pages").get(0).path("id").asText();

        JsonNode addedWidget = assertOk(postJson("/api/services/app/DashboardCustomization/AddWidget",
                Map.of("dashboardName", "TenantDashboard", "application", "Angular", "pageId", defaultPageId,
                        "widgetId", "Widgets_Tenant_GeneralStats", "width", 0, "height", 0)));

        assertThat(addedWidget.path("width").asInt()).isZero();
        assertThat(addedWidget.path("height").asInt()).isZero();

        JsonNode savedWidgets = tenantDashboard().path("pages").get(0).path("widgets");
        JsonNode savedWidget = savedWidgets.get(savedWidgets.size() - 1);
        assertThat(savedWidget.path("width").asInt()).isZero();
        assertThat(savedWidget.path("height").asInt()).isZero();
    }

    private MockHttpServletRequestBuilder postJson(String url, Map<String, Object> body) throws Exception {
        return post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
    }

    private JsonNode tenantDashboard() throws Exception {
        return assertOk(get("/api/services/app/DashboardCustomization/GetUserDashboard")
                .param("DashboardName", "TenantDashboard")
                .param("Application", "Angular"));
    }

    private JsonNode assertOk(MockHttpServletRequestBuilder request) throws Exception {
        String body = mockMvc.perform(request.header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isTrue();
        return response.path("result");
    }

    private void assertUnknownDashboard(MockHttpServletRequestBuilder request) throws Exception {
        assertFailed(request, "Unknown Dashboard: MissingDashboard.");
    }

    private JsonNode assertFailed(MockHttpServletRequestBuilder request, String message) throws Exception {
        String body = mockMvc.perform(request.header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("unAuthorizedRequest").asBoolean()).isFalse();
        assertThat(response.path("error").path("message").asText())
                .isEqualTo(message);
        return response;
    }

    private int maxWidgetBottom(JsonNode widgets) {
        int max = 0;
        for (JsonNode widget : widgets) {
            max = Math.max(max, widget.path("positionY").asInt() + widget.path("height").asInt());
        }
        return max;
    }

    private JsonNode findPage(JsonNode pages, String pageId) {
        for (JsonNode page : pages) {
            if (page.path("id").asText().equals(pageId)) {
                return page;
            }
        }
        throw new AssertionError("Page not found: " + pageId);
    }

    private void assertNoPageId(JsonNode pages, String pageId) {
        for (JsonNode page : pages) {
            assertThat(page.path("id").asText()).isNotEqualTo(pageId);
        }
    }

    private String adminToken() {
        return authService.authenticate("admin", "123qwe").orElseThrow().token();
    }
}
