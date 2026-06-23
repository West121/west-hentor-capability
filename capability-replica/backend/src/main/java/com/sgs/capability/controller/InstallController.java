package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.model.InstallSettingsItem;
import com.sgs.capability.model.NameValueItem;
import com.sgs.capability.model.SystemSettingsItem;
import com.sgs.capability.service.CapabilityStore;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Mirrors InstallAppService setup and appsettings endpoints. */
@RestController
@RequestMapping("/api/services/app/Install")
public class InstallController {
    private final CapabilityStore store;

    public InstallController(CapabilityStore store) {
        this.store = store;
    }

    @PostMapping("/Setup")
    public AbpResponse<Void> setup(@RequestBody(required = false) InstallDto input) {
        InstallDto safeInput = input == null ? new InstallDto() : input;
        if (!hasText(safeInput.connectionString) || !hasText(safeInput.adminPassword)
                || !hasText(safeInput.webSiteUrl) || !hasText(safeInput.defaultLanguage)) {
            // 原 InstallDto 要求 ConnectionString、AdminPassword、WebSiteUrl、DefaultLanguage 必填。
            return AbpResponse.failed("Validation failed");
        }
        if (store.installDatabaseExists()) {
            return AbpResponse.failed("Setup process is already done.");
        }
        store.setupInstall(toSettings(safeInput), safeInput.adminPassword);
        return AbpResponse.ok(null);
    }

    @GetMapping("/GetAppSettingsJson")
    public AbpResponse<AppSettingsJsonDto> getAppSettingsJson() {
        InstallSettingsItem settings = store.installSettings();
        if (settings.webSiteRootAddressMode) {
            return AbpResponse.ok(new AppSettingsJsonDto(settings.webSiteUrl, null, null));
        }
        return AbpResponse.ok(new AppSettingsJsonDto(settings.webSiteUrl, settings.serverUrl, initialLanguages()));
    }

    @PostMapping("/GetAppSettingsJson")
    public AbpResponse<AppSettingsJsonDto> postAppSettingsJson() {
        return getAppSettingsJson();
    }

    @GetMapping("/CheckDatabase")
    public AbpResponse<CheckDatabaseOutput> checkDatabase() {
        return AbpResponse.ok(new CheckDatabaseOutput(store.installDatabaseExists()));
    }

    @PostMapping("/CheckDatabase")
    public AbpResponse<CheckDatabaseOutput> postCheckDatabase() {
        return checkDatabase();
    }

    private InstallSettingsItem toSettings(InstallDto input) {
        InstallSettingsItem item = new InstallSettingsItem();
        item.connectionString = input.connectionString;
        item.webSiteUrl = input.webSiteUrl;
        item.serverUrl = input.serverUrl;
        item.webSiteRootAddressMode = !hasText(input.serverUrl);
        item.defaultLanguage = input.defaultLanguage;
        item.smtpSettings = input.smtpSettings;
        item.billInfo = input.billInfo;
        return item;
    }

    private List<NameValueItem> initialLanguages() {
        return List.of(
                nameValue("English", "en"),
                nameValue("简体中文", "zh-Hans")
        );
    }

    private NameValueItem nameValue(String name, String value) {
        NameValueItem item = new NameValueItem();
        item.name = name;
        item.value = value;
        return item;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public static class InstallDto {
        public String connectionString;
        public String adminPassword;
        public String webSiteUrl;
        public String serverUrl;
        public String defaultLanguage;
        public SystemSettingsItem.EmailSettings smtpSettings;
        public SystemSettingsItem.HostBillingSettings billInfo;
    }

    public record AppSettingsJsonDto(String webSiteUrl, String serverSiteUrl, List<NameValueItem> languages) {
    }

    public record CheckDatabaseOutput(boolean isDatabaseExist) {
    }
}
