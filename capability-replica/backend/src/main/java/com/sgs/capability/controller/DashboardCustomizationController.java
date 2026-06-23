package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.model.DashboardCustomizationItem;
import com.sgs.capability.model.DashboardPageItem;
import com.sgs.capability.model.DashboardWidgetItem;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.CapabilityStore;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Mirrors DashboardCustomizationAppService user layout and definition routes. */
@RestController
@RequestMapping("/api/services/app/DashboardCustomization")
@RequirePermission("Pages")
public class DashboardCustomizationController {
    private final CapabilityStore store;

    public DashboardCustomizationController(CapabilityStore store) {
        this.store = store;
    }

    @PostMapping("/GetUserDashboard")
    public AbpResponse<DashboardCustomizationItem> getUserDashboard(@RequestBody(required = false) GetDashboardInput input) {
        GetDashboardInput safeInput = input == null ? new GetDashboardInput() : input;
        if (!knownDashboardName(safeInput.dashboardName)) {
            return unknownDashboard(safeInput.dashboardName);
        }
        return AbpResponse.ok(store.dashboardCustomization(safeInput.application, safeInput.dashboardName));
    }

    @GetMapping("/GetUserDashboard")
    public AbpResponse<DashboardCustomizationItem> getUserDashboardGet(@RequestParam(name = "Application", required = false) String application,
                                                                       @RequestParam(name = "application", required = false) String lowerApplication,
                                                                       @RequestParam(name = "DashboardName", required = false) String dashboardName,
                                                                       @RequestParam(name = "dashboardName", required = false) String lowerDashboardName) {
        GetDashboardInput input = new GetDashboardInput();
        input.application = firstText(application, lowerApplication);
        input.dashboardName = firstText(dashboardName, lowerDashboardName);
        return getUserDashboard(input);
    }

    @PostMapping("/SavePage")
    public AbpResponse<Void> savePage(@RequestBody(required = false) SavePageInput input) {
        SavePageInput safeInput = input == null ? new SavePageInput() : input;
        if (!knownDashboardName(safeInput.dashboardName)) {
            return unknownDashboard(safeInput.dashboardName);
        }
        try {
            store.saveDashboardPages(safeInput.application, safeInput.dashboardName, safeInput.pages);
            return AbpResponse.ok(null);
        } catch (IllegalArgumentException ex) {
            return AbpResponse.failed(ex.getMessage());
        }
    }

    @PostMapping("/RenamePage")
    public AbpResponse<Void> renamePage(@RequestBody(required = false) RenamePageInput input) {
        if (input != null && !knownDashboardName(input.dashboardName)) {
            return unknownDashboard(input.dashboardName);
        }
        if (input != null) {
            store.renameDashboardPage(input.application, input.dashboardName, input.id, input.name);
        }
        return AbpResponse.ok(null);
    }

    @PostMapping("/AddNewPage")
    public AbpResponse<AddNewPageOutput> addNewPage(@RequestBody(required = false) AddNewPageInput input) {
        AddNewPageInput safeInput = input == null ? new AddNewPageInput() : input;
        if (!knownDashboardName(safeInput.dashboardName)) {
            return unknownDashboard(safeInput.dashboardName);
        }
        return AbpResponse.ok(new AddNewPageOutput(store.addDashboardPage(
                safeInput.application, safeInput.dashboardName, safeInput.name)));
    }

    @PostMapping("/AddWidget")
    public AbpResponse<DashboardWidgetItem> addWidget(@RequestBody(required = false) AddWidgetInput input) {
        AddWidgetInput safeInput = input == null ? new AddWidgetInput() : input;
        if (!knownDashboardName(safeInput.dashboardName)) {
            return unknownDashboard(safeInput.dashboardName);
        }
        try {
            return AbpResponse.ok(store.addDashboardWidget(safeInput.application, safeInput.dashboardName,
                    safeInput.pageId, safeInput.widgetId, safeInput.width, safeInput.height));
        } catch (IllegalArgumentException ex) {
            return AbpResponse.failed(ex.getMessage());
        }
    }

    @PostMapping("/DeletePage")
    public AbpResponse<Void> deletePage(@RequestBody(required = false) DeletePageInput input) {
        if (input != null && !knownDashboardName(input.dashboardName)) {
            return unknownDashboard(input.dashboardName);
        }
        if (input != null) {
            store.deleteDashboardPage(input.application, input.dashboardName, input.id);
        }
        return AbpResponse.ok(null);
    }

    @DeleteMapping("/DeletePage")
    public AbpResponse<Void> deletePageByQuery(@RequestParam(name = "Id", required = false) String id,
                                               @RequestParam(name = "DashboardName", required = false) String dashboardName,
                                               @RequestParam(name = "Application", required = false) String application) {
        if (!knownDashboardName(dashboardName)) {
            return unknownDashboard(dashboardName);
        }
        store.deleteDashboardPage(application, dashboardName, id);
        return AbpResponse.ok(null);
    }

    @PostMapping("/GetDashboardDefinition")
    public AbpResponse<DashboardOutput> getDashboardDefinition(@RequestBody(required = false) GetDashboardInput input) {
        String dashboardName = input == null ? null : input.dashboardName;
        if (!knownDashboardName(dashboardName)) {
            return unknownDashboard(dashboardName);
        }
        return AbpResponse.ok(new DashboardOutput(safeDashboardName(dashboardName), widgetsForDashboard(dashboardName)));
    }

