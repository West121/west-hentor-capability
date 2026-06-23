package com.sgs.capability.model;

/** Host and tenant settings copied from AspNet Zero settings DTOs. */
public class SystemSettingsItem {
    public static final String DEFAULT_ABILITY_DESCRIPTION = """
            <p><strong>按以下信息检索</strong></p>
            <p><span style="color:#ff5a26">▪</span>样品类型&nbsp;&nbsp;&nbsp; <span style="color:#ff5a26">▪</span>样品名称&nbsp;&nbsp;&nbsp; <span style="color:#ff5a26">▪</span>测试项目</p>
            <p><span style="color:#ff5a26">▪</span>标准号&nbsp;&nbsp;&nbsp; <span style="color:#ff5a26">▪</span>方法中文描述&nbsp;&nbsp;&nbsp; <span style="color:#ff5a26">▪</span>方法英文描述</p>
            <p><span style="color:#ff5a26">▪</span>实验室location缩写。如搜索天津实验室能力，输入TJ，不分大小写。</p>
            <p>&nbsp;</p>
            <p><strong>CNAS&amp;CMA盖章说明</strong></p>
            <p><span style="color:#ff5a26">▪</span>实验室能力一列显示了各个实验室的能力及CNAS&amp;CMA情况。</p>
            <p><span style="color:#ff5a26">▪</span>应CNAS及CMA要求，所检测的产品名称、检测项目及测试标准需与能力表一致，才能盖章。</p>
            <p><span style="color:#ff5a26">▪</span>如使用铁矿测试方法检测铬矿石中的Fe含量，则无法加盖CNAS或CMA章。</p>
            <p><span style="color:#ff5a26">▪</span>部分能力表中的样品名称包括多个，请在需要盖章时，与QA再次确认。</p>
            """;

    public static class HostSettings {
        public GeneralSettings general = new GeneralSettings();
        public HostUserManagementSettings userManagement = new HostUserManagementSettings();
        public EmailSettings email = new EmailSettings();
        public TenantManagementSettings tenantManagement = new TenantManagementSettings();
        public SecuritySettings security = new SecuritySettings();
        public HostBillingSettings billing = new HostBillingSettings();
        public OtherSettings otherSettings = new OtherSettings();
        public ExternalLoginProviderSettings externalLoginProviderSettings = new ExternalLoginProviderSettings();
    }

    public static class TenantSettings {
        public GeneralSettings general = new GeneralSettings();
        public TenantUserManagementSettings userManagement = new TenantUserManagementSettings();
        public TenantEmailSettings email = new TenantEmailSettings();
        public LdapSettings ldap = new LdapSettings();
        public SecuritySettings security = new SecuritySettings();
        public TenantBillingSettings billing = new TenantBillingSettings();
        public TenantOtherSettings otherSettings = new TenantOtherSettings();
        public ExternalLoginProviderSettings externalLoginProviderSettings = new ExternalLoginProviderSettings();
    }

    public static class AbilitySettings {
        public String description = DEFAULT_ABILITY_DESCRIPTION;
    }

    public static class GeneralSettings {
        public String timezone = "China Standard Time";
        public String timezoneForComparison = "China Standard Time";
    }

    public static class EmailSettings {
        public String defaultFromAddress = "noreply@example.local";
        public String defaultFromDisplayName = "Capability Table";
        public String smtpHost = "localhost";
        public int smtpPort = 25;
        public String smtpUserName = "";
        public String smtpPassword = "";
        public String smtpDomain = "";
        public boolean smtpEnableSsl;
        public boolean smtpUseDefaultCredentials = true;
    }

    public static class TenantEmailSettings extends EmailSettings {
        public boolean useHostDefaultEmailSettings = true;
    }

    public static class HostUserManagementSettings {
        public boolean isEmailConfirmationRequiredForLogin;
        public boolean smsVerificationEnabled;
        public boolean isCookieConsentEnabled = true;
        public boolean isQuickThemeSelectEnabled = true;
        public boolean useCaptchaOnLogin;
        public SessionTimeOutSettings sessionTimeOutSettings = new SessionTimeOutSettings();
    }

