package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.model.SystemSettingsItem;
import com.sgs.capability.security.AuthContext;
import com.sgs.capability.security.AuthService;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.CapabilityStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

/** Mirrors TenantSettingsAppService settings routes. */
@RestController
@RequestMapping("/api/services/app/TenantSettings")
@RequirePermission("Pages.Administration.Tenant.Settings")
public class TenantSettingsController {
    private final CapabilityStore store;
    private final AuthService auth;

    public TenantSettingsController(CapabilityStore store, AuthService auth) {
        this.store = store;
        this.auth = auth;
    }

    @GetMapping("/GetAllSettings")
    public AbpResponse<SystemSettingsItem.TenantSettings> getAllSettings(HttpServletRequest request) {
        return AbpResponse.ok(store.tenantSettings(currentTenantId(request)));
    }

    @PostMapping("/GetAllSettings")
    public AbpResponse<SystemSettingsItem.TenantSettings> postGetAllSettings(HttpServletRequest request) {
        return getAllSettings(request);
    }

    @PostMapping("/UpdateAllSettings")
    public AbpResponse<Void> updateAllSettings(
            @RequestBody(required = false) SystemSettingsItem.TenantSettings input,
            HttpServletRequest request) {
        return saveAllSettings(input, request);
    }

    @PutMapping("/UpdateAllSettings")
    public AbpResponse<Void> putUpdateAllSettings(
            @RequestBody(required = false) SystemSettingsItem.TenantSettings input,
            HttpServletRequest request) {
        return saveAllSettings(input, request);
    }

    private AbpResponse<Void> saveAllSettings(SystemSettingsItem.TenantSettings input, HttpServletRequest request) {
        String requiredSectionError = validateTenantSettingsInput(input);
        if (requiredSectionError != null) {
            return AbpResponse.failed(requiredSectionError);
        }
        String validationError = validateSessionTimeOutSettings(
                input.userManagement.sessionTimeOutSettings);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        store.updateTenantSettings(currentTenantId(request), input);
        return AbpResponse.ok(null);
    }

    @PostMapping("/SendTestEmail")
    public AbpResponse<Void> sendTestEmail(@RequestBody(required = false) SendTestEmailInput input) {
        String validationError = validateSendTestEmailInput(input);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        store.sendTestEmail("TenantSettingsAppService", input.emailAddress());
        return AbpResponse.ok(null);
    }

    @PostMapping("/ClearLogo")
    public AbpResponse<Void> clearLogo(HttpServletRequest request) {
        store.clearTenantLogo(currentTenantId(request));
        return AbpResponse.ok(null);
    }

    @PostMapping("/ClearCustomCss")
    public AbpResponse<Void> clearCustomCss(HttpServletRequest request) {
        store.clearTenantCustomCss(currentTenantId(request));
        return AbpResponse.ok(null);
    }

    private int currentTenantId(HttpServletRequest request) {
        return auth.currentUser(request.getHeader("Authorization"))
                .map(AuthContext::tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
    }

    private String validateSendTestEmailInput(SendTestEmailInput input) {
        if (input == null || input.emailAddress() == null || input.emailAddress().isBlank()
                || input.emailAddress().length() > 256) {
            // 原 SendTestEmailInput 要求 EmailAddress 必填且最多 256。
            return "Validation failed";
        }
        return null;
    }

    private String validateTenantSettingsInput(SystemSettingsItem.TenantSettings input) {
        if (input == null || input.userManagement == null || input.email == null || input.security == null) {
            // 原 TenantSettingsEditDto 要求 UserManagement/Security 必填，并通过 ValidateHostSettings 要求 Email。
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
