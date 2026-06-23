package com.sgs.capability.model;

import java.time.LocalDateTime;

/** Local setup state mirrored from InstallDto and appsettings.json. */
public class InstallSettingsItem {
    public String connectionString = "";
    public String webSiteUrl = "http://localhost:5173/";
    public String serverUrl = "http://localhost:9901/";
    public boolean webSiteRootAddressMode;
    public String defaultLanguage = "zh-Hans";
    public SystemSettingsItem.EmailSettings smtpSettings = new SystemSettingsItem.EmailSettings();
    public SystemSettingsItem.HostBillingSettings billInfo = new SystemSettingsItem.HostBillingSettings();
    public boolean installed;
    public String setupTime;

    public static InstallSettingsItem defaults() {
        InstallSettingsItem item = new InstallSettingsItem();
        item.setupTime = LocalDateTime.now().toString();
        return item;
    }
}
