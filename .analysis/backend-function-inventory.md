# 后端反编译功能清单

工作目录：`/Users/west/Downloads/capability`

反编译输出：`.decompiled/`

说明：这是从发布后的 DLL 反编译得到的代码。可用于理解模块、接口、实体和迁移 Java 的范围，但不等同于原始源码；Debug 状态机代码较多，清单只统计顶层公开 AppService/Controller 方法。

## 总览

- 后端：ASP.NET Core 3.1 / C# / ABP + AspNet Zero
- 数据层：Entity Framework Core，主要数据库应为 SQL Server
- 前端：Angular SPA，静态资源位于 `wwwroot/`
- 关键业务：能力表、实验室、样品/样品类型、组织能力字段配置、Excel 导入导出、能力查询、分包能力、收藏、审计/变更历史
- 平台能力：多租户、用户/角色/权限、组织架构、登录/JWT/二次认证/外部登录、通知、聊天、Webhook、支付、仪表盘、语言、设置、缓存、日志

## 核心业务模块

- **能力表管理**：维护 `MineralAbilityTable`：业务部门、样品类型、样品名称、测试项目、方法描述、标准号、检测周期、样品量、粒度、适用范围、价格、备注、实验室能力、SGS/SOP/OTHERS/DZ 标准编号。
- **能力查询**：按动态字段和组织设置查询能力表，并查看单条能力的变更历史。
- **组织能力字段配置**：按业务部门配置能力表可见/启用字段、是否公开和说明文字。
- **实验室管理**：维护实验室简称、名称、负责人、联系方式、地址、CNAS/CMS 标识。
- **样品和样品类型**：维护样品类型与业务部门关系、样品中文名/英文名/别名。
- **Excel 导入导出**：导出能力表模板、导入能力表、识别重复数据、保存新数据或覆盖重复数据；导入分包能力；上传新版标准文件。
- **收藏夹**：用户可建收藏夹并收藏/移除能力项。
- **审计和历史**：ABP 审计日志、实体变更、能力表历史明细。

## 业务实体/表

- `MineralAbilityTable` / `AbilityTable`：`OrgName`, `OrgId`, `TypeName`, `TypeId`, `SamplingName`, `SamplingId`, `ProductCode`, `TestItem`, `TestItemRemark`, `MethodName`, `MethodRemark`, `MethodEngName`, `StandardNo`, `GbNo`, `GbRemark`, `IsoNo`, `IsoRemark`, `GbtNo`, `GbtRemark`, `AstmNo`, `AstmRemark`, `IndustryStandardNo`, `IndustryStandardRemark`, `OtherNo`, `OtherRemark`, `CycleWorkingDay`, `TestTime`, `TestTimeRemark`, `MassRequired`, `MassRequiredRemark`, `SizeRequired`, `SizeRequiredRemark`, `DetectionLimit`, `Price`, `PriceRemark`, `Remark`, `LabAbility`, `StandardNoSgs`, `StandardNoSop`, `StandardNoOthers`, `StandardNoDz`, `LabAbilities`
- `OrgAbilityPropertySettings` / `OrgAbilityPropertySetting`：`OrgId`, `Properties`, `Labs`, `IsPublic`, `Description`
- `MineralSubcontractAbility` / `SubcontractAbility`：`LabName`, `ContactDetails`, `TestCategory`, `CmaOrCnas`, `Gist`, `Appraiser`, `EvaluationResult`
- `AppUserDelegations` / `UserDelegation`：`SourceUserId`, `TargetUserId`, `TenantId`, `StartTime`, `EndTime`
- `AppChatMessages` / `ChatMessage`：`UserId`, `TenantId`, `TargetUserId`, `TargetTenantId`, `Message`, `CreationTime`, `Side`, `ReadState`, `ReceiverReadState`, `SharedMessageId`
- `AppFriendships` / `Friendship`：`UserId`, `TenantId`, `FriendUserId`, `FriendTenantId`, `FriendUserName`, `FriendTenancyName`, `FriendProfilePictureId`, `State`, `CreationTime`
- `MineralLaboratory` / `Laboratory`：`Code`, `Name`, `EngName`, `Describe`, `Address`, `Leader`, `ContactInfo`, `HasCnas`, `HasCms`
- `MineralLaboratoryUser` / `LaboratoryUser`：`LabId`, `UserId`
- `AppInvoices` / `Invoice`：`InvoiceNo`, `InvoiceDate`, `TenantLegalName`, `TenantAddress`, `TenantTaxNo`
- `AppSubscriptionPayments` / `SubscriptionPayment`：`Description`, `Gateway`, `Amount`, `Status`, `EditionId`, `TenantId`, `DayCount`, `PaymentPeriodType`, `ExternalPaymentId`, `Edition`, `InvoiceNo`, `IsRecurring`, `SuccessUrl`, `ErrorUrl`, `EditionPaymentType`
- `AppSubscriptionPaymentsExtensionData` / `SubscriptionPaymentExtensionData`：`SubscriptionPaymentId`, `Key`, `Value`, `IsDeleted`
- `MineralSample` / `Sample`：`DisplayName`, `EngName`, `Alias`, `TypeId`, `TypeName`
- `MineralSampleType` / `SampleType`：`DisplayName`, `OrgId`, `OrgName`
- `AppBinaryObjects` / `BinaryObject`：`TenantId`

## 权限点

- `Pages`
- `Pages.DemoUiComponents`
- `Pages.Administration`
- `Pages.Administration.Roles`
- `Pages.Administration.Roles.Create`
- `Pages.Administration.Roles.Edit`
- `Pages.Administration.Roles.Delete`
- `Pages.Administration.Users`
- `Pages.Administration.Users.Create`
- `Pages.Administration.Users.Edit`
- `Pages.Administration.Users.Delete`
- `Pages.Administration.Users.ChangePermissions`
- `Pages.Administration.Users.Impersonation`
- `Pages.Administration.Users.Unlock`
- `Pages.Administration.Languages`
- `Pages.Administration.Languages.Create`
- `Pages.Administration.Languages.Edit`
- `Pages.Administration.Languages.Delete`
- `Pages.Administration.Languages.ChangeTexts`
- `Pages.Administration.AuditLogs`
- `Pages.Administration.OrganizationUnits`
- `Pages.Administration.OrganizationUnits.ManageOrganizationTree`
- `Pages.Administration.OrganizationUnits.ManageMembers`
- `Pages.Administration.OrganizationUnits.ManageRoles`
- `Pages.Administration.HangfireDashboard`
- `Pages.Administration.UiCustomization`
- `Pages.Administration.WebhookSubscription`
- `Pages.Administration.WebhookSubscription.Create`
- `Pages.Administration.WebhookSubscription.Edit`
- `Pages.Administration.WebhookSubscription.ChangeActivity`
- `Pages.Administration.WebhookSubscription.Detail`
- `Pages.Administration.Webhook.ListSendAttempts`
- `Pages.Administration.Webhook.ResendWebhook`
- `Pages.Administration.DynamicParameters`
- `Pages.Administration.DynamicParameters.Create`
- `Pages.Administration.DynamicParameters.Edit`
- `Pages.Administration.DynamicParameters.Delete`
- `Pages.Administration.DynamicParameterValue`
- `Pages.Administration.DynamicParameterValue.Create`
- `Pages.Administration.DynamicParameterValue.Edit`
- `Pages.Administration.DynamicParameterValue.Delete`
- `Pages.Administration.EntityDynamicParameters`
- `Pages.Administration.EntityDynamicParameters.Create`
- `Pages.Administration.EntityDynamicParameters.Edit`
- `Pages.Administration.EntityDynamicParameters.Delete`
- `Pages.Administration.EntityDynamicParameterValue`
- `Pages.Administration.EntityDynamicParameterValue.Create`
- `Pages.Administration.EntityDynamicParameterValue.Edit`
- `Pages.Administration.EntityDynamicParameterValue.Delete`
- `Pages.Tenant.Dashboard`
- `Pages.Administration.Tenant.Settings`
- `Pages.Administration.Tenant.SubscriptionManagement`
- `Pages.Editions`
- `Pages.Editions.Create`
- `Pages.Editions.Edit`
- `Pages.Editions.Delete`
- `Pages.Editions.MoveTenantsToAnotherEdition`
- `Pages.Tenants`
- `Pages.Tenants.Create`
- `Pages.Tenants.Edit`
- `Pages.Tenants.ChangeFeatures`
- `Pages.Tenants.Delete`
- `Pages.Tenants.Impersonation`
- `Pages.Administration.Host.Maintenance`
- `Pages.Administration.Host.Settings`
- `Pages.Administration.Host.Dashboard`
- `Pages.Administration.Laboratory`
- `Pages.Administration.Laboratory.Create`
- `Pages.Administration.Laboratory.Edit`
- `Pages.Administration.Laboratory.Delete`
- `Pages.Administration.StandardUpdate`
- `Pages.AbilityManagement`
- `Pages.AbilityManagement.EditDesc`
- `Pages.AbilityManagement.Sample`
- `Pages.AbilityManagement.Ability`
- `Pages.AbilityManagement.Ability.Edit`
- `Pages.AbilityManagement.Ability.PublicEdit`
- `Pages.AbilityManagement.Ability.DeleteAll`
- `Pages.AbilityManagement.Ability.Create`
- `Pages.AbilityManagement.Ability.Delete`
- `Pages.AbilityManagement.Ability.ImportExcel`
- `Pages.AbilityManagement.Ability.History`
- `Pages.AbilityManagement.AbilitySetting`
- `Pages.AbilityQuery`
- `Pages.Administration.AbilityHistory`
- `Pages.Log`
- `Pages.Log.AbilityHistory`

## AppService 方法

### AbilityAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.AbilityTables/AbilityAppService.cs`
- `GetOrgTypeLit(EntityDto<long?> input)` -> `System.Threading.Tasks.Task<ListResultDto<NameValueDto>>`
- `DeleteAll(DeleteAllDto input)` -> `System.Threading.Tasks.Task`
- `GetMyOrgSetting()` -> `System.Threading.Tasks.Task<ListResultDto<MyOrgSettingDto>>`；属性：`AbpAuthorize(new string[] { })`
- `FindPageAblibities(FindPageAblibitiesInput input)` -> `System.Threading.Tasks.Task<AbilityPageDto>`
- `ExportData(ExportAblibitiesDataInput input)` -> `System.Threading.Tasks.Task<FileDto>`；属性：`AbpAuthorize(new string[] { })`
- `FindAllAblibities()` -> `System.Threading.Tasks.Task<List<CreateAbilityInput>>`；属性：`AbpAuthorize(new string[] { })`
- `GetTemplateExcel(GetTemplateExcelExcelInput input)` -> `System.Threading.Tasks.Task<FileDto>`；属性：`AbpAuthorize(new string[] { })`
- `SaveExcelData(SaveExcelDataInput input)` -> `System.Threading.Tasks.Task`；属性：`UseCase(Description = "EXCEL导入"); AbpAuthorize(new string[] { "Pages.AbilityManagement.Ability.Edit" })`
- `GetAllUnits()` -> `System.Threading.Tasks.Task<ListResultDto<OrganizationUnitDto>>`；属性：`AbpAuthorize(new string[] { })`
- `GetAbilityForEdit(NullableIdDto<Guid> input)` -> `System.Threading.Tasks.Task<GetAbilityForEditOutput>`
- `CreateAbility(CreateAbilityInput input)` -> `System.Threading.Tasks.Task`；属性：`AbpAuthorize(new string[] { "Pages.AbilityManagement.Ability.Edit" }); UseCase(Description = "编辑维护")`
- `UpdateAbility(UpdateAbilityInput input)` -> `System.Threading.Tasks.Task`
- `DeleteAbility(EntityDto<Guid> input)` -> `System.Threading.Tasks.Task`；属性：`AbpAuthorize(new string[] { "Pages.AbilityManagement.Ability.Edit" })`

### AbilityHistoryAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.AbilityTables/AbilityHistoryAppService.cs`
- `GetAbilityHistory(GetAbilityHistoryInput input)` -> `System.Threading.Tasks.Task<PagedResultDto<AbilityHistoryDto>>`
- `GetHistoryDetail(EntityDto<long> input)` -> `System.Threading.Tasks.Task<ListResultDto<AbilityHistoryDetailDto>>`

### AbilityPropertyAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.AbilityTables/AbilityPropertyAppService.cs`
- `AbilityPropertyList()` -> `System.Threading.Tasks.Task<List<AbilityPropertyDto>>`
- `OrgAbilityPropertyList(OrgAbilityPropertyListInput input)` -> `System.Threading.Tasks.Task<OrgAbilityPropertyListOutput>`
- `GetOrgAbilitySetting(GetOrgAbilitySettingInput input)` -> `System.Threading.Tasks.Task<List<string>>`
- `SaveOrgSetting(SaveOrgSettingInput input)` -> `System.Threading.Tasks.Task`

### AbilityQueryAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.AbilityTables/AbilityQueryAppService.cs`
- `FindHistory(EntityDto<Guid> input)` -> `System.Threading.Tasks.Task<List<AbilityHistoryItemDto>>`；属性：`AbpAuthorize(new string[] { "Pages.AbilityQuery" })`
- `FindAblibities(FindAblibitiesInput input)` -> `System.Threading.Tasks.Task<AbilityPageDto>`

### StandardAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.AbilityTables/StandardAppService.cs`
- `UploadNewStandard(UploadNewStandardDto input)` -> `System.Threading.Tasks.Task`

### SubcontractAbilityAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.AbilityTables/SubcontractAbilityAppService.cs`
- `SaveExcelData(SaveSubcontractAbilityExcelInput input)` -> `System.Threading.Tasks.Task`；属性：`UseCase(Description = "EXCEL导入")`
- `FindList(FindSubcontractAbilityListDto input)` -> `System.Threading.Tasks.Task<PagedResultDto<SubcontractAbilityDto>>`

### AuditLogAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.Auditing/AuditLogAppService.cs`
- `GetAuditLogs(GetAuditLogsInput input)` -> `System.Threading.Tasks.Task<PagedResultDto<AuditLogListDto>>`
- `GetAuditLogsToExcel(GetAuditLogsInput input)` -> `System.Threading.Tasks.Task<FileDto>`
- `GetEntityHistoryObjectTypes()` -> `List<NameValueDto>`
- `GetEntityChanges(GetEntityChangeInput input)` -> `System.Threading.Tasks.Task<PagedResultDto<EntityChangeListDto>>`
- `GetEntityTypeChanges(GetEntityTypeChangeInput input)` -> `System.Threading.Tasks.Task<PagedResultDto<EntityChangeListDto>>`
- `GetEntityChangesToExcel(GetEntityChangeInput input)` -> `System.Threading.Tasks.Task<FileDto>`
- `GetEntityPropertyChanges(long entityChangeId)` -> `System.Threading.Tasks.Task<List<EntityPropertyChangeDto>>`

### AccountAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.Authorization.Accounts/AccountAppService.cs`
- `IsTenantAvailable(IsTenantAvailableInput input)` -> `System.Threading.Tasks.Task<IsTenantAvailableOutput>`
- `ResolveTenantId(ResolveTenantIdInput input)` -> `System.Threading.Tasks.Task<int?>`
- `Register(RegisterInput input)` -> `System.Threading.Tasks.Task<RegisterOutput>`
- `SendPasswordResetCode(SendPasswordResetCodeInput input)` -> `System.Threading.Tasks.Task`
- `ResetPassword(ResetPasswordInput input)` -> `System.Threading.Tasks.Task<ResetPasswordOutput>`
- `SendEmailActivationLink(SendEmailActivationLinkInput input)` -> `System.Threading.Tasks.Task`
- `ActivateEmail(ActivateEmailInput input)` -> `System.Threading.Tasks.Task`
- `Impersonate(ImpersonateInput input)` -> `virtual async System.Threading.Tasks.Task<ImpersonateOutput>`；属性：`AbpAuthorize(new string[] { "Pages.Administration.Users.Impersonation" })`
- `DelegatedImpersonate(DelegatedImpersonateInput input)` -> `virtual async System.Threading.Tasks.Task<ImpersonateOutput>`
- `BackToImpersonator()` -> `virtual async System.Threading.Tasks.Task<ImpersonateOutput>`
- `SwitchToLinkedAccount(SwitchToLinkedAccountInput input)` -> `virtual async System.Threading.Tasks.Task<SwitchToLinkedAccountOutput>`

### PermissionAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.Authorization.Permissions/PermissionAppService.cs`
- `GetAllPermissions()` -> `ListResultDto<FlatPermissionWithLevelDto>`

### RoleAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.Authorization.Roles/RoleAppService.cs`
- `GetRoles(GetRolesInput input)` -> `System.Threading.Tasks.Task<ListResultDto<RoleListDto>>`
- `GetRoleForEdit(NullableIdDto input)` -> `System.Threading.Tasks.Task<GetRoleForEditOutput>`；属性：`AbpAuthorize(new string[] { })`
- `CreateOrUpdateRole(CreateOrUpdateRoleInput input)` -> `System.Threading.Tasks.Task`
- `DeleteRole(EntityDto input)` -> `System.Threading.Tasks.Task`；属性：`AbpAuthorize(new string[] { })`
- `UpdateRoleAsync(CreateOrUpdateRoleInput input)` -> `virtual System.Threading.Tasks.Task`；属性：`AbpAuthorize(new string[] { })`
- `CreateRoleAsync(CreateOrUpdateRoleInput input)` -> `virtual System.Threading.Tasks.Task`；属性：`AbpAuthorize(new string[] { })`

### UserAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.Authorization.Users/UserAppService.cs`
- `GetUsers(GetUsersInput input)` -> `System.Threading.Tasks.Task<PagedResultDto<UserListDto>>`
- `GetUsersToExcel(GetUsersToExcelInput input)` -> `System.Threading.Tasks.Task<FileDto>`
- `GetUserForEdit(NullableIdDto<long> input)` -> `System.Threading.Tasks.Task<GetUserForEditOutput>`；属性：`AbpAuthorize(new string[] { })`
- `GetUserPermissionsForEdit(EntityDto<long> input)` -> `System.Threading.Tasks.Task<GetUserPermissionsForEditOutput>`；属性：`AbpAuthorize(new string[] { })`
- `ResetUserSpecificPermissions(EntityDto<long> input)` -> `System.Threading.Tasks.Task`；属性：`AbpAuthorize(new string[] { })`
- `UpdateUserPermissions(UpdateUserPermissionsInput input)` -> `System.Threading.Tasks.Task`；属性：`AbpAuthorize(new string[] { })`
- `CreateOrUpdateUser(CreateOrUpdateUserInput input)` -> `System.Threading.Tasks.Task`
- `DeleteUser(EntityDto<long> input)` -> `System.Threading.Tasks.Task`；属性：`AbpAuthorize(new string[] { })`
- `UnlockUser(EntityDto<long> input)` -> `System.Threading.Tasks.Task`；属性：`AbpAuthorize(new string[] { })`
- `UpdateUserAsync(CreateOrUpdateUserInput input)` -> `virtual System.Threading.Tasks.Task`；属性：`AbpAuthorize(new string[] { })`
- `CreateUserAsync(CreateOrUpdateUserInput input)` -> `virtual System.Threading.Tasks.Task`；属性：`AbpAuthorize(new string[] { })`
- `ResetUserPassword(EntityDto<long> input)` -> `System.Threading.Tasks.Task`

### UserLinkAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.Authorization.Users/UserLinkAppService.cs`
- `LinkToUser(LinkToUserInput input)` -> `System.Threading.Tasks.Task`
- `GetLinkedUsers(GetLinkedUsersInput input)` -> `System.Threading.Tasks.Task<PagedResultDto<LinkedUserDto>>`
- `GetRecentlyUsedLinkedUsers()` -> `System.Threading.Tasks.Task<ListResultDto<LinkedUserDto>>`
- `UnlinkUser(UnlinkUserInput input)` -> `System.Threading.Tasks.Task`

### UserLoginAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.Authorization.Users/UserLoginAppService.cs`
- `GetRecentUserLoginAttempts()` -> `System.Threading.Tasks.Task<ListResultDto<UserLoginAttemptDto>>`

### UserDelegationAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.Authorization.Users.Delegation/UserDelegationAppService.cs`
- `GetDelegatedUsers(GetUserDelegationsInput input)` -> `System.Threading.Tasks.Task<PagedResultDto<UserDelegationDto>>`
- `DelegateNewUser(CreateUserDelegationDto input)` -> `System.Threading.Tasks.Task`
- `RemoveDelegation(EntityDto<long> input)` -> `System.Threading.Tasks.Task`
- `GetActiveUserDelegations()` -> `System.Threading.Tasks.Task<List<UserDelegationDto>>`

### ProfileAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.Authorization.Users.Profile/ProfileAppService.cs`
- `GetCurrentUserProfileForEdit()` -> `System.Threading.Tasks.Task<CurrentUserProfileEditDto>`
- `DisableGoogleAuthenticator()` -> `System.Threading.Tasks.Task`
- `UpdateGoogleAuthenticatorKey()` -> `System.Threading.Tasks.Task<UpdateGoogleAuthenticatorKeyOutput>`
- `SendVerificationSms(SendVerificationSmsInputDto input)` -> `System.Threading.Tasks.Task`
- `VerifySmsCode(VerifySmsCodeInputDto input)` -> `System.Threading.Tasks.Task`
- `PrepareCollectedData()` -> `System.Threading.Tasks.Task`
- `UpdateCurrentUserProfile(CurrentUserProfileEditDto input)` -> `System.Threading.Tasks.Task`
- `ChangePassword(ChangePasswordInput input)` -> `System.Threading.Tasks.Task`
- `UpdateProfilePicture(UpdateProfilePictureInput input)` -> `System.Threading.Tasks.Task`
- `GetPasswordComplexitySetting()` -> `System.Threading.Tasks.Task<GetPasswordComplexitySettingOutput>`
- `GetProfilePicture()` -> `System.Threading.Tasks.Task<GetProfilePictureOutput>`
- `GetFriendProfilePictureById(GetFriendProfilePictureByIdInput input)` -> `System.Threading.Tasks.Task<GetProfilePictureOutput>`
- `GetProfilePictureById(Guid profilePictureId)` -> `System.Threading.Tasks.Task<GetProfilePictureOutput>`
- `ChangeLanguage(ChangeUserLanguageDto input)` -> `System.Threading.Tasks.Task`

### CachingAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.Caching/CachingAppService.cs`
- `GetAllCaches()` -> `ListResultDto<CacheDto>`
- `ClearCache(EntityDto<string> input)` -> `System.Threading.Tasks.Task`
- `ClearAllCaches()` -> `System.Threading.Tasks.Task`

### ChatAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.Chat/ChatAppService.cs`
- `GetUserChatFriendsWithSettings()` -> `GetUserChatFriendsWithSettingsOutput`
- `GetUserChatMessages(GetUserChatMessagesInput input)` -> `System.Threading.Tasks.Task<ListResultDto<ChatMessageDto>>`
- `MarkAllUnreadMessagesOfUserAsRead(MarkAllUnreadMessagesOfUserAsReadInput input)` -> `System.Threading.Tasks.Task`

### CommonLookupAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.Common/CommonLookupAppService.cs`
- `GetEditionsForCombobox(bool onlyFreeItems = false)` -> `System.Threading.Tasks.Task<ListResultDto<SubscribableEditionComboboxItemDto>>`
- `FindUsers(FindUsersInput input)` -> `System.Threading.Tasks.Task<PagedResultDto<NameValueDto>>`
- `GetDefaultEditionName()` -> `GetDefaultEditionNameOutput`

### UiCustomizationSettingsAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.Configuration/UiCustomizationSettingsAppService.cs`
- `GetUiManagementSettings()` -> `System.Threading.Tasks.Task<List<ThemeSettingsDto>>`
- `ChangeThemeWithDefaultValues(string themeName)` -> `System.Threading.Tasks.Task`
- `UpdateUiManagementSettings(ThemeSettingsDto settings)` -> `System.Threading.Tasks.Task`
- `UpdateDefaultUiManagementSettings(ThemeSettingsDto settings)` -> `System.Threading.Tasks.Task`
- `UseSystemDefaultSettings()` -> `System.Threading.Tasks.Task`

### HostSettingsAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.Configuration.Host/HostSettingsAppService.cs`
- `GetAllSettings()` -> `System.Threading.Tasks.Task<HostSettingsEditDto>`
- `UpdateAllSettings(HostSettingsEditDto input)` -> `System.Threading.Tasks.Task`
- `GetAbilitySettings()` -> `System.Threading.Tasks.Task<AbilitySettingsEditDto>`
- `UpdateAbilitySettings(AbilitySettingsEditDto input)` -> `System.Threading.Tasks.Task`

### TenantSettingsAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.Configuration.Tenants/TenantSettingsAppService.cs`
- `GetAllSettings()` -> `System.Threading.Tasks.Task<TenantSettingsEditDto>`
- `UpdateAllSettings(TenantSettingsEditDto input)` -> `System.Threading.Tasks.Task`
- `ClearLogo()` -> `System.Threading.Tasks.Task`
- `ClearCustomCss()` -> `System.Threading.Tasks.Task`

### DashboardAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.Dashboard/DashboardAppService.cs`
- `Statistics()` -> `System.Threading.Tasks.Task<StatisticsOutput>`
- `OrgCount()` -> `System.Threading.Tasks.Task<ListResultDto<OrgCountOutput>>`
- `ChangeCountInWeek()` -> `System.Threading.Tasks.Task<ListResultDto<NameValueDto>>`

### DashboardCustomizationAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.DashboardCustomization/DashboardCustomizationAppService.cs`
- `GetUserDashboard(GetDashboardInput input)` -> `System.Threading.Tasks.Task<Dashboard>`
- `SavePage(SavePageInput input)` -> `System.Threading.Tasks.Task`
- `RenamePage(RenamePageInput input)` -> `System.Threading.Tasks.Task`
- `AddNewPage(AddNewPageInput input)` -> `System.Threading.Tasks.Task<AddNewPageOutput>`
- `DeletePage(DeletePageInput input)` -> `System.Threading.Tasks.Task`
- `AddWidget(AddWidgetInput input)` -> `System.Threading.Tasks.Task<Widget>`
- `GetDashboardDefinition(GetDashboardInput input)` -> `DashboardOutput`
- `GetAllWidgetDefinitions(GetDashboardInput input)` -> `List<WidgetOutput>`
- `GetSettingName(string application)` -> `string`

### DemoUiComponentsAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.DemoUiComponents/DemoUiComponentsAppService.cs`
- `SendAndGetDate(System.DateTime? date)` -> `DateToStringOutput`
- `SendAndGetDateTime(System.DateTime? date)` -> `DateToStringOutput`
- `SendAndGetDateRange(System.DateTime? startDate, System.DateTime? endDate)` -> `DateToStringOutput`
- `GetCountries(string searchTerm)` -> `List<NameValue<string>>`
- `SendAndGetSelectedCountries(List<NameValue<string>> selectedCountries)` -> `List<NameValue<string>>`
- `SendAndGetValue(string input)` -> `StringOutput`

### DynamicEntityParameterDefinitionAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.DynamicEntityParameters/DynamicEntityParameterDefinitionAppService.cs`
- `GetAllAllowedInputTypeNames()` -> `List<string>`
- `GetAllEntities()` -> `List<string>`

### DynamicParameterAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.DynamicEntityParameters/DynamicParameterAppService.cs`
- `Get(int id)` -> `System.Threading.Tasks.Task<DynamicParameterDto>`
- `GetAll()` -> `System.Threading.Tasks.Task<ListResultDto<DynamicParameterDto>>`
- `Add(DynamicParameterDto dto)` -> `System.Threading.Tasks.Task`；属性：`Authorize("Pages.Administration.DynamicParameterValue.Create")`
- `Update(DynamicParameterDto dto)` -> `System.Threading.Tasks.Task`；属性：`Authorize("Pages.Administration.DynamicParameterValue.Edit")`
- `Delete(int id)` -> `System.Threading.Tasks.Task`；属性：`Authorize("Pages.Administration.DynamicParameterValue.Delete")`
- `FindAllowedInputType(string name)` -> `IInputType`

### DynamicParameterValueAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.DynamicEntityParameters/DynamicParameterValueAppService.cs`
- `Get(int id)` -> `System.Threading.Tasks.Task<DynamicParameterValueDto>`
- `GetAllValuesOfDynamicParameter(EntityDto input)` -> `System.Threading.Tasks.Task<ListResultDto<DynamicParameterValueDto>>`
- `Add(DynamicParameterValueDto dto)` -> `System.Threading.Tasks.Task`；属性：`Authorize("Pages.Administration.DynamicParameterValue.Create")`
- `Update(DynamicParameterValueDto dto)` -> `System.Threading.Tasks.Task`；属性：`Authorize("Pages.Administration.DynamicParameterValue.Edit")`
- `Delete(int id)` -> `System.Threading.Tasks.Task`；属性：`Authorize("Pages.Administration.DynamicParameterValue.Delete")`

### EntityDynamicParameterAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.DynamicEntityParameters/EntityDynamicParameterAppService.cs`
- `Get(int id)` -> `System.Threading.Tasks.Task<EntityDynamicParameterDto>`
- `GetAllParametersOfAnEntity(EntityDynamicParameterGetAllInput input)` -> `System.Threading.Tasks.Task<ListResultDto<EntityDynamicParameterDto>>`
- `GetAll()` -> `System.Threading.Tasks.Task<ListResultDto<EntityDynamicParameterDto>>`
- `Add(EntityDynamicParameterDto dto)` -> `System.Threading.Tasks.Task`；属性：`Authorize("Pages.Administration.EntityDynamicParameters.Create")`
- `Update(EntityDynamicParameterDto dto)` -> `System.Threading.Tasks.Task`；属性：`Authorize("Pages.Administration.EntityDynamicParameters.Edit")`
- `Delete(int id)` -> `System.Threading.Tasks.Task`；属性：`Authorize("Pages.Administration.EntityDynamicParameters.Delete")`

### EntityDynamicParameterValueAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.DynamicEntityParameters/EntityDynamicParameterValueAppService.cs`
- `Get(int id)` -> `System.Threading.Tasks.Task<EntityDynamicParameterValueDto>`
- `GetAll(GetAllInput input)` -> `System.Threading.Tasks.Task<ListResultDto<EntityDynamicParameterValueDto>>`
- `Add(EntityDynamicParameterValueDto input)` -> `System.Threading.Tasks.Task`；属性：`Authorize("Pages.Administration.EntityDynamicParameterValue.Create")`
- `Update(EntityDynamicParameterValueDto input)` -> `System.Threading.Tasks.Task`；属性：`Authorize("Pages.Administration.EntityDynamicParameterValue.Edit")`
- `Delete(int id)` -> `System.Threading.Tasks.Task`；属性：`Authorize("Pages.Administration.EntityDynamicParameterValue.Delete")`
- `GetAllEntityDynamicParameterValues(GetAllEntityDynamicParameterValuesInput input)` -> `System.Threading.Tasks.Task<GetAllEntityDynamicParameterValuesOutput>`
- `InsertOrUpdateAllValues(InsertOrUpdateAllValuesInput input)` -> `System.Threading.Tasks.Task`；属性：`Authorize("Pages.Administration.EntityDynamicParameterValue.Create"); Authorize("Pages.Administration.EntityDynamicParameterValue.Edit")`
- `CleanValues(CleanValuesInput input)` -> `System.Threading.Tasks.Task`；属性：`Authorize("Pages.Administration.EntityDynamicParameterValue.Delete")`

### EditionAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.Editions/EditionAppService.cs`
- `GetEditions()` -> `System.Threading.Tasks.Task<ListResultDto<EditionListDto>>`；属性：`AbpAuthorize(new string[] { "Pages.Editions" })`
- `GetEditionForEdit(NullableIdDto input)` -> `System.Threading.Tasks.Task<GetEditionEditOutput>`；属性：`AbpAuthorize(new string[] { "Pages.Editions.Create", "Pages.Editions.Edit" })`
- `CreateEdition(CreateEditionDto input)` -> `System.Threading.Tasks.Task`；属性：`AbpAuthorize(new string[] { "Pages.Editions.Create" })`
- `UpdateEdition(UpdateEditionDto input)` -> `System.Threading.Tasks.Task`；属性：`AbpAuthorize(new string[] { "Pages.Editions.Edit" })`
- `DeleteEdition(EntityDto input)` -> `System.Threading.Tasks.Task`；属性：`AbpAuthorize(new string[] { "Pages.Editions.Delete" })`
- `MoveTenantsToAnotherEdition(MoveTenantsToAnotherEditionDto input)` -> `System.Threading.Tasks.Task`；属性：`AbpAuthorize(new string[] { "Pages.Editions.MoveTenantsToAnotherEdition" })`
- `GetEditionComboboxItems(int? selectedEditionId = null, bool addAllItem = false, bool onlyFreeItems = false)` -> `System.Threading.Tasks.Task<List<SubscribableEditionComboboxItemDto>>`；属性：`AbpAuthorize(new string[] { "Pages.Editions", "Pages.Tenants" })`
- `GetTenantCount(int editionId)` -> `System.Threading.Tasks.Task<int>`
- `CreateEditionAsync(CreateEditionDto input)` -> `virtual System.Threading.Tasks.Task`；属性：`AbpAuthorize(new string[] { "Pages.Editions.Create" })`
- `UpdateEditionAsync(UpdateEditionDto input)` -> `virtual System.Threading.Tasks.Task`；属性：`AbpAuthorize(new string[] { "Pages.Editions.Edit" })`

### MyFavoriteAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.Favorite/MyFavoriteAppService.cs`
- `AddItem(AddMyFavoriteItemInput input)` -> `System.Threading.Tasks.Task`
- `DeleteMyFavorite(EntityDto<Guid> input)` -> `System.Threading.Tasks.Task`
- `GetMyFavoriteAbilityList(GetMyFavoriteAbilityListInput input)` -> `System.Threading.Tasks.Task<ListResultDto<AbilityListDto>>`
- `GetMyFavoriteForEdit(EntityDto<Guid?> input)` -> `System.Threading.Tasks.Task<MyFavoriteDto>`
- `GetMyFavoriteList()` -> `System.Threading.Tasks.Task<ListResultDto<MyFavoriteDto>>`
- `RemoveItem(EntityDto<Guid> input)` -> `System.Threading.Tasks.Task`
- `SaveOrUpdateMyFavorite(MyFavoriteDto input)` -> `System.Threading.Tasks.Task`

### FriendshipAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.Friendships/FriendshipAppService.cs`
- `CreateFriendshipRequest(CreateFriendshipRequestInput input)` -> `System.Threading.Tasks.Task<FriendDto>`
- `CreateFriendshipRequestByUserName(CreateFriendshipRequestByUserNameInput input)` -> `System.Threading.Tasks.Task<FriendDto>`
- `BlockUser(BlockUserInput input)` -> `System.Threading.Tasks.Task`
- `UnblockUser(UnblockUserInput input)` -> `System.Threading.Tasks.Task`
- `AcceptFriendshipRequest(AcceptFriendshipRequestInput input)` -> `System.Threading.Tasks.Task`

### InstallAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.Install/InstallAppService.cs`
- `Setup(InstallDto input)` -> `System.Threading.Tasks.Task`
- `GetAppSettingsJson()` -> `AppSettingsJsonDto`
- `CheckDatabase()` -> `CheckDatabaseOutput`

### LaboratoryAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.Laboratories/LaboratoryAppService.cs`
- `List()` -> `System.Threading.Tasks.Task<LaboratoryListDto>`；属性：`AbpAuthorize(new string[] { })`
- `CreateOrUpdate(LaboratoryDto input)` -> `System.Threading.Tasks.Task<Guid>`
- `Update(LaboratoryDto input)` -> `System.Threading.Tasks.Task`
- `Create(LaboratoryDto input)` -> `System.Threading.Tasks.Task<Guid>`
- `GetLabForEdit(NullableIdDto<Guid> input)` -> `System.Threading.Tasks.Task<LaboratoryDto>`
- `DeleteLab(EntityDto<Guid> input)` -> `System.Threading.Tasks.Task`

### LanguageAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.Localization/LanguageAppService.cs`
- `GetLanguages()` -> `System.Threading.Tasks.Task<GetLanguagesOutput>`
- `GetLanguageForEdit(NullableIdDto input)` -> `System.Threading.Tasks.Task<GetLanguageForEditOutput>`；属性：`AbpAuthorize(new string[] { "Pages.Administration.Languages.Create", "Pages.Administration.Languages.Edit" })`
- `CreateOrUpdateLanguage(CreateOrUpdateLanguageInput input)` -> `System.Threading.Tasks.Task`
- `DeleteLanguage(EntityDto input)` -> `System.Threading.Tasks.Task`
- `SetDefaultLanguage(SetDefaultLanguageInput input)` -> `System.Threading.Tasks.Task`
- `GetLanguageTexts(GetLanguageTextsInput input)` -> `System.Threading.Tasks.Task<PagedResultDto<LanguageTextListDto>>`；属性：`AbpAuthorize(new string[] { "Pages.Administration.Languages.ChangeTexts" })`
- `UpdateLanguageText(UpdateLanguageTextInput input)` -> `System.Threading.Tasks.Task`
- `CreateLanguageAsync(CreateOrUpdateLanguageInput input)` -> `virtual System.Threading.Tasks.Task`；属性：`AbpAuthorize(new string[] { "Pages.Administration.Languages.Create" })`
- `UpdateLanguageAsync(CreateOrUpdateLanguageInput input)` -> `virtual System.Threading.Tasks.Task`；属性：`AbpAuthorize(new string[] { "Pages.Administration.Languages.Edit" })`

### WebLogAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.Logging/WebLogAppService.cs`
- `GetLatestWebLogs()` -> `GetLatestWebLogsOutput`
- `DownloadWebLogs()` -> `FileDto`

### SubscriptionAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.MultiTenancy/SubscriptionAppService.cs`
- `DisableRecurringPayments()` -> `System.Threading.Tasks.Task`
- `EnableRecurringPayments()` -> `System.Threading.Tasks.Task`

### TenantAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.MultiTenancy/TenantAppService.cs`
- `GetTenants(GetTenantsInput input)` -> `System.Threading.Tasks.Task<PagedResultDto<TenantListDto>>`
- `CreateTenant(CreateTenantInput input)` -> `System.Threading.Tasks.Task`；属性：`AbpAuthorize(new string[] { "Pages.Tenants.Create" })`
- `GetTenantForEdit(EntityDto input)` -> `System.Threading.Tasks.Task<TenantEditDto>`；属性：`AbpAuthorize(new string[] { "Pages.Tenants.Edit" })`
- `UpdateTenant(TenantEditDto input)` -> `System.Threading.Tasks.Task`；属性：`AbpAuthorize(new string[] { "Pages.Tenants.Edit" })`
- `DeleteTenant(EntityDto input)` -> `System.Threading.Tasks.Task`；属性：`AbpAuthorize(new string[] { "Pages.Tenants.Delete" })`
- `GetTenantFeaturesForEdit(EntityDto input)` -> `System.Threading.Tasks.Task<GetTenantFeaturesEditOutput>`；属性：`AbpAuthorize(new string[] { "Pages.Tenants.ChangeFeatures" })`
- `UpdateTenantFeatures(UpdateTenantFeaturesInput input)` -> `System.Threading.Tasks.Task`；属性：`AbpAuthorize(new string[] { "Pages.Tenants.ChangeFeatures" })`
- `ResetTenantSpecificFeatures(EntityDto input)` -> `System.Threading.Tasks.Task`；属性：`AbpAuthorize(new string[] { "Pages.Tenants.ChangeFeatures" })`
- `UnlockTenantAdmin(EntityDto input)` -> `System.Threading.Tasks.Task`

### TenantRegistrationAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.MultiTenancy/TenantRegistrationAppService.cs`
- `RegisterTenant(RegisterTenantInput input)` -> `System.Threading.Tasks.Task<RegisterTenantOutput>`
- `GetEditionsForSelect()` -> `System.Threading.Tasks.Task<EditionsSelectOutput>`
- `GetEdition(int editionId)` -> `System.Threading.Tasks.Task<EditionSelectDto>`

### InvoiceAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.MultiTenancy.Accounting/InvoiceAppService.cs`
- `GetInvoiceInfo(EntityDto<long> input)` -> `System.Threading.Tasks.Task<InvoiceDto>`
- `CreateInvoice(CreateInvoiceDto input)` -> `System.Threading.Tasks.Task`

### HostDashboardAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.MultiTenancy.HostDashboard/HostDashboardAppService.cs`
- `GetTopStatsData(GetTopStatsInput input)` -> `System.Threading.Tasks.Task<TopStatsData>`
- `GetRecentTenantsData()` -> `System.Threading.Tasks.Task<GetRecentTenantsOutput>`
- `GetSubscriptionExpiringTenantsData()` -> `System.Threading.Tasks.Task<GetExpiringTenantsOutput>`
- `GetIncomeStatistics(GetIncomeStatisticsDataInput input)` -> `System.Threading.Tasks.Task<GetIncomeStatisticsDataOutput>`
- `GetEditionTenantStatistics(GetEditionTenantStatisticsInput input)` -> `System.Threading.Tasks.Task<GetEditionTenantStatisticsOutput>`

### PayPalPaymentAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.MultiTenancy.Payments/PayPalPaymentAppService.cs`
- `ConfirmPayment(long paymentId, string paypalOrderId)` -> `System.Threading.Tasks.Task`
- `GetConfiguration()` -> `PayPalConfigurationDto`

### PaymentAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.MultiTenancy.Payments/PaymentAppService.cs`
- `GetPaymentInfo(PaymentInfoInput input)` -> `System.Threading.Tasks.Task<PaymentInfoDto>`；属性：`Authorize("Pages.Administration.Tenant.SubscriptionManagement")`
- `CreatePayment(CreatePaymentDto input)` -> `System.Threading.Tasks.Task<long>`
- `CancelPayment(CancelPaymentDto input)` -> `System.Threading.Tasks.Task`
- `GetPaymentHistory(GetPaymentHistoryInput input)` -> `System.Threading.Tasks.Task<PagedResultDto<SubscriptionPaymentListDto>>`
- `GetActiveGateways(GetActiveGatewaysInput input)` -> `List<PaymentGatewayModel>`
- `GetPaymentAsync(long paymentId)` -> `System.Threading.Tasks.Task<SubscriptionPaymentDto>`
- `GetLastCompletedPayment()` -> `System.Threading.Tasks.Task<SubscriptionPaymentDto>`
- `BuyNowSucceed(long paymentId)` -> `System.Threading.Tasks.Task`
- `NewRegistrationSucceed(long paymentId)` -> `System.Threading.Tasks.Task`
- `UpgradeSucceed(long paymentId)` -> `System.Threading.Tasks.Task`
- `ExtendSucceed(long paymentId)` -> `System.Threading.Tasks.Task`
- `PaymentFailed(long paymentId)` -> `System.Threading.Tasks.Task`
- `SwitchBetweenFreeEditions(int upgradeEditionId)` -> `System.Threading.Tasks.Task`
- `UpgradeSubscriptionCostsLessThenMinAmount(int editionId)` -> `System.Threading.Tasks.Task`
- `HasAnyPayment()` -> `System.Threading.Tasks.Task<bool>`；属性：`Authorize("Pages.Administration.Tenant.SubscriptionManagement")`

### StripePaymentAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.MultiTenancy.Payments/StripePaymentAppService.cs`
- `ConfirmPayment(StripeConfirmPaymentInput input)` -> `System.Threading.Tasks.Task`
- `GetConfiguration()` -> `StripeConfigurationDto`
- `GetPaymentAsync(StripeGetPaymentInput input)` -> `System.Threading.Tasks.Task<SubscriptionPaymentDto>`
- `CreatePaymentSession(StripeCreatePaymentSessionInput input)` -> `System.Threading.Tasks.Task<string>`
- `GetPaymentResult(StripePaymentResultInput input)` -> `System.Threading.Tasks.Task<StripePaymentResultOutput>`

### NotificationAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.Notifications/NotificationAppService.cs`
- `GetUserNotifications(GetUserNotificationsInput input)` -> `System.Threading.Tasks.Task<GetNotificationsOutput>`
- `SetAllNotificationsAsRead()` -> `System.Threading.Tasks.Task`
- `SetNotificationAsRead(EntityDto<Guid> input)` -> `System.Threading.Tasks.Task`
- `GetNotificationSettings()` -> `System.Threading.Tasks.Task<GetNotificationSettingsOutput>`
- `UpdateNotificationSettings(UpdateNotificationSettingsInput input)` -> `System.Threading.Tasks.Task`
- `DeleteNotification(EntityDto<Guid> input)` -> `System.Threading.Tasks.Task`
- `DeleteAllUserNotifications(DeleteAllUserNotificationsInput input)` -> `System.Threading.Tasks.Task`

### OrganizationUnitAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.Organizations/OrganizationUnitAppService.cs`
- `GetOrganizationUnits()` -> `System.Threading.Tasks.Task<ListResultDto<OrganizationUnitDto>>`
- `GetOrganizationUnitUsers(GetOrganizationUnitUsersInput input)` -> `System.Threading.Tasks.Task<PagedResultDto<OrganizationUnitUserListDto>>`
- `GetOrganizationUnitRoles(GetOrganizationUnitRolesInput input)` -> `System.Threading.Tasks.Task<PagedResultDto<OrganizationUnitRoleListDto>>`
- `CreateOrganizationUnit(CreateOrganizationUnitInput input)` -> `System.Threading.Tasks.Task<OrganizationUnitDto>`；属性：`AbpAuthorize(new string[] { })`
- `UpdateOrganizationUnit(UpdateOrganizationUnitInput input)` -> `System.Threading.Tasks.Task<OrganizationUnitDto>`；属性：`AbpAuthorize(new string[] { })`
- `MoveOrganizationUnit(MoveOrganizationUnitInput input)` -> `System.Threading.Tasks.Task<OrganizationUnitDto>`；属性：`AbpAuthorize(new string[] { })`
- `DeleteOrganizationUnit(EntityDto<long> input)` -> `System.Threading.Tasks.Task`；属性：`AbpAuthorize(new string[] { })`
- `RemoveUserFromOrganizationUnit(UserToOrganizationUnitInput input)` -> `System.Threading.Tasks.Task`；属性：`AbpAuthorize(new string[] { })`
- `RemoveRoleFromOrganizationUnit(RoleToOrganizationUnitInput input)` -> `System.Threading.Tasks.Task`；属性：`AbpAuthorize(new string[] { })`
- `AddUsersToOrganizationUnit(UsersToOrganizationUnitInput input)` -> `System.Threading.Tasks.Task`；属性：`AbpAuthorize(new string[] { })`
- `AddRolesToOrganizationUnit(RolesToOrganizationUnitInput input)` -> `System.Threading.Tasks.Task`；属性：`AbpAuthorize(new string[] { })`
- `FindUsers(FindOrganizationUnitUsersInput input)` -> `System.Threading.Tasks.Task<PagedResultDto<NameValueDto>>`；属性：`AbpAuthorize(new string[] { })`
- `FindRoles(FindOrganizationUnitRolesInput input)` -> `System.Threading.Tasks.Task<PagedResultDto<NameValueDto>>`；属性：`AbpAuthorize(new string[] { })`

### SampleAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.Samples/SampleAppService.cs`
- `GetList(GetSampleListInputDto input)` -> `System.Threading.Tasks.Task<SampleListDto>`
- `CreateOrUpdate(SampleDto input)` -> `System.Threading.Tasks.Task<Guid>`
- `GetForEdit(NullableIdDto<Guid> input)` -> `System.Threading.Tasks.Task<SampleDto>`
- `DeleteSample(EntityDto<Guid> input)` -> `System.Threading.Tasks.Task`
- `Update(SampleDto input)` -> `System.Threading.Tasks.Task`
- `Create(SampleDto input)` -> `System.Threading.Tasks.Task<Guid>`

### SampleTypeAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.Samples/SampleTypeAppService.cs`
- `GetList()` -> `System.Threading.Tasks.Task<SampleTypeListDto>`；属性：`AbpAuthorize(new string[] { })`
- `CreateOrUpdate(SampleTypeDto input)` -> `System.Threading.Tasks.Task<Guid>`
- `GetForEdit(NullableIdDto<Guid> input)` -> `System.Threading.Tasks.Task<GetSampleTypeForEditOutput>`
- `DeleteSampleType(EntityDto<Guid> input)` -> `System.Threading.Tasks.Task`
- `Update(SampleTypeDto input)` -> `System.Threading.Tasks.Task`
- `Create(SampleTypeDto input)` -> `System.Threading.Tasks.Task<Guid>`
- `GetListByOrg(GetListByOrgInput input)` -> `System.Threading.Tasks.Task<ListResultDto<SampleTypeDto>>`

### SessionAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.Sessions/SessionAppService.cs`
- `GetCurrentLoginInformations()` -> `System.Threading.Tasks.Task<GetCurrentLoginInformationsOutput>`
- `UpdateUserSignInToken()` -> `System.Threading.Tasks.Task<UpdateUserSignInTokenOutput>`

### TenantDashboardAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.Tenants.Dashboard/TenantDashboardAppService.cs`
- `GetMemberActivity()` -> `GetMemberActivityOutput`
- `GetDashboardData(GetDashboardDataInput input)` -> `GetDashboardDataOutput`
- `GetTopStats()` -> `GetTopStatsOutput`
- `GetProfitShare()` -> `GetProfitShareOutput`
- `GetDailySales()` -> `GetDailySalesOutput`
- `GetSalesSummary(GetSalesSummaryInput input)` -> `GetSalesSummaryOutput`
- `GetRegionalStats()` -> `GetRegionalStatsOutput`
- `GetGeneralStats()` -> `GetGeneralStatsOutput`

### TimingAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.Timing/TimingAppService.cs`
- `GetTimezones(GetTimezonesInput input)` -> `System.Threading.Tasks.Task<ListResultDto<NameValueDto>>`
- `GetTimezoneComboboxItems(GetTimezoneComboboxItemsInput input)` -> `System.Threading.Tasks.Task<List<ComboboxItemDto>>`

### WebhookEventAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.WebHooks/WebhookEventAppService.cs`
- `Get(string id)` -> `System.Threading.Tasks.Task<WebhookEvent>`

### WebhookSendAttemptAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.WebHooks/WebhookSendAttemptAppService.cs`
- `GetAllSendAttempts(GetAllSendAttemptsInput input)` -> `System.Threading.Tasks.Task<PagedResultDto<GetAllSendAttemptsOutput>>`
- `GetAllSendAttemptsOfWebhookEvent(GetAllSendAttemptsOfWebhookEventInput input)` -> `System.Threading.Tasks.Task<ListResultDto<GetAllSendAttemptsOfWebhookEventOutput>>`
- `Resend(string sendAttemptId)` -> `System.Threading.Tasks.Task`；属性：`AbpAuthorize(new string[] { "Pages.Administration.Webhook.ResendWebhook" })`

### WebhookSubscriptionAppService
文件：`.decompiled/SgsMineral.CapabilityTable.Application/SgsMineral.CapabilityTable.WebHooks/WebhookSubscriptionAppService.cs`
- `PublishTestWebhook()` -> `System.Threading.Tasks.Task<string>`
- `GetAllSubscriptions()` -> `System.Threading.Tasks.Task<ListResultDto<GetAllSubscriptionsOutput>>`
- `GetSubscription(string subscriptionId)` -> `System.Threading.Tasks.Task<WebhookSubscription>`；属性：`AbpAuthorize(new string[] { "Pages.Administration.WebhookSubscription.Create", "Pages.Administration.WebhookSubscription.Edit", "Pages.Administration.WebhookSubscription.Detail" })`
- `AddSubscription(WebhookSubscription subscription)` -> `System.Threading.Tasks.Task`；属性：`AbpAuthorize(new string[] { "Pages.Administration.WebhookSubscription.Create" })`
- `UpdateSubscription(WebhookSubscription subscription)` -> `System.Threading.Tasks.Task`；属性：`AbpAuthorize(new string[] { "Pages.Administration.WebhookSubscription.Edit" })`
- `ActivateWebhookSubscription(ActivateWebhookSubscriptionInput input)` -> `System.Threading.Tasks.Task`；属性：`AbpAuthorize(new string[] { "Pages.Administration.WebhookSubscription.ChangeActivity" })`
- `IsSubscribed(string webhookName)` -> `System.Threading.Tasks.Task<bool>`
- `GetAllSubscriptionsIfFeaturesGranted(string webhookName)` -> `System.Threading.Tasks.Task<ListResultDto<GetAllSubscriptionsOutput>>`
- `GetAllAvailableWebhooks()` -> `System.Threading.Tasks.Task<ListResultDto<GetAllAvailableWebhooksOutput>>`

## Controller 方法

### AbilityTableController
文件：`.decompiled/SgsMineral.CapabilityTable.Web.Core/SgsMineral.CapabilityTable.Web.Controllers/AbilityTableController.cs`
- `UploadNewStandard()` -> `System.Threading.Tasks.Task<UploadNewStandardDto>`
- `UploadSubcontractAbility()` -> `System.Threading.Tasks.Task<UploadSubcontractAbilityDto>`
- `UploadAbilityTable(long orgId)` -> `System.Threading.Tasks.Task<AbilityTableOutput>`

### CapabilityTableControllerBase
文件：`.decompiled/SgsMineral.CapabilityTable.Web.Core/SgsMineral.CapabilityTable.Web.Controllers/CapabilityTableControllerBase.cs`
- `CheckErrors(IdentityResult identityResult)` -> `void`
- `SetTenantIdCookie(int? tenantId)` -> `void`

### ChatControllerBase
文件：`.decompiled/SgsMineral.CapabilityTable.Web.Core/SgsMineral.CapabilityTable.Web.Controllers/ChatControllerBase.cs`
- `UploadFile()` -> `System.Threading.Tasks.Task<JsonResult>`；属性：`HttpPost; AbpMvcAuthorize(new string[] { })`

### DemoUiComponentsController
文件：`.decompiled/SgsMineral.CapabilityTable.Web.Core/SgsMineral.CapabilityTable.Web.Controllers/DemoUiComponentsController.cs`
- `UploadFiles()` -> `System.Threading.Tasks.Task<JsonResult>`；属性：`HttpPost`

### ErrorController
文件：`.decompiled/SgsMineral.CapabilityTable.Web.Core/SgsMineral.CapabilityTable.Web.Controllers/ErrorController.cs`
- `Index(int statusCode = 0)` -> `ActionResult`
- `E403()` -> `ActionResult`
- `E404()` -> `ActionResult`

### FileController
文件：`.decompiled/SgsMineral.CapabilityTable.Web.Core/SgsMineral.CapabilityTable.Web.Controllers/FileController.cs`
- `DownloadTempFile(FileDto file)` -> `ActionResult`
- `DownloadBinaryFile(Guid id, string contentType, string fileName)` -> `System.Threading.Tasks.Task<ActionResult>`

### ProfileControllerBase
文件：`.decompiled/SgsMineral.CapabilityTable.Web.Core/SgsMineral.CapabilityTable.Web.Controllers/ProfileControllerBase.cs`
- `UploadProfilePicture(FileDto input)` -> `UploadProfilePictureOutput`
- `GetDefaultProfilePicture()` -> `FileResult`
- `GetDefaultProfilePictureInternal()` -> `FileResult`

### StripeControllerBase
文件：`.decompiled/SgsMineral.CapabilityTable.Web.Core/SgsMineral.CapabilityTable.Web.Controllers/StripeControllerBase.cs`
- `WebHooks()` -> `System.Threading.Tasks.Task<IActionResult>`；属性：`HttpPost`

### TenantCustomizationController
文件：`.decompiled/SgsMineral.CapabilityTable.Web.Core/SgsMineral.CapabilityTable.Web.Controllers/TenantCustomizationController.cs`
- `UploadLogo()` -> `System.Threading.Tasks.Task<JsonResult>`；属性：`HttpPost; AbpMvcAuthorize(new string[] { "Pages.Administration.Tenant.Settings" })`
- `UploadCustomCss()` -> `System.Threading.Tasks.Task<JsonResult>`；属性：`HttpPost; AbpMvcAuthorize(new string[] { "Pages.Administration.Tenant.Settings" })`
- `GetLogo(int? tenantId)` -> `System.Threading.Tasks.Task<ActionResult>`
- `GetTenantLogo(string skin, int? tenantId)` -> `System.Threading.Tasks.Task<ActionResult>`
- `GetCustomCss(int? tenantId)` -> `System.Threading.Tasks.Task<ActionResult>`

### TokenAuthController
文件：`.decompiled/SgsMineral.CapabilityTable.Web.Core/SgsMineral.CapabilityTable.Web.Controllers/TokenAuthController.cs`
- `Authenticate([FromBody] AuthenticateModel model)` -> `System.Threading.Tasks.Task<AuthenticateResultModel>`；属性：`HttpPost`
- `RefreshToken(string refreshToken)` -> `System.Threading.Tasks.Task<RefreshTokenResult>`；属性：`HttpPost`
- `LogOut()` -> `System.Threading.Tasks.Task`；属性：`HttpGet; AbpAuthorize(new string[] { })`
- `SendTwoFactorAuthCode([FromBody] SendTwoFactorAuthCodeModel model)` -> `System.Threading.Tasks.Task`；属性：`HttpPost`
- `ImpersonatedAuthenticate(string impersonationToken)` -> `System.Threading.Tasks.Task<ImpersonatedAuthenticateResultModel>`；属性：`HttpPost`
- `DelegatedImpersonatedAuthenticate(long userDelegationId, string impersonationToken)` -> `System.Threading.Tasks.Task<ImpersonatedAuthenticateResultModel>`；属性：`HttpPost`
- `LinkedAccountAuthenticate(string switchAccountToken)` -> `System.Threading.Tasks.Task<SwitchedAccountAuthenticateResultModel>`；属性：`HttpPost`
- `GetExternalAuthenticationProviders()` -> `List<ExternalLoginProviderInfoModel>`；属性：`HttpGet`
- `ExternalAuthenticate([FromBody] ExternalAuthenticateModel model)` -> `System.Threading.Tasks.Task<ExternalAuthenticateResultModel>`；属性：`HttpPost`
- `TestNotification(string message = "", string severity = "info")` -> `System.Threading.Tasks.Task<ActionResult>`；属性：`AbpMvcAuthorize(new string[] { }); HttpGet`

### UsersControllerBase
文件：`.decompiled/SgsMineral.CapabilityTable.Web.Core/SgsMineral.CapabilityTable.Web.Controllers/UsersControllerBase.cs`
- `ImportFromExcel()` -> `System.Threading.Tasks.Task<JsonResult>`；属性：`HttpPost; AbpMvcAuthorize(new string[] { "Pages.Administration.Users.Create" })`

