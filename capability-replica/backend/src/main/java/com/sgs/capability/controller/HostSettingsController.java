package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.model.SystemSettingsItem;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.CapabilityStore;
import org.springframework.web.bind.annotation.*;

/** Mirrors HostSettingsAppService settings routes. */
@RestController
@RequestMapping("/api/services/app/HostSettings")
@RequirePermission("Pages.Administration.Host.Settings")
public class HostSettingsController {
    private final CapabilityStore store;

    public HostSettingsController(CapabilityStore store) {
        this.store = store;
    }

    @GetMapping("/GetAllSettings")
    public AbpResponse<SystemSettingsItem.HostSettings> getAllSettings() {
        return AbpResponse.ok(store.hostSettings());
    }

    @PostMapping("/GetAllSettings")
    public AbpResponse<SystemSettingsItem.HostSettings> postGetAllSettings() {
        return getAllSettings();
    }

    @PostMapping("/UpdateAllSettings")
    public AbpResponse<Void> updateAllSettings(
            @RequestBody(required = false) SystemSettingsItem.HostSettings input) {
        return saveAllSettings(input);
    }

    @PutMapping("/UpdateAllSettings")
    public AbpResponse<Void> putUpdateAllSettings(
            @RequestBody(required = false) SystemSettingsItem.HostSettings input) {
        return saveAllSettings(input);
    }

    private AbpResponse<Void> saveAllSettings(SystemSettingsItem.HostSettings input) {
        String requiredSectionError = validateHostSettingsInput(input);
        if (requiredSectionError != null) {
            return AbpResponse.failed(requiredSectionError);
        }
        String validationError = validateSessionTimeOutSettings(
                input.userManagement.sessionTimeOutSettings);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        store.updateHostSettings(input);
        return AbpResponse.ok(null);
    }

    @GetMapping("/GetAbilitySettings")
    @RequirePermission("Pages.AbilityManagement.EditDesc")
    public AbpResponse<SystemSettingsItem.AbilitySettings> getAbilitySettings() {
        return AbpResponse.ok(store.abilitySettings());
    }

    @PostMapping("/GetAbilitySettings")
    @RequirePermission("Pages.AbilityManagement.EditDesc")
    public AbpResponse<SystemSettingsItem.AbilitySettings> postGetAbilitySettings() {
        return getAbilitySettings();
    }

    @PostMapping("/UpdateAbilitySettings")
    @RequirePermission("Pages.AbilityManagement.EditDesc")
    public AbpResponse<Void> updateAbilitySettings(
            @RequestBody(required = false) SystemSettingsItem.AbilitySettings input) {
        return saveAbilitySettings(input);
    }

    @PutMapping("/UpdateAbilitySettings")
    @RequirePermission("Pages.AbilityManagement.EditDesc")
    public AbpResponse<Void> putUpdateAbilitySettings(
            @RequestBody(required = false) SystemSettingsItem.AbilitySettings input) {
        return saveAbilitySettings(input);
    }

    private AbpResponse<Void> saveAbilitySettings(SystemSettingsItem.AbilitySettings input) {
        store.updateAbilitySettings(input);
        return AbpResponse.ok(null);
    }

    @PostMapping("/SendTestEmail")
    public AbpResponse<Void> sendTestEmail(@RequestBody(required = false) SendTestEmailInput input) {
        String validationError = validateSendTestEmailInput(input);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        store.sendTestEmail("HostSettingsAppService", input.emailAddress());
        return AbpResponse.ok(null);
    }

    private String validateSendTestEmailInput(SendTestEmailInput input) {
        if (input == null || input.emailAddress() == null || input.emailAddress().isBlank()
                || input.emailAddress().length() > 256) {
            // 原 SendTestEmailInput 要求 EmailAddress 必填且最多 256。
            return "Validation failed";
        }
        return null;
    }

    private String validateHostSettingsInput(SystemSettingsItem.HostSettings input) {
        if (input == null || input.general == null || input.userManagement == null || input.email == null
                || input.tenantManagement == null || input.security == null) {
            // 原 HostSettingsEditDto 要求 General/UserManagement/Email/TenantManagement/Security 必填。
            return "Validation failed";
        }
        return null;
    }

    private String validateSessionTimeOutSettings(SystemSettingsItem.SessionTimeOutSettings settings) {
        if (settings == null) {
            return null;
        }
        if (settings.timeOutSecond < 10 || settings.showTimeOutNotificationSecond < 10) {
            // 原 SessionTimeOutSettingsEditDto 要求两个秒数字段都至少为 10。
            return "Validation failed";
        }
        return null;
    }

    public record SendTestEmailInput(String emailAddress) {
    }
}
