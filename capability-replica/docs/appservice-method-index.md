# AppService Method Index

This index is extracted from the decompiled backend interfaces under `/Users/west/Downloads/capability/.decompiled`.

Coverage: 54 AppService interfaces, 271 methods.

Route coverage check:

- Command: `node scripts/check-appservice-route-coverage.mjs`
- Result: `expectedAppServiceMethods=271`, `javaAppRoutePaths=288`, `javaAppRouteVariants=438`, `missingCount=0`.
- Alias note: the decompiled interface `IWebhookAttemptAppService` is implemented and routed as `WebhookSendAttemptAppService`, so the Java replica checks it under `/api/services/app/WebhookSendAttempt/...`.
- Scope note: this verifies Spring route entry-point coverage for the original AppService methods. Behavioral parity is covered by the dedicated `*ParityTest` tests and still needs method-level audits when changing individual features.

Method coverage audit:

- Command: `node scripts/audit-appservice-method-coverage.mjs`
- Result: `expectedAppServiceMethods=271`, `backendRouteMissingCount=0`, `frontendSourceMissingCount=0`, `backendTestMissingCount=0`, `frontendTestMissingCount=0`.
- Scope note: this is method/route/API/test-entry coverage. Production-exact behavior remains covered by the dedicated parity tests and feature notes.