### AntiForgeryController
文件：`.decompiled/SgsMineral.CapabilityTable.Web.Host/SgsMineral.CapabilityTable.Web.Controllers/AntiForgeryController.cs`
- `GetToken()` -> `void`

### ChatController
文件：`.decompiled/SgsMineral.CapabilityTable.Web.Host/SgsMineral.CapabilityTable.Web.Controllers/ChatController.cs`
- `GetUploadedObject(Guid fileId, string fileName, string contentType)` -> `System.Threading.Tasks.Task<ActionResult>`

### HomeController
文件：`.decompiled/SgsMineral.CapabilityTable.Web.Host/SgsMineral.CapabilityTable.Web.Controllers/HomeController.cs`
- `Index()` -> `IActionResult`

### UiController
文件：`.decompiled/SgsMineral.CapabilityTable.Web.Host/SgsMineral.CapabilityTable.Web.Controllers/UiController.cs`
- `Index()` -> `System.Threading.Tasks.Task<IActionResult>`
- `Login(string returnUrl = "")` -> `IActionResult`；属性：`HttpGet`
- `Login(LoginModel model, string returnUrl = "")` -> `System.Threading.Tasks.Task<IActionResult>`；属性：`HttpPost`
- `Logout()` -> `System.Threading.Tasks.Task<ActionResult>`

### ConsentController
文件：`.decompiled/SgsMineral.CapabilityTable.Web.Host/SgsMineral.CapabilityTable.Web.Host.Controllers/ConsentController.cs`
- `Index(string returnUrl)` -> `System.Threading.Tasks.Task<IActionResult>`
- `Index(ConsentInputModel model)` -> `System.Threading.Tasks.Task<IActionResult>`；属性：`HttpPost`
- `ProcessConsent(ConsentInputModel model)` -> `System.Threading.Tasks.Task<ProcessConsentResult>`
- `CreateScopeViewModel(IdentityResource identity, bool check)` -> `ScopeViewModel`
- `CreateScopeViewModel(Scope scope, bool check)` -> `ScopeViewModel`

## 前端调用到的 `/api/services/app/*`