    @GetMapping("/GetDashboardDefinition")
    public AbpResponse<DashboardOutput> getDashboardDefinitionGet(@RequestParam(name = "DashboardName", required = false) String dashboardName,
                                                                  @RequestParam(name = "dashboardName", required = false) String lowerDashboardName,
                                                                  @RequestParam(name = "Application", required = false) String application,
                                                                  @RequestParam(name = "application", required = false) String lowerApplication) {
        GetDashboardInput input = new GetDashboardInput();
        input.dashboardName = firstText(dashboardName, lowerDashboardName);
        input.application = firstText(application, lowerApplication);
        return getDashboardDefinition(input);
    }

    @PostMapping("/GetAllWidgetDefinitions")
    public AbpResponse<List<WidgetOutput>> getAllWidgetDefinitions(@RequestBody(required = false) GetDashboardInput input) {
        String dashboardName = input == null ? null : input.dashboardName;
        if (!knownDashboardName(dashboardName)) {
            return unknownDashboard(dashboardName);
        }
        return AbpResponse.ok(widgetsForDashboard(dashboardName));
    }

    @GetMapping("/GetAllWidgetDefinitions")
    public AbpResponse<List<WidgetOutput>> getAllWidgetDefinitionsGet(@RequestParam(name = "DashboardName", required = false) String dashboardName,
                                                                      @RequestParam(name = "dashboardName", required = false) String lowerDashboardName,
                                                                      @RequestParam(name = "Application", required = false) String application,
                                                                      @RequestParam(name = "application", required = false) String lowerApplication) {
        GetDashboardInput input = new GetDashboardInput();
        input.dashboardName = firstText(dashboardName, lowerDashboardName);
        input.application = firstText(application, lowerApplication);
        return getAllWidgetDefinitions(input);
    }

    @GetMapping("/GetSettingName")
    public AbpResponse<String> getSettingName(@RequestParam(name = "application", required = false) String application) {
        String safeApplication = application == null || application.isBlank() ? "Angular" : application;
        return AbpResponse.ok("App.DashboardCustomization.Configuration." + safeApplication);
    }

    private List<WidgetOutput> widgetsForDashboard(String dashboardName) {
        List<String> ids = safeDashboardName(dashboardName).equals("HostDashboard")
                ? List.of("Widgets_Host_IncomeStatistics", "Widgets_Host_TopStats", "Widgets_Host_EditionStatistics",
                "Widgets_Host_SubscriptionExpiringTenants", "Widgets_Host_RecentTenants")
                : List.of("Widgets_Tenant_GeneralStats", "Widgets_Tenant_DailySales", "Widgets_Tenant_ProfitShare",
                "Widgets_Tenant_MemberActivity", "Widgets_Tenant_RegionalStats", "Widgets_Tenant_TopStats",
                "Widgets_Tenant_SalesSummary");
        return widgetDefinitions().stream().filter(widget -> ids.contains(widget.id)).toList();
    }

    private List<WidgetOutput> widgetDefinitions() {
        WidgetFilterOutput dateRange = new WidgetFilterOutput("Filters_DateRangePicker", "FilterDateRangePicker");
        return List.of(
                new WidgetOutput("Widgets_Tenant_DailySales", "WidgetDailySales", "", List.of(dateRange)),
                new WidgetOutput("Widgets_Tenant_GeneralStats", "WidgetGeneralStats", "", List.of()),
                new WidgetOutput("Widgets_Tenant_ProfitShare", "WidgetProfitShare", "", List.of()),
                new WidgetOutput("Widgets_Tenant_MemberActivity", "WidgetMemberActivity", "", List.of()),
                new WidgetOutput("Widgets_Tenant_RegionalStats", "WidgetRegionalStats", "", List.of()),
                new WidgetOutput("Widgets_Tenant_SalesSummary", "WidgetSalesSummary", "", List.of(dateRange)),
                new WidgetOutput("Widgets_Tenant_TopStats", "WidgetTopStats", "", List.of()),
                new WidgetOutput("Widgets_Host_IncomeStatistics", "WidgetIncomeStatistics", "", List.of()),
                new WidgetOutput("Widgets_Host_TopStats", "WidgetTopStats", "", List.of()),
                new WidgetOutput("Widgets_Host_EditionStatistics", "WidgetEditionStatistics", "", List.of()),
                new WidgetOutput("Widgets_Host_SubscriptionExpiringTenants", "WidgetSubscriptionExpiringTenants", "", List.of()),
                new WidgetOutput("Widgets_Host_RecentTenants", "WidgetRecentTenants", "", List.of(dateRange))
        );
    }

    private String safeDashboardName(String dashboardName) {
        return dashboardName == null || dashboardName.isBlank() ? "TenantDashboard" : dashboardName;
    }

    private boolean knownDashboardName(String dashboardName) {
        String safeName = safeDashboardName(dashboardName);
        return safeName.equals("TenantDashboard") || safeName.equals("HostDashboard");
    }

    private <T> AbpResponse<T> unknownDashboard(String dashboardName) {
        return AbpResponse.failed("Unknown Dashboard: " + dashboardName + ".");
    }

    private String firstText(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }

    public static class GetDashboardInput {
        public String dashboardName;
        public String application;
    }

    public static class SavePageInput extends GetDashboardInput {
        public List<DashboardPageItem> pages = List.of();
    }

    public static class RenamePageInput extends GetDashboardInput {
        public String id;
        public String name;
    }

    public static class AddNewPageInput extends GetDashboardInput {
        public String name;
    }

    public record AddNewPageOutput(String pageId) {
    }

    public static class AddWidgetInput extends GetDashboardInput {
        public String widgetId;
        public String pageId;
        public int width;
        public int height;
    }

    public static class DeletePageInput extends GetDashboardInput {
        public String id;
    }

    public record DashboardOutput(String name, List<WidgetOutput> widgets) {
    }

    public record WidgetOutput(String id, String name, String description, List<WidgetFilterOutput> filters) {
    }

    public record WidgetFilterOutput(String id, String name) {
    }
}