| Interface | Count | Methods |
| --- | ---: | --- |
| `IAbilityAppService` | 13 | `FindPageAblibities`, `DeleteAll`, `GetMyOrgSetting`, `ExportData`, `GetAbilityForEdit`, `CreateAbility`, `UpdateAbility`, `DeleteAbility`, `GetAllUnits`, `GetTemplateExcel`, `SaveExcelData`, `FindAllAblibities`, `GetOrgTypeLit` |
| `IAbilityHistoryAppService` | 2 | `GetAbilityHistory`, `GetHistoryDetail` |
| `IAbilityPropertyAppService` | 4 | `AbilityPropertyList`, `OrgAbilityPropertyList`, `SaveOrgSetting`, `GetOrgAbilitySetting` |
| `IAbilityQueryAppService` | 2 | `FindAblibities`, `FindHistory` |
| `IAccountAppService` | 10 | `IsTenantAvailable`, `ResolveTenantId`, `Register`, `SendPasswordResetCode`, `ResetPassword`, `SendEmailActivationLink`, `ActivateEmail`, `Impersonate`, `BackToImpersonator`, `SwitchToLinkedAccount` |
| `IAuditLogAppService` | 7 | `GetAuditLogs`, `GetAuditLogsToExcel`, `GetEntityChanges`, `GetEntityTypeChanges`, `GetEntityChangesToExcel`, `GetEntityPropertyChanges`, `GetEntityHistoryObjectTypes` |
| `ICachingAppService` | 3 | `GetAllCaches`, `ClearCache`, `ClearAllCaches` |
| `IChatAppService` | 3 | `GetUserChatFriendsWithSettings`, `GetUserChatMessages`, `MarkAllUnreadMessagesOfUserAsRead` |
| `ICommonLookupAppService` | 3 | `GetEditionsForCombobox`, `FindUsers`, `GetDefaultEditionName` |
| `IDashboardAppService` | 3 | `Statistics`, `OrgCount`, `ChangeCountInWeek` |
| `IDashboardCustomizationAppService` | 8 | `GetUserDashboard`, `SavePage`, `RenamePage`, `AddNewPage`, `AddWidget`, `DeletePage`, `GetDashboardDefinition`, `GetAllWidgetDefinitions` |
| `IDemoUiComponentsAppService` | 6 | `SendAndGetDate`, `SendAndGetDateTime`, `SendAndGetDateRange`, `GetCountries`, `SendAndGetSelectedCountries`, `SendAndGetValue` |
| `IDynamicEntityParameterDefinitionAppService` | 2 | `GetAllAllowedInputTypeNames`, `GetAllEntities` |
| `IDynamicParameterAppService` | 6 | `Get`, `GetAll`, `Add`, `Update`, `Delete`, `FindAllowedInputType` |
| `IDynamicParameterValueAppService` | 5 | `Get`, `GetAllValuesOfDynamicParameter`, `Add`, `Update`, `Delete` |
| `IEditionAppService` | 8 | `GetEditions`, `GetEditionForEdit`, `CreateEdition`, `UpdateEdition`, `DeleteEdition`, `MoveTenantsToAnotherEdition`, `GetEditionComboboxItems`, `GetTenantCount` |
| `IEntityDynamicParameterAppService` | 6 | `Get`, `GetAllParametersOfAnEntity`, `GetAll`, `Add`, `Update`, `Delete` |
| `IEntityDynamicParameterValueAppService` | 6 | `Get`, `GetAll`, `Add`, `Update`, `Delete`, `GetAllEntityDynamicParameterValues` |
| `IFriendshipAppService` | 5 | `CreateFriendshipRequest`, `CreateFriendshipRequestByUserName`, `BlockUser`, `UnblockUser`, `AcceptFriendshipRequest` |
| `IHostDashboardAppService` | 5 | `GetTopStatsData`, `GetRecentTenantsData`, `GetSubscriptionExpiringTenantsData`, `GetIncomeStatistics`, `GetEditionTenantStatistics` |
| `IHostSettingsAppService` | 5 | `GetAllSettings`, `UpdateAllSettings`, `SendTestEmail`, `GetAbilitySettings`, `UpdateAbilitySettings` |
| `IInstallAppService` | 3 | `Setup`, `GetAppSettingsJson`, `CheckDatabase` |
| `IInvoiceAppService` | 2 | `GetInvoiceInfo`, `CreateInvoice` |
| `ILaboratoryAppService` | 4 | `List`, `CreateOrUpdate`, `GetLabForEdit`, `DeleteLab` |
| `ILanguageAppService` | 7 | `GetLanguages`, `GetLanguageForEdit`, `CreateOrUpdateLanguage`, `DeleteLanguage`, `SetDefaultLanguage`, `GetLanguageTexts`, `UpdateLanguageText` |
| `IMyFavoriteAppService` | 7 | `GetMyFavoriteList`, `GetMyFavoriteAbilityList`, `SaveOrUpdateMyFavorite`, `GetMyFavoriteForEdit`, `DeleteMyFavorite`, `AddItem`, `RemoveItem` |
| `INotificationAppService` | 7 | `GetUserNotifications`, `SetAllNotificationsAsRead`, `SetNotificationAsRead`, `GetNotificationSettings`, `UpdateNotificationSettings`, `DeleteNotification`, `DeleteAllUserNotifications` |
| `IOrganizationUnitAppService` | 12 | `GetOrganizationUnits`, `GetOrganizationUnitUsers`, `CreateOrganizationUnit`, `UpdateOrganizationUnit`, `MoveOrganizationUnit`, `DeleteOrganizationUnit`, `RemoveUserFromOrganizationUnit`, `RemoveRoleFromOrganizationUnit`, `AddUsersToOrganizationUnit`, `AddRolesToOrganizationUnit`, `FindUsers`, `FindRoles` |
| `IPaymentAppService` | 15 | `GetPaymentInfo`, `CreatePayment`, `CancelPayment`, `GetPaymentHistory`, `GetActiveGateways`, `GetPaymentAsync`, `GetLastCompletedPayment`, `BuyNowSucceed`, `NewRegistrationSucceed`, `UpgradeSucceed`, `ExtendSucceed`, `PaymentFailed`, `SwitchBetweenFreeEditions`, `UpgradeSubscriptionCostsLessThenMinAmount`, `HasAnyPayment` |
| `IPayPalPaymentAppService` | 2 | `ConfirmPayment`, `GetConfiguration` |
| `IPermissionAppService` | 1 | `GetAllPermissions` |
| `IProfileAppService` | 13 | `GetCurrentUserProfileForEdit`, `UpdateCurrentUserProfile`, `ChangePassword`, `UpdateProfilePicture`, `GetPasswordComplexitySetting`, `GetProfilePicture`, `GetProfilePictureById`, `GetFriendProfilePictureById`, `ChangeLanguage`, `UpdateGoogleAuthenticatorKey`, `SendVerificationSms`, `VerifySmsCode`, `PrepareCollectedData` |
| `IRoleAppService` | 4 | `GetRoles`, `GetRoleForEdit`, `CreateOrUpdateRole`, `DeleteRole` |
| `ISampleAppService` | 4 | `GetList`, `CreateOrUpdate`, `GetForEdit`, `DeleteSample` |
| `ISampleTypeAppService` | 5 | `GetList`, `GetListByOrg`, `CreateOrUpdate`, `GetForEdit`, `DeleteSampleType` |
| `ISessionAppService` | 2 | `GetCurrentLoginInformations`, `UpdateUserSignInToken` |
| `IStandardAppService` | 1 | `UploadNewStandard` |
| `IStripePaymentAppService` | 4 | `ConfirmPayment`, `GetConfiguration`, `GetPaymentAsync`, `CreatePaymentSession` |
| `ISubcontractAbilityAppService` | 2 | `FindList`, `SaveExcelData` |
| `ISubscriptionAppService` | 2 | `DisableRecurringPayments`, `EnableRecurringPayments` |
| `ITenantAppService` | 9 | `GetTenants`, `CreateTenant`, `GetTenantForEdit`, `UpdateTenant`, `DeleteTenant`, `GetTenantFeaturesForEdit`, `UpdateTenantFeatures`, `ResetTenantSpecificFeatures`, `UnlockTenantAdmin` |
| `ITenantDashboardAppService` | 8 | `GetMemberActivity`, `GetDashboardData`, `GetDailySales`, `GetProfitShare`, `GetSalesSummary`, `GetTopStats`, `GetRegionalStats`, `GetGeneralStats` |
| `ITenantRegistrationAppService` | 3 | `RegisterTenant`, `GetEditionsForSelect`, `GetEdition` |
| `ITenantSettingsAppService` | 4 | `GetAllSettings`, `UpdateAllSettings`, `ClearLogo`, `ClearCustomCss` |
| `ITimingAppService` | 2 | `GetTimezones`, `GetTimezoneComboboxItems` |
| `IUiCustomizationSettingsAppService` | 4 | `GetUiManagementSettings`, `UpdateUiManagementSettings`, `UpdateDefaultUiManagementSettings`, `UseSystemDefaultSettings` |
| `IUserAppService` | 10 | `GetUsers`, `GetUsersToExcel`, `GetUserForEdit`, `GetUserPermissionsForEdit`, `ResetUserSpecificPermissions`, `ResetUserPassword`, `UpdateUserPermissions`, `CreateOrUpdateUser`, `DeleteUser`, `UnlockUser` |
| `IUserDelegationAppService` | 4 | `GetDelegatedUsers`, `DelegateNewUser`, `RemoveDelegation`, `GetActiveUserDelegations` |
| `IUserLinkAppService` | 4 | `LinkToUser`, `GetLinkedUsers`, `GetRecentlyUsedLinkedUsers`, `UnlinkUser` |
| `IUserLoginAppService` | 1 | `GetRecentUserLoginAttempts` |
| `IWebhookAttemptAppService` | 2 | `GetAllSendAttempts`, `GetAllSendAttemptsOfWebhookEvent` |
| `IWebhookEventAppService` | 1 | `Get` |
| `IWebhookSubscriptionAppService` | 8 | `GetAllSubscriptions`, `GetSubscription`, `AddSubscription`, `UpdateSubscription`, `ActivateWebhookSubscription`, `IsSubscribed`, `GetAllSubscriptionsIfFeaturesGranted`, `GetAllAvailableWebhooks` |
| `IWebLogAppService` | 2 | `GetLatestWebLogs`, `DownloadWebLogs` |