- `/api/services/app/Ability/CreateAbility`
- `/api/services/app/Ability/DeleteAbility`
- `/api/services/app/Ability/DeleteAll`
- `/api/services/app/Ability/ExportData`
- `/api/services/app/Ability/FindAllAblibities`
- `/api/services/app/Ability/FindPageAblibities`
- `/api/services/app/Ability/GetAbilityForEdit`
- `/api/services/app/Ability/GetAllUnits`
- `/api/services/app/Ability/GetMyOrgSetting`
- `/api/services/app/Ability/GetOrgTypeLit`
- `/api/services/app/Ability/GetTemplateExcel`
- `/api/services/app/Ability/SaveExcelData`
- `/api/services/app/Ability/UpdateAbility`
- `/api/services/app/AbilityHistory/GetAbilityHistory`
- `/api/services/app/AbilityHistory/GetHistoryDetail`
- `/api/services/app/AbilityProperty/AbilityPropertyList`
- `/api/services/app/AbilityProperty/GetOrgAbilitySetting`
- `/api/services/app/AbilityProperty/OrgAbilityPropertyList`
- `/api/services/app/AbilityProperty/SaveOrgSetting`
- `/api/services/app/AbilityQuery/FindAblibities`
- `/api/services/app/AbilityQuery/FindHistory`
- `/api/services/app/Account/ActivateEmail`
- `/api/services/app/Account/BackToImpersonator`
- `/api/services/app/Account/DelegatedImpersonate`
- `/api/services/app/Account/Impersonate`
- `/api/services/app/Account/IsTenantAvailable`
- `/api/services/app/Account/Register`
- `/api/services/app/Account/ResetPassword`
- `/api/services/app/Account/ResolveTenantId`
- `/api/services/app/Account/SendEmailActivationLink`
- `/api/services/app/Account/SendPasswordResetCode`
- `/api/services/app/Account/SwitchToLinkedAccount`
- `/api/services/app/AuditLog/GetAuditLogs`
- `/api/services/app/AuditLog/GetAuditLogsToExcel`
- `/api/services/app/AuditLog/GetEntityChanges`
- `/api/services/app/AuditLog/GetEntityChangesToExcel`
- `/api/services/app/AuditLog/GetEntityHistoryObjectTypes`
- `/api/services/app/AuditLog/GetEntityPropertyChanges`
- `/api/services/app/AuditLog/GetEntityTypeChanges`
- `/api/services/app/Caching/ClearAllCaches`
- `/api/services/app/Caching/ClearCache`
- `/api/services/app/Caching/GetAllCaches`
- `/api/services/app/Chat/GetUserChatFriendsWithSettings`
- `/api/services/app/Chat/GetUserChatMessages`
- `/api/services/app/Chat/MarkAllUnreadMessagesOfUserAsRead`
- `/api/services/app/CommonLookup/FindUsers`
- `/api/services/app/CommonLookup/GetDefaultEditionName`
- `/api/services/app/CommonLookup/GetEditionsForCombobox`
- `/api/services/app/Dashboard/ChangeCountInWeek`
- `/api/services/app/Dashboard/OrgCount`
- `/api/services/app/Dashboard/Statistics`
- `/api/services/app/DashboardCustomization/AddNewPage`
- `/api/services/app/DashboardCustomization/AddWidget`
- `/api/services/app/DashboardCustomization/DeletePage`
- `/api/services/app/DashboardCustomization/GetAllWidgetDefinitions`
- `/api/services/app/DashboardCustomization/GetDashboardDefinition`
- `/api/services/app/DashboardCustomization/GetSettingName`
- `/api/services/app/DashboardCustomization/GetUserDashboard`
- `/api/services/app/DashboardCustomization/RenamePage`
- `/api/services/app/DashboardCustomization/SavePage`
- `/api/services/app/DemoUiComponents/GetCountries`
- `/api/services/app/DemoUiComponents/SendAndGetDate`
- `/api/services/app/DemoUiComponents/SendAndGetDateRange`
- `/api/services/app/DemoUiComponents/SendAndGetDateTime`
- `/api/services/app/DemoUiComponents/SendAndGetSelectedCountries`
- `/api/services/app/DemoUiComponents/SendAndGetValue`
- `/api/services/app/DynamicEntityParameterDefinition/GetAllAllowedInputTypeNames`
- `/api/services/app/DynamicEntityParameterDefinition/GetAllEntities`
- `/api/services/app/DynamicParameter/Add`
- `/api/services/app/DynamicParameter/Delete`
- `/api/services/app/DynamicParameter/FindAllowedInputType`
- `/api/services/app/DynamicParameter/Get`
- `/api/services/app/DynamicParameter/GetAll`
- `/api/services/app/DynamicParameter/Update`
- `/api/services/app/DynamicParameterValue/Add`
- `/api/services/app/DynamicParameterValue/Delete`
- `/api/services/app/DynamicParameterValue/Get`
- `/api/services/app/DynamicParameterValue/GetAllValuesOfDynamicParameter`
- `/api/services/app/DynamicParameterValue/Update`
- `/api/services/app/Edition/CreateEdition`
- `/api/services/app/Edition/DeleteEdition`
- `/api/services/app/Edition/GetEditionComboboxItems`
- `/api/services/app/Edition/GetEditionForEdit`
- `/api/services/app/Edition/GetEditions`
- `/api/services/app/Edition/GetTenantCount`
- `/api/services/app/Edition/MoveTenantsToAnotherEdition`
- `/api/services/app/Edition/UpdateEdition`
- `/api/services/app/EntityDynamicParameter/Add`
- `/api/services/app/EntityDynamicParameter/Delete`
- `/api/services/app/EntityDynamicParameter/Get`
- `/api/services/app/EntityDynamicParameter/GetAll`
- `/api/services/app/EntityDynamicParameter/GetAllParametersOfAnEntity`
- `/api/services/app/EntityDynamicParameter/Update`
- `/api/services/app/EntityDynamicParameterValue/Add`
- `/api/services/app/EntityDynamicParameterValue/CleanValues`
- `/api/services/app/EntityDynamicParameterValue/Delete`
- `/api/services/app/EntityDynamicParameterValue/Get`
- `/api/services/app/EntityDynamicParameterValue/GetAll`
- `/api/services/app/EntityDynamicParameterValue/GetAllEntityDynamicParameterValues`
- `/api/services/app/EntityDynamicParameterValue/InsertOrUpdateAllValues`
- `/api/services/app/EntityDynamicParameterValue/Update`
- `/api/services/app/Friendship/AcceptFriendshipRequest`
- `/api/services/app/Friendship/BlockUser`
- `/api/services/app/Friendship/CreateFriendshipRequest`
- `/api/services/app/Friendship/CreateFriendshipRequestByUserName`
- `/api/services/app/Friendship/UnblockUser`
- `/api/services/app/HostDashboard/GetEditionTenantStatistics`
- `/api/services/app/HostDashboard/GetIncomeStatistics`
- `/api/services/app/HostDashboard/GetRecentTenantsData`
- `/api/services/app/HostDashboard/GetSubscriptionExpiringTenantsData`
- `/api/services/app/HostDashboard/GetTopStatsData`
- `/api/services/app/HostSettings/GetAbilitySettings`
- `/api/services/app/HostSettings/GetAllSettings`
- `/api/services/app/HostSettings/SendTestEmail`
- `/api/services/app/HostSettings/UpdateAbilitySettings`
- `/api/services/app/HostSettings/UpdateAllSettings`
- `/api/services/app/Install/CheckDatabase`
- `/api/services/app/Install/GetAppSettingsJson`
- `/api/services/app/Install/Setup`
- `/api/services/app/Invoice/CreateInvoice`
- `/api/services/app/Invoice/GetInvoiceInfo`
- `/api/services/app/Laboratory/CreateOrUpdate`
- `/api/services/app/Laboratory/DeleteLab`
- `/api/services/app/Laboratory/GetLabForEdit`
- `/api/services/app/Laboratory/List`
- `/api/services/app/Language/CreateOrUpdateLanguage`
- `/api/services/app/Language/DeleteLanguage`
- `/api/services/app/Language/GetLanguageForEdit`
- `/api/services/app/Language/GetLanguageTexts`
- `/api/services/app/Language/GetLanguages`
- `/api/services/app/Language/SetDefaultLanguage`
- `/api/services/app/Language/UpdateLanguageText`
- `/api/services/app/MyFavorite/AddItem`
- `/api/services/app/MyFavorite/DeleteMyFavorite`
- `/api/services/app/MyFavorite/GetMyFavoriteAbilityList`
- `/api/services/app/MyFavorite/GetMyFavoriteForEdit`
- `/api/services/app/MyFavorite/GetMyFavoriteList`
- `/api/services/app/MyFavorite/RemoveItem`
- `/api/services/app/MyFavorite/SaveOrUpdateMyFavorite`
- `/api/services/app/Notification/DeleteAllUserNotifications`
- `/api/services/app/Notification/DeleteNotification`
- `/api/services/app/Notification/GetNotificationSettings`
- `/api/services/app/Notification/GetUserNotifications`
- `/api/services/app/Notification/SetAllNotificationsAsRead`
- `/api/services/app/Notification/SetNotificationAsRead`
- `/api/services/app/Notification/UpdateNotificationSettings`
- `/api/services/app/OrganizationUnit/AddRolesToOrganizationUnit`
- `/api/services/app/OrganizationUnit/AddUsersToOrganizationUnit`
- `/api/services/app/OrganizationUnit/CreateOrganizationUnit`
- `/api/services/app/OrganizationUnit/DeleteOrganizationUnit`
- `/api/services/app/OrganizationUnit/FindRoles`
- `/api/services/app/OrganizationUnit/FindUsers`
- `/api/services/app/OrganizationUnit/GetOrganizationUnitRoles`
- `/api/services/app/OrganizationUnit/GetOrganizationUnitUsers`
- `/api/services/app/OrganizationUnit/GetOrganizationUnits`
- `/api/services/app/OrganizationUnit/MoveOrganizationUnit`
- `/api/services/app/OrganizationUnit/RemoveRoleFromOrganizationUnit`
- `/api/services/app/OrganizationUnit/RemoveUserFromOrganizationUnit`
- `/api/services/app/OrganizationUnit/UpdateOrganizationUnit`
- `/api/services/app/PayPalPayment/ConfirmPayment`
- `/api/services/app/PayPalPayment/GetConfiguration`
- `/api/services/app/Payment/BuyNowSucceed`
- `/api/services/app/Payment/CancelPayment`
- `/api/services/app/Payment/CreatePayment`
- `/api/services/app/Payment/ExtendSucceed`
- `/api/services/app/Payment/GetActiveGateways`
- `/api/services/app/Payment/GetLastCompletedPayment`
- `/api/services/app/Payment/GetPayment`
- `/api/services/app/Payment/GetPaymentHistory`
- `/api/services/app/Payment/GetPaymentInfo`
- `/api/services/app/Payment/HasAnyPayment`
- `/api/services/app/Payment/NewRegistrationSucceed`
- `/api/services/app/Payment/PaymentFailed`
- `/api/services/app/Payment/SwitchBetweenFreeEditions`
- `/api/services/app/Payment/UpgradeSubscriptionCostsLessThenMinAmount`
- `/api/services/app/Payment/UpgradeSucceed`
- `/api/services/app/Permission/GetAllPermissions`
- `/api/services/app/Profile/ChangeLanguage`
- `/api/services/app/Profile/ChangePassword`
- `/api/services/app/Profile/DisableGoogleAuthenticator`
- `/api/services/app/Profile/GetCurrentUserProfileForEdit`
- `/api/services/app/Profile/GetFriendProfilePictureById`
- `/api/services/app/Profile/GetPasswordComplexitySetting`
- `/api/services/app/Profile/GetProfilePicture`
- `/api/services/app/Profile/GetProfilePictureById`
- `/api/services/app/Profile/PrepareCollectedData`
- `/api/services/app/Profile/SendVerificationSms`
- `/api/services/app/Profile/UpdateCurrentUserProfile`
- `/api/services/app/Profile/UpdateGoogleAuthenticatorKey`
- `/api/services/app/Profile/UpdateProfilePicture`
- `/api/services/app/Profile/VerifySmsCode`
- `/api/services/app/Role/CreateOrUpdateRole`
- `/api/services/app/Role/DeleteRole`
- `/api/services/app/Role/GetRoleForEdit`
- `/api/services/app/Role/GetRoles`
- `/api/services/app/Sample/CreateOrUpdate`
- `/api/services/app/Sample/DeleteSample`
- `/api/services/app/Sample/GetForEdit`
- `/api/services/app/Sample/GetList`
- `/api/services/app/SampleType/CreateOrUpdate`
- `/api/services/app/SampleType/DeleteSampleType`
- `/api/services/app/SampleType/GetForEdit`
- `/api/services/app/SampleType/GetList`
- `/api/services/app/SampleType/GetListByOrg`
- `/api/services/app/Session/GetCurrentLoginInformations`
- `/api/services/app/Session/UpdateUserSignInToken`
- `/api/services/app/Standard/UploadNewStandard`
- `/api/services/app/StripePayment/CreatePaymentSession`
- `/api/services/app/StripePayment/GetConfiguration`
- `/api/services/app/StripePayment/GetPayment`
- `/api/services/app/StripePayment/GetPaymentResult`
- `/api/services/app/SubcontractAbility/FindList`
- `/api/services/app/SubcontractAbility/SaveExcelData`
- `/api/services/app/Subscription/DisableRecurringPayments`
- `/api/services/app/Subscription/EnableRecurringPayments`
- `/api/services/app/Tenant/CreateTenant`
- `/api/services/app/Tenant/DeleteTenant`
- `/api/services/app/Tenant/GetTenantFeaturesForEdit`
- `/api/services/app/Tenant/GetTenantForEdit`
- `/api/services/app/Tenant/GetTenants`
- `/api/services/app/Tenant/ResetTenantSpecificFeatures`
- `/api/services/app/Tenant/UnlockTenantAdmin`
- `/api/services/app/Tenant/UpdateTenant`
- `/api/services/app/Tenant/UpdateTenantFeatures`
- `/api/services/app/TenantDashboard/GetDailySales`
- `/api/services/app/TenantDashboard/GetDashboardData`
- `/api/services/app/TenantDashboard/GetGeneralStats`
- `/api/services/app/TenantDashboard/GetMemberActivity`
- `/api/services/app/TenantDashboard/GetProfitShare`
- `/api/services/app/TenantDashboard/GetRegionalStats`
- `/api/services/app/TenantDashboard/GetSalesSummary`
- `/api/services/app/TenantDashboard/GetTopStats`
- `/api/services/app/TenantRegistration/GetEdition`
- `/api/services/app/TenantRegistration/GetEditionsForSelect`
- `/api/services/app/TenantRegistration/RegisterTenant`
- `/api/services/app/TenantSettings/ClearCustomCss`
- `/api/services/app/TenantSettings/ClearLogo`
- `/api/services/app/TenantSettings/GetAllSettings`
- `/api/services/app/TenantSettings/SendTestEmail`
- `/api/services/app/TenantSettings/UpdateAllSettings`
- `/api/services/app/Timing/GetTimezoneComboboxItems`
- `/api/services/app/Timing/GetTimezones`
- `/api/services/app/UiCustomizationSettings/ChangeThemeWithDefaultValues`
- `/api/services/app/UiCustomizationSettings/GetUiManagementSettings`
- `/api/services/app/UiCustomizationSettings/UpdateDefaultUiManagementSettings`
- `/api/services/app/UiCustomizationSettings/UpdateUiManagementSettings`
- `/api/services/app/UiCustomizationSettings/UseSystemDefaultSettings`
- `/api/services/app/User/CreateOrUpdateUser`
- `/api/services/app/User/DeleteUser`
- `/api/services/app/User/GetUserForEdit`
- `/api/services/app/User/GetUserPermissionsForEdit`
- `/api/services/app/User/GetUsers`
- `/api/services/app/User/GetUsersToExcel`
- `/api/services/app/User/ResetUserPassword`
- `/api/services/app/User/ResetUserSpecificPermissions`
- `/api/services/app/User/UnlockUser`
- `/api/services/app/User/UpdateUserPermissions`
- `/api/services/app/UserDelegation/DelegateNewUser`
- `/api/services/app/UserDelegation/GetActiveUserDelegations`
- `/api/services/app/UserDelegation/GetDelegatedUsers`
- `/api/services/app/UserDelegation/RemoveDelegation`
- `/api/services/app/UserLink/GetLinkedUsers`
- `/api/services/app/UserLink/GetRecentlyUsedLinkedUsers`
- `/api/services/app/UserLink/LinkToUser`
- `/api/services/app/UserLink/UnlinkUser`
- `/api/services/app/UserLogin/GetRecentUserLoginAttempts`
- `/api/services/app/WebLog/DownloadWebLogs`
- `/api/services/app/WebLog/GetLatestWebLogs`
- `/api/services/app/WebhookEvent/Get`
- `/api/services/app/WebhookSendAttempt/GetAllSendAttempts`
- `/api/services/app/WebhookSendAttempt/GetAllSendAttemptsOfWebhookEvent`
- `/api/services/app/WebhookSendAttempt/Resend`
- `/api/services/app/WebhookSubscription/ActivateWebhookSubscription`
- `/api/services/app/WebhookSubscription/AddSubscription`
- `/api/services/app/WebhookSubscription/GetAllAvailableWebhooks`
- `/api/services/app/WebhookSubscription/GetAllSubscriptions`
- `/api/services/app/WebhookSubscription/GetAllSubscriptionsIfFeaturesGranted`
- `/api/services/app/WebhookSubscription/GetSubscription`
- `/api/services/app/WebhookSubscription/IsSubscribed`
- `/api/services/app/WebhookSubscription/PublishTestWebhook`
- `/api/services/app/WebhookSubscription/UpdateSubscription`
