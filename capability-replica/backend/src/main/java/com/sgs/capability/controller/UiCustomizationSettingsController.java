package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.model.ThemeSettingsItem;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.CapabilityStore;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Mirrors UiCustomizationSettingsAppService theme settings routes. */
@RestController
@RequestMapping("/api/services/app/UiCustomizationSettings")
@RequirePermission("Pages.Administration.UiCustomization")
public class UiCustomizationSettingsController {
    private final CapabilityStore store;

    public UiCustomizationSettingsController(CapabilityStore store) {
        this.store = store;
    }

    @GetMapping("/GetUiManagementSettings")
    public AbpResponse<List<ThemeSettingsItem>> getUiManagementSettings() {
        return AbpResponse.ok(store.uiManagementSettings());
    }

    @PostMapping("/GetUiManagementSettings")
    public AbpResponse<List<ThemeSettingsItem>> postGetUiManagementSettings() {
        return getUiManagementSettings();
    }

    @PostMapping("/ChangeThemeWithDefaultValues")
    public AbpResponse<Void> changeThemeWithDefaultValues(@RequestParam(required = false) String themeName,
                                                          @RequestBody(required = false) ThemeNameInput input) {
        // Original proxy posts the theme name in the query string.
        String safeThemeName = themeName == null || themeName.isBlank() ? input == null ? null : input.themeName() : themeName;
        store.changeThemeWithDefaultValues(safeThemeName);
        return AbpResponse.ok(null);
    }

    @PostMapping("/UpdateUiManagementSettings")
    public AbpResponse<Void> updateUiManagementSettings(@RequestBody ThemeSettingsItem input) {
        return saveUiManagementSettings(input);
    }

    @PutMapping("/UpdateUiManagementSettings")
    public AbpResponse<Void> putUpdateUiManagementSettings(@RequestBody ThemeSettingsItem input) {
        return saveUiManagementSettings(input);
    }

    private AbpResponse<Void> saveUiManagementSettings(ThemeSettingsItem input) {
        store.updateUiManagementSettings(input);
        return AbpResponse.ok(null);
    }

    @PostMapping("/UpdateDefaultUiManagementSettings")
    public AbpResponse<Void> updateDefaultUiManagementSettings(@RequestBody ThemeSettingsItem input) {
        return saveDefaultUiManagementSettings(input);
    }

    @PutMapping("/UpdateDefaultUiManagementSettings")
    public AbpResponse<Void> putUpdateDefaultUiManagementSettings(@RequestBody ThemeSettingsItem input) {
        return saveDefaultUiManagementSettings(input);
    }

    private AbpResponse<Void> saveDefaultUiManagementSettings(ThemeSettingsItem input) {
        store.updateDefaultUiManagementSettings(input);
        return AbpResponse.ok(null);
    }

    @PostMapping("/UseSystemDefaultSettings")
    public AbpResponse<Void> useSystemDefaultSettings() {
        store.useSystemDefaultSettings();
        return AbpResponse.ok(null);
    }

    public static class ThemeNameInput {
        public String themeName;
        public String name;

        public String themeName() {
            return themeName == null || themeName.isBlank() ? name : themeName;
        }
    }
}