    public static class TenantUserManagementSettings {
        public boolean allowSelfRegistration = true;
        public boolean isNewRegisteredUserActiveByDefault;
        public boolean isEmailConfirmationRequiredForLogin;
        public boolean useCaptchaOnRegistration = true;
        public boolean useCaptchaOnLogin;
        public boolean isCookieConsentEnabled = true;
        public boolean isQuickThemeSelectEnabled = true;
        public SessionTimeOutSettings sessionTimeOutSettings = new SessionTimeOutSettings();
    }

    public static class TenantManagementSettings {
        public boolean allowSelfRegistration = true;
        public boolean isNewRegisteredTenantActiveByDefault;
        public boolean useCaptchaOnRegistration = true;
        public Integer defaultEditionId;
    }

    public static class SessionTimeOutSettings {
        public boolean isEnabled;
        public int timeOutSecond = 30;
        public int showTimeOutNotificationSecond = 30;
        public boolean showLockScreenWhenTimedOut;
    }

    public static class SecuritySettings {
        public boolean allowOneConcurrentLoginPerUser;
        public boolean useDefaultPasswordComplexitySettings = true;
        public PasswordComplexitySetting passwordComplexity = new PasswordComplexitySetting();
        public PasswordComplexitySetting defaultPasswordComplexity = new PasswordComplexitySetting();
        public UserLockOutSettings userLockOut = new UserLockOutSettings();
        public TwoFactorLoginSettings twoFactorLogin = new TwoFactorLoginSettings();
    }

    public static class PasswordComplexitySetting {
        public boolean requireDigit = true;
        public boolean requireLowercase;
        public boolean requireNonAlphanumeric;
        public boolean requireUppercase;
        public int requiredLength = 6;
    }

    public static class UserLockOutSettings {
        public boolean isEnabled = true;
        public int maxFailedAccessAttemptsBeforeLockout = 5;
        public int defaultAccountLockoutSeconds = 300;
    }

    public static class TwoFactorLoginSettings {
        public boolean isEnabledForApplication = true;
        public boolean isEnabled;
        public boolean isEmailProviderEnabled = true;
        public boolean isSmsProviderEnabled;
        public boolean isRememberBrowserEnabled = true;
        public boolean isGoogleAuthenticatorEnabled;
    }

    public static class HostBillingSettings {
        public String legalName = "SGS Local Replica";
        public String address = "Local development environment";
    }

    public static class TenantBillingSettings extends HostBillingSettings {
        public String taxVatNo = "";
    }

    public static class OtherSettings {
        public boolean isQuickThemeSelectEnabled = true;
    }

    public static class TenantOtherSettings extends OtherSettings {
    }

    public static class LdapSettings {
        public boolean isModuleEnabled = true;
        public boolean isEnabled;
        public String domain = "";
        public String userName = "";
        public String password = "";
    }

    public static class ExternalLoginProviderSettings {
        public FacebookSettings facebook = new FacebookSettings();
        public GoogleSettings google = new GoogleSettings();
        public TwitterSettings twitter = new TwitterSettings();
        public MicrosoftSettings microsoft = new MicrosoftSettings();
    }

    public static class FacebookSettings {
        public String appId = "";
        public String appSecret = "";
    }

    public static class GoogleSettings {
        public String clientId = "";
        public String clientSecret = "";
        public String userInfoEndpoint = "https://www.googleapis.com/oauth2/v3/userinfo";
    }

    public static class TwitterSettings {
        public String consumerKey = "";
        public String consumerSecret = "";
    }

    public static class MicrosoftSettings {
        public String clientId = "";
        public String clientSecret = "";
    }

    public static HostSettings defaultHostSettings() {
        return new HostSettings();
    }

    public static TenantSettings defaultTenantSettings() {
        TenantSettings settings = new TenantSettings();
        settings.billing.legalName = "SGS Tenant Replica";
        settings.billing.address = "Local tenant environment";
        settings.billing.taxVatNo = "LOCAL-TAX-001";
        return settings;
    }

    public static AbilitySettings defaultAbilitySettings() {
        return new AbilitySettings();
    }
}
