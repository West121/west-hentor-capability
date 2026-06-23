import { apiDelete, apiDownload, apiGet, apiPost, apiPut, apiUpload } from './http';
import { tokenAuthAuthenticatePayload, type TokenAuthCredentials } from './tokenAuthRequest';
import {
  abilityPropertySettingQuery,
  chatMessagesQuery,
  changeThemeWithDefaultValuesQuery,
  deleteAllUserNotificationsQuery,
  demoCountriesQuery,
  demoSendAndGetDateQuery,
  demoSendAndGetDateRangeQuery,
  demoSendAndGetDateTimeQuery,
  demoSendAndGetValueQuery,
  dynamicParameterValuesQuery,
  editionComboboxItemsQuery,
  entityDynamicParametersQuery,
  entityDynamicParameterValuesQuery,
  favoriteAbilitiesQuery,
  findAllowedInputTypeQuery,
  hostDashboardEditionTenantStatisticsQuery,
  hostDashboardIncomeStatisticsQuery,
  hostDashboardTopStatsQuery,
  invoiceInfoQuery,
  paymentAsyncPath,
  payPalConfirmPaymentQuery,
  paymentCallbackQuery,
  paymentDetailQuery,
  paymentEditionSwitchQuery,
  stripePaymentAsyncPath,
  stripePaymentResultQuery,
  stripePaymentDetailQuery,
  tenantDashboardDataQuery,
  tenantRegistrationEditionQuery,
  tenantSalesSummaryQuery,
  upgradeSubscriptionCostsLessThenMinAmountQuery,
  userNotificationsQuery,
  webhookEventQuery,
} from './requestContracts';
import type { EntityDynamicParameterValuesQueryParams } from './requestContracts';
import type {
  Ability,
  AbilityHistoryDetailItem,
  AbilitySettings,
  AbilityTableUploadOutput,
  AbilityHistoryItem,
  AbilityProperty,
  AppSettingsJsonDto,
  AuditLog,
  CacheItem,
  CheckDatabaseOutput,
  ChatFriendsWithSettings,
  ChatMessageItem,
  ComboboxItem,
  CurrentUserProfile,
  DashboardCustomizationItem,
  DashboardDefinitionOutput,
  DashboardPageItem,
  DashboardStatistics,
  DashboardWidgetItem,
  DateToStringOutput,
  DynamicParameterItem,
  DynamicInputTypeInfo,
  DynamicParameterValueItem,
  EntityChangeItem,
  EntityDynamicParameterItem,
  EntityDynamicParameterValueItem,
  EntityDynamicParameterValuesInputItem,
  EntityDynamicParameterValuesOutput,
  EntityHistoryObjectType,
  EntityPropertyChangeItem,
  EditionItem,
  EditionsSelectOutput,
  EditionSelectDto,
  ExternalAuthenticateInput,
  ExternalAuthenticateResult,
  ExternalLoginProviderInfo,
  FavoriteGroup,
  FeatureItem,
  FeatureValueItem,
  FileDto,
  FriendItem,
  GoogleAuthenticatorOutput,
  HostEditionTenantStatisticsOutput,
  HostExpiringTenantsOutput,
  HostIncomeStatisticsOutput,
  HostSettings,
  HostRecentTenantsOutput,
  HostTopStatsData,
  ImportAbilityTableDto,
  InstallDto,
  InvoiceItem,
  LanguageItem,
  LanguageTextItem,
  Laboratory,
  ListResult,
  LinkedUserItem,
  MyOrgSetting,
  NameValue,
  NotificationItem,
  NotificationSettings,
  OrgCount,
  OrganizationUnit,
  PageResult,
  PaymentGatewayItem,
  PermissionItem,
  ProfilePictureOutput,
  RoleItem,
  Sample,
  SampleType,
  SessionLoginInfo,
  SubcontractAbility,
  StringOutput,
  StripePaymentResultOutput,
  SubscriptionPaymentItem,
  ThemeSettingsItem,
  TenantBrandingUploadResult,
  TenantItem,
  TenantDashboardData,
  TenantGeneralStats,
  TenantSalesSummaryOutput,
  TenantSettings,
  TenantTopStats,
  MemberActivity,
  RegionalStatCountry,
  RegisterTenantInput,
  RegisterTenantOutput,
  SubscribableEditionComboboxItem,
  UeditorUploadOutput,
  UploadStandardOutput,
  UploadFileOutput,
  UploadProfilePictureOutput,
  UpdateUserSignInTokenOutput,
  UploadSubcontractAbilityOutput,
  UpdateProfilePictureInput,
  UserDelegation,
  UserImportOutput,
  UserItem,
  UserLoginAttemptItem,
  WebhookDefinitionItem,
  WebhookEventItem,
  WebhookSendAttemptItem,
  WebhookSubscriptionItem,
  WidgetOutput,
  WebLogOutput,
} from '../types/domain';

const appendQueryValue = (search: URLSearchParams, key: string, value: unknown) => {
  if (value === undefined || value === null) {
    return;
  }
  if (Array.isArray(value)) {
    value.forEach((item) => appendQueryValue(search, key, item));
    return;
  }
  search.append(key, value instanceof Date ? value.toJSON() : String(value));
};

const queryValue = (params: unknown, ...keys: string[]) => {
  if (!params || typeof params !== 'object') {
    return undefined;
  }
  const source = params as Record<string, unknown>;
  for (const key of keys) {
    if (source[key] !== undefined) {
      return source[key];
    }
  }
  return undefined;
};

const withQuery = (url: string, params: Record<string, unknown>) => {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => appendQueryValue(search, key, value));
  const query = search.toString();
  return query ? `${url}?${query}` : url;
};

// Centralizes original ABP route names for easier parity checks.
export const api = {
  login: (credentials?: TokenAuthCredentials) =>
    apiPost<{
      accessToken: string;
      encryptedAccessToken?: string;
      expireInSeconds?: number;
      userId?: number;
      requiresTwoFactorVerification?: boolean;
      twoFactorAuthProviders?: string[];
      twoFactorRememberClientToken?: string;
      shouldResetPassword?: boolean;
      passwordResetCode?: string;
      returnUrl?: string;
      refreshToken?: string;
      refreshTokenExpireInSeconds?: number;
    }>('/api/TokenAuth/Authenticate', tokenAuthAuthenticatePayload(credentials)),
  refreshToken: (refreshToken?: string) =>
    apiPost<{ accessToken: string; encryptedAccessToken?: string; expireInSeconds?: number }>('/api/TokenAuth/RefreshToken', {
      refreshToken,
    }),
  logoutToken: () => apiGet<void>('/api/TokenAuth/LogOut'),
  sendTwoFactorAuthCode: (userId?: number, provider = 'Email') =>
    apiPost<void>('/api/TokenAuth/SendTwoFactorAuthCode', { userId, provider }),
  externalAuthenticationProviders: () =>
    apiGet<ExternalLoginProviderInfo[]>('/api/TokenAuth/GetExternalAuthenticationProviders'),
  externalAuthenticate: (payload: ExternalAuthenticateInput) =>
    apiPost<ExternalAuthenticateResult>('/api/TokenAuth/ExternalAuthenticate', payload),
  testNotification: (message?: string, severity?: string) =>
    apiGet<void>(withQuery('/api/TokenAuth/TestNotification', { message, severity })),
  delegatedImpersonatedAuthenticate: (userDelegationId: number, impersonationToken: string) =>
    apiPost<{
      accessToken: string;
      encryptedAccessToken?: string;
      expireInSeconds?: number;
      refreshToken?: string;
    }>(
      `/api/TokenAuth/DelegatedImpersonatedAuthenticate?${new URLSearchParams({
        userDelegationId: String(userDelegationId),
        impersonationToken,
      })}`,
    ),
  impersonatedAuthenticate: (impersonationToken: string) =>
    apiPost<{ accessToken: string; refreshToken?: string }>(`/api/TokenAuth/ImpersonatedAuthenticate?impersonationToken=${impersonationToken}`),
  linkedAccountAuthenticate: (switchAccountToken: string) =>
    apiPost<{ accessToken: string }>(`/api/TokenAuth/LinkedAccountAuthenticate?switchAccountToken=${switchAccountToken}`),
  resolveTenantId: (c?: string) => apiPost<number | null>('/api/services/app/Account/ResolveTenantId', { c }),
  installSetup: (payload: InstallDto) => apiPost<void>('/api/services/app/Install/Setup', payload),
  installAppSettingsJson: () => apiGet<AppSettingsJsonDto>('/api/services/app/Install/GetAppSettingsJson'),
  installCheckDatabase: () => apiPost<CheckDatabaseOutput>('/api/services/app/Install/CheckDatabase'),
  session: () => apiGet<SessionLoginInfo>('/api/services/app/Session/GetCurrentLoginInformations'),
  updateUserSignInToken: () =>
    apiPut<UpdateUserSignInTokenOutput>('/api/services/app/Session/UpdateUserSignInToken'),
  abilities: (params?: unknown) =>
    apiPost<PageResult<Ability>>('/api/services/app/Ability/FindPageAblibities', params),
  allAbilities: () => apiPost<Ability[]>('/api/services/app/Ability/FindAllAblibities'),
  myOrgSettings: () => apiGet<ListResult<MyOrgSetting>>('/api/services/app/Ability/GetMyOrgSetting'),
  allUnits: () => apiGet<ListResult<OrganizationUnit>>('/api/services/app/Ability/GetAllUnits'),
  orgTypeList: (id?: number) => apiGet<ListResult<NameValue>>(withQuery('/api/services/app/Ability/GetOrgTypeLit', { Id: id })),
  queryAbilities: (params?: unknown) =>
    apiPost<PageResult<Ability> & { labs?: Laboratory[] }>('/api/services/app/AbilityQuery/FindAblibities', params),
  abilityForEdit: (id?: string) =>
    apiGet<{ abilityDto?: Ability; labList: Laboratory[]; orgList: OrganizationUnit[]; sampleTypeList: SampleType[] }>(
      withQuery('/api/services/app/Ability/GetAbilityForEdit', { Id: id }),
    ),
  createAbility: (ability: Ability) => apiPost<void>('/api/services/app/Ability/CreateAbility', ability),
  updateAbility: (ability: Ability) => apiPut<void>('/api/services/app/Ability/UpdateAbility', ability),
  deleteAbility: (id: string) => apiDelete<void>(withQuery('/api/services/app/Ability/DeleteAbility', { Id: id })),
  deleteAllAbilities: (orgName: string) => apiDelete<void>(withQuery('/api/services/app/Ability/DeleteAll', { OrgName: orgName })),
  abilityTemplate: (params?: { orgId?: number }) =>
    apiGet<FileDto>(withQuery('/api/services/app/Ability/GetTemplateExcel', { OrgId: params?.orgId })),
  exportAbilities: (params?: unknown) => apiPost<FileDto>('/api/services/app/Ability/ExportData', params ?? {}),
  uploadAbilityTable: (file: File, orgId?: number) =>
    apiUpload<AbilityTableUploadOutput>('/AbilityTable/UploadAbilityTable', file, { orgId }),
  saveAbilityExcel: (dataList: ImportAbilityTableDto[], onlySaveNew: boolean) =>
    apiPost<void>('/api/services/app/Ability/SaveExcelData', { dataList, onlySaveNew }),
  labs: () => apiPost<{ list: Laboratory[] }>('/api/services/app/Laboratory/List'),
  saveLab: (lab: Laboratory) => apiPost<string>('/api/services/app/Laboratory/CreateOrUpdate', lab),
  labForEdit: (id?: string) => apiGet<Laboratory>(withQuery('/api/services/app/Laboratory/GetLabForEdit', { Id: id })),
  deleteLab: (id: string) => apiDelete<void>(withQuery('/api/services/app/Laboratory/DeleteLab', { Id: id })),
  sampleTypes: () => apiGet<ListResult<SampleType>>('/api/services/app/SampleType/GetList'),
  sampleTypesByOrg: (orgId?: number) => apiGet<ListResult<SampleType>>(withQuery('/api/services/app/SampleType/GetListByOrg', { OrgId: orgId })),
  sampleTypeForEdit: (id?: string) =>
    apiGet<{ type: SampleType; orgList: OrganizationUnit[] }>(withQuery('/api/services/app/SampleType/GetForEdit', { Id: id })),
  saveSampleType: (sampleType: SampleType) => apiPost<string>('/api/services/app/SampleType/CreateOrUpdate', sampleType),
  deleteSampleType: (id: string) => apiDelete<void>(withQuery('/api/services/app/SampleType/DeleteSampleType', { Id: id })),
  samples: (typeId?: string) => apiGet<ListResult<Sample>>(withQuery('/api/services/app/Sample/GetList', { TypeId: typeId })),
  sampleForEdit: (id?: string) => apiGet<Sample>(withQuery('/api/services/app/Sample/GetForEdit', { Id: id })),
  saveSample: (sample: Sample) => apiPost<string>('/api/services/app/Sample/CreateOrUpdate', sample),
  deleteSample: (id: string) => apiDelete<void>(withQuery('/api/services/app/Sample/DeleteSample', { Id: id })),
  orgUnits: () => apiGet<ListResult<OrganizationUnit>>('/api/services/app/OrganizationUnit/GetOrganizationUnits'),
  createOrgUnit: (org: OrganizationUnit) =>
    apiPost<OrganizationUnit>('/api/services/app/OrganizationUnit/CreateOrganizationUnit', org),
  updateOrgUnit: (org: OrganizationUnit) =>
    apiPut<OrganizationUnit>('/api/services/app/OrganizationUnit/UpdateOrganizationUnit', org),
  moveOrgUnit: (id: number, newParentId?: number | null) =>
    apiPost<OrganizationUnit>('/api/services/app/OrganizationUnit/MoveOrganizationUnit', { id, newParentId }),
  deleteOrgUnit: (id: number) => apiDelete<void>(withQuery('/api/services/app/OrganizationUnit/DeleteOrganizationUnit', { Id: id })),
  // Original generated client uses GET plus PascalCase query keys for membership reads.
  orgUnitUsers: (id: number, params?: { sorting?: string; maxResultCount?: number; skipCount?: number }) =>
    apiGet<PageResult<UserItem>>(
      withQuery('/api/services/app/OrganizationUnit/GetOrganizationUnitUsers', {
        Id: id,
        Sorting: params?.sorting,
        MaxResultCount: params?.maxResultCount,
        SkipCount: params?.skipCount,
      }),
    ),
  orgUnitRoles: (id: number, params?: { sorting?: string; maxResultCount?: number; skipCount?: number }) =>
    apiGet<PageResult<RoleItem>>(
      withQuery('/api/services/app/OrganizationUnit/GetOrganizationUnitRoles', {
        Id: id,
        Sorting: params?.sorting,
        MaxResultCount: params?.maxResultCount,
        SkipCount: params?.skipCount,
      }),
    ),
  findOrgUnitUsers: (organizationUnitId?: number, filter = '', maxResultCount = 100) =>
    apiPost<PageResult<NameValue>>('/api/services/app/OrganizationUnit/FindUsers', {
      organizationUnitId,
      filter,
      maxResultCount,
    }),
  findOrgUnitRoles: (organizationUnitId?: number, filter = '', maxResultCount = 100) =>
    apiPost<PageResult<NameValue>>('/api/services/app/OrganizationUnit/FindRoles', {
      organizationUnitId,
      filter,
      maxResultCount,
    }),
  addUsersToOrgUnit: (organizationUnitId: number, userIds: number[]) =>
    apiPost<void>('/api/services/app/OrganizationUnit/AddUsersToOrganizationUnit', { organizationUnitId, userIds }),
  removeUserFromOrgUnit: (organizationUnitId: number, userId: number) =>
    apiDelete<void>(
      withQuery('/api/services/app/OrganizationUnit/RemoveUserFromOrganizationUnit', {
        UserId: userId,
        OrganizationUnitId: organizationUnitId,
      }),
    ),
  addRolesToOrgUnit: (organizationUnitId: number, roleIds: number[]) =>
    apiPost<void>('/api/services/app/OrganizationUnit/AddRolesToOrganizationUnit', { organizationUnitId, roleIds }),
  removeRoleFromOrgUnit: (organizationUnitId: number, roleId: number) =>
    apiDelete<void>(
      withQuery('/api/services/app/OrganizationUnit/RemoveRoleFromOrganizationUnit', {
        RoleId: roleId,
        OrganizationUnitId: organizationUnitId,
      }),
    ),
  orgAbilityProperties: (orgId: number) =>
    apiPost<{ propertyList: AbilityProperty[]; isPublic: boolean; description: string }>(
      '/api/services/app/AbilityProperty/OrgAbilityPropertyList',
      { orgId },
    ),
  abilityProperties: () => apiPost<AbilityProperty[]>('/api/services/app/AbilityProperty/AbilityPropertyList'),
  orgAbilitySetting: (orgId?: number) => apiGet<string[]>(abilityPropertySettingQuery(orgId)),
  saveOrgSetting: (payload: unknown) => apiPost<void>('/api/services/app/AbilityProperty/SaveOrgSetting', payload),
  // Original generated client uses GET plus PascalCase query keys for these log reads.
  abilityHistory: (params?: { sorting?: string; maxResultCount?: number; skipCount?: number }) =>
    apiGet<PageResult<AbilityHistoryItem>>(
      withQuery('/api/services/app/AbilityHistory/GetAbilityHistory', {
        Sorting: params?.sorting,
        MaxResultCount: params?.maxResultCount,
        SkipCount: params?.skipCount,
      }),
    ),
  abilityHistoryDetail: (id?: number) =>
    apiGet<ListResult<AbilityHistoryDetailItem>>(withQuery('/api/services/app/AbilityHistory/GetHistoryDetail', { Id: id })),
  queryAbilityHistory: (id?: string) => apiPost<AbilityHistoryItem[]>('/api/services/app/AbilityQuery/FindHistory', { id }),
  auditLogs: (params?: unknown) =>
    apiGet<PageResult<AuditLog>>(
      withQuery('/api/services/app/AuditLog/GetAuditLogs', {
        StartDate: queryValue(params, 'StartDate', 'startDate'),
        EndDate: queryValue(params, 'EndDate', 'endDate'),
        UserName: queryValue(params, 'UserName', 'userName'),
        ServiceName: queryValue(params, 'ServiceName', 'serviceName'),
        MethodName: queryValue(params, 'MethodName', 'methodName'),
        BrowserInfo: queryValue(params, 'BrowserInfo', 'browserInfo'),
        HasException: queryValue(params, 'HasException', 'hasException'),
        MinExecutionDuration: queryValue(params, 'MinExecutionDuration', 'minExecutionDuration'),
        MaxExecutionDuration: queryValue(params, 'MaxExecutionDuration', 'maxExecutionDuration'),
        Sorting: queryValue(params, 'Sorting', 'sorting'),
        MaxResultCount: queryValue(params, 'MaxResultCount', 'maxResultCount'),
        SkipCount: queryValue(params, 'SkipCount', 'skipCount'),
      }),
    ),
  exportAuditLogs: (params?: unknown) =>
    apiGet<FileDto>(
      withQuery('/api/services/app/AuditLog/GetAuditLogsToExcel', {
        StartDate: queryValue(params, 'StartDate', 'startDate'),
        EndDate: queryValue(params, 'EndDate', 'endDate'),
        UserName: queryValue(params, 'UserName', 'userName'),
        ServiceName: queryValue(params, 'ServiceName', 'serviceName'),
        MethodName: queryValue(params, 'MethodName', 'methodName'),
        BrowserInfo: queryValue(params, 'BrowserInfo', 'browserInfo'),
        HasException: queryValue(params, 'HasException', 'hasException'),
        MinExecutionDuration: queryValue(params, 'MinExecutionDuration', 'minExecutionDuration'),
        MaxExecutionDuration: queryValue(params, 'MaxExecutionDuration', 'maxExecutionDuration'),
        Sorting: queryValue(params, 'Sorting', 'sorting'),
        MaxResultCount: queryValue(params, 'MaxResultCount', 'maxResultCount'),
        SkipCount: queryValue(params, 'SkipCount', 'skipCount'),
      }),
    ),
  entityHistoryObjectTypes: () =>
    apiGet<EntityHistoryObjectType[]>('/api/services/app/AuditLog/GetEntityHistoryObjectTypes'),
  entityChanges: (params?: unknown) =>
    apiGet<PageResult<EntityChangeItem>>(
      withQuery('/api/services/app/AuditLog/GetEntityChanges', {
        StartDate: queryValue(params, 'StartDate', 'startDate'),
        EndDate: queryValue(params, 'EndDate', 'endDate'),
        UserName: queryValue(params, 'UserName', 'userName'),
        EntityTypeFullName: queryValue(params, 'EntityTypeFullName', 'entityTypeFullName'),
        Sorting: queryValue(params, 'Sorting', 'sorting'),
        MaxResultCount: queryValue(params, 'MaxResultCount', 'maxResultCount'),
        SkipCount: queryValue(params, 'SkipCount', 'skipCount'),
      }),
    ),
  entityTypeChanges: (params?: unknown) =>
    apiGet<PageResult<EntityChangeItem>>(
      withQuery('/api/services/app/AuditLog/GetEntityTypeChanges', {
        EntityTypeFullName: queryValue(params, 'EntityTypeFullName', 'entityTypeFullName'),
        EntityId: queryValue(params, 'EntityId', 'entityId'),
        Sorting: queryValue(params, 'Sorting', 'sorting'),
        MaxResultCount: queryValue(params, 'MaxResultCount', 'maxResultCount'),
        SkipCount: queryValue(params, 'SkipCount', 'skipCount'),
      }),
    ),
  exportEntityChanges: (params?: unknown) =>
    apiGet<FileDto>(
      withQuery('/api/services/app/AuditLog/GetEntityChangesToExcel', {
        StartDate: queryValue(params, 'StartDate', 'startDate'),
        EndDate: queryValue(params, 'EndDate', 'endDate'),
        UserName: queryValue(params, 'UserName', 'userName'),
        EntityTypeFullName: queryValue(params, 'EntityTypeFullName', 'entityTypeFullName'),
        Sorting: queryValue(params, 'Sorting', 'sorting'),
        MaxResultCount: queryValue(params, 'MaxResultCount', 'maxResultCount'),
        SkipCount: queryValue(params, 'SkipCount', 'skipCount'),
      }),
    ),
  entityPropertyChanges: (entityChangeId?: number) =>
    apiGet<EntityPropertyChangeItem[]>(
      withQuery('/api/services/app/AuditLog/GetEntityPropertyChanges', { entityChangeId }),
    ),
  dashboardStatistics: () => apiPost<DashboardStatistics>('/api/services/app/Dashboard/Statistics'),
  orgCount: () => apiPost<ListResult<OrgCount>>('/api/services/app/Dashboard/OrgCount'),
  changeCountInWeek: () => apiPost<ListResult<NameValue>>('/api/services/app/Dashboard/ChangeCountInWeek'),
  userDashboardLayout: (params?: { application?: string; dashboardName?: string }) =>
    apiGet<DashboardCustomizationItem>(
      withQuery('/api/services/app/DashboardCustomization/GetUserDashboard', {
        DashboardName: params?.dashboardName,
        Application: params?.application,
      }),
    ),
  saveDashboardPage: (params: { application?: string; dashboardName?: string; pages: DashboardPageItem[] }) =>
    apiPost<void>('/api/services/app/DashboardCustomization/SavePage', params),
  renameDashboardPage: (params: { application?: string; dashboardName?: string; id?: string; name?: string }) =>
    apiPost<void>('/api/services/app/DashboardCustomization/RenamePage', params),
  addDashboardPage: (params: { application?: string; dashboardName?: string; name?: string }) =>
    apiPost<{ pageId: string }>('/api/services/app/DashboardCustomization/AddNewPage', params),
  addDashboardWidget: (params: { application?: string; dashboardName?: string; pageId?: string; widgetId?: string; width?: number; height?: number }) =>
    apiPost<DashboardWidgetItem>('/api/services/app/DashboardCustomization/AddWidget', params),
  deleteDashboardPage: (params: { application?: string; dashboardName?: string; id?: string }) =>
    apiDelete<void>(
      withQuery('/api/services/app/DashboardCustomization/DeletePage', {
        Id: params.id,
        DashboardName: params.dashboardName,
        Application: params.application,
      }),
    ),
  dashboardDefinition: (params?: { application?: string; dashboardName?: string }) =>
    apiGet<DashboardDefinitionOutput>(
      withQuery('/api/services/app/DashboardCustomization/GetDashboardDefinition', {
        DashboardName: params?.dashboardName,
        Application: params?.application,
      }),
    ),
  allWidgetDefinitions: (params?: { application?: string; dashboardName?: string }) =>
    apiGet<WidgetOutput[]>(
      withQuery('/api/services/app/DashboardCustomization/GetAllWidgetDefinitions', {
        DashboardName: params?.dashboardName,
        Application: params?.application,
      }),
    ),
  dashboardSettingName: (application?: string) =>
    apiGet<string>(withQuery('/api/services/app/DashboardCustomization/GetSettingName', { application })),
  hostTopStats: (payload?: { startDate?: string; endDate?: string }) =>
    apiGet<HostTopStatsData>(hostDashboardTopStatsQuery(payload)),
  hostRecentTenants: () => apiGet<HostRecentTenantsOutput>('/api/services/app/HostDashboard/GetRecentTenantsData'),
  hostExpiringTenants: () =>
    apiGet<HostExpiringTenantsOutput>('/api/services/app/HostDashboard/GetSubscriptionExpiringTenantsData'),
  hostIncomeStatistics: (payload?: { startDate?: string; endDate?: string; incomeStatisticsDateInterval?: number }) =>
    apiGet<HostIncomeStatisticsOutput>(hostDashboardIncomeStatisticsQuery(payload)),
  hostEditionTenantStatistics: (payload?: { startDate?: string; endDate?: string }) =>
    apiGet<HostEditionTenantStatisticsOutput>(hostDashboardEditionTenantStatisticsQuery(payload)),
  tenantDashboardData: (salesSummaryDatePeriod = 1) =>
    apiGet<TenantDashboardData>(tenantDashboardDataQuery(salesSummaryDatePeriod)),
  tenantTopStats: () => apiGet<TenantTopStats>('/api/services/app/TenantDashboard/GetTopStats'),
  tenantProfitShare: () => apiGet<{ profitShares: number[] }>('/api/services/app/TenantDashboard/GetProfitShare'),
  tenantDailySales: () => apiGet<{ dailySales: number[] }>('/api/services/app/TenantDashboard/GetDailySales'),
  tenantSalesSummary: (salesSummaryDatePeriod = 1) =>
    apiGet<TenantSalesSummaryOutput>(tenantSalesSummaryQuery(salesSummaryDatePeriod)),
  tenantRegionalStats: () => apiGet<{ stats: RegionalStatCountry[] }>('/api/services/app/TenantDashboard/GetRegionalStats'),
  tenantGeneralStats: () => apiGet<TenantGeneralStats>('/api/services/app/TenantDashboard/GetGeneralStats'),
  tenantMemberActivity: () => apiGet<{ memberActivities: MemberActivity[] }>('/api/services/app/TenantDashboard/GetMemberActivity'),
  favorites: () => apiGet<ListResult<FavoriteGroup>>('/api/services/app/MyFavorite/GetMyFavoriteList'),
  favoriteAbilities: (id?: string) =>
    apiGet<ListResult<Ability>>(favoriteAbilitiesQuery(id)),
  saveFavorite: (favorite: FavoriteGroup) =>
    apiPost<void>('/api/services/app/MyFavorite/SaveOrUpdateMyFavorite', favorite),
  deleteFavorite: (id: string) => apiDelete<void>(withQuery('/api/services/app/MyFavorite/DeleteMyFavorite', { Id: id })),
  addFavoriteItem: (myFavoriteId: string | undefined, abilityId: string) =>
    apiPost<void>('/api/services/app/MyFavorite/AddItem', { myFavoriteId, abilityId }),
  removeFavoriteItem: (abilityId: string) => apiDelete<void>(withQuery('/api/services/app/MyFavorite/RemoveItem', { Id: abilityId })),
  subcontractAbilities: (params?: unknown) =>
    apiPost<PageResult<SubcontractAbility>>('/api/services/app/SubcontractAbility/FindList', params),
  subcontractTemplate: () => apiPost<FileDto>('/api/services/app/SubcontractAbility/GetTemplateExcel'),
  uploadSubcontractAbility: (file: File) =>
    apiUpload<UploadSubcontractAbilityOutput>('/AbilityTable/UploadSubcontractAbility', file),
  saveSubcontractExcel: (input: { dataList: SubcontractAbility[]; file?: FileDto; onlySaveNew?: boolean }) =>
    apiPost<void>('/api/services/app/SubcontractAbility/SaveExcelData', input),
  uploadStandardFile: (file: File) => apiUpload<UploadStandardOutput>('/AbilityTable/UploadNewStandard', file),
  uploadStandard: (input?: UploadStandardOutput) =>
    apiPost<void>('/api/services/app/Standard/UploadNewStandard', input),
  downloadFile: async (file: FileDto) => {
    const blob = await apiDownload('/File/DownloadTempFile', {
      fileName: file.fileName,
      fileType: file.fileType,
      fileToken: file.fileToken,
    });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = file.fileName;
    link.click();
    URL.revokeObjectURL(url);
  },
  downloadBinaryFile: async (id: string, fileName: string, contentType: string) => {
    const blob = await apiDownload('/File/DownloadBinaryFile', { id, fileName, contentType });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName;
    link.click();
    URL.revokeObjectURL(url);
  },
  commonEditionsForCombobox: (onlyFreeItems = false) =>
    apiGet<ListResult<SubscribableEditionComboboxItem>>(
      withQuery('/api/services/app/CommonLookup/GetEditionsForCombobox', { onlyFreeItems }),
    ),
  commonFindUsers: (params?: unknown) => apiPost<PageResult<NameValue>>('/api/services/app/CommonLookup/FindUsers', params),
  commonDefaultEditionName: () => apiGet<{ name: string }>('/api/services/app/CommonLookup/GetDefaultEditionName'),
  latestWebLogs: () => apiGet<WebLogOutput>('/api/services/app/WebLog/GetLatestWebLogs'),
  downloadWebLogs: () => apiPost<FileDto>('/api/services/app/WebLog/DownloadWebLogs'),
  timezones: (defaultTimezoneScope = 0) =>
    apiGet<ListResult<NameValue>>(
      withQuery('/api/services/app/Timing/GetTimezones', { DefaultTimezoneScope: defaultTimezoneScope }),
    ),
  timezoneComboboxItems: (params?: { defaultTimezoneScope?: number; selectedTimezoneId?: string }) =>
    apiGet<ComboboxItem[]>(
      withQuery('/api/services/app/Timing/GetTimezoneComboboxItems', { SelectedTimezoneId: params?.selectedTimezoneId }),
    ),
  demoSendAndGetDate: (date?: string) =>
    apiPost<DateToStringOutput>(demoSendAndGetDateQuery(date)),
  demoSendAndGetDateTime: (date?: string) =>
    apiPost<DateToStringOutput>(demoSendAndGetDateTimeQuery(date)),
  demoSendAndGetDateRange: (startDate?: string, endDate?: string) =>
    apiPost<DateToStringOutput>(demoSendAndGetDateRangeQuery({ startDate, endDate })),
  demoCountries: (searchTerm?: string) => apiGet<NameValue[]>(demoCountriesQuery(searchTerm)),
  demoSelectedCountries: (selectedCountries: NameValue[]) =>
    apiPost<NameValue[]>('/api/services/app/DemoUiComponents/SendAndGetSelectedCountries', { selectedCountries }),
  demoSendAndGetValue: (input?: string) =>
    apiPost<StringOutput>(demoSendAndGetValueQuery(input)),
  demoUploadFile: (file: File) => apiUpload<UploadFileOutput[]>('/DemoUiComponents/UploadFiles', file),
  permissions: () => apiGet<ListResult<PermissionItem>>('/api/services/app/Permission/GetAllPermissions'),
  roles: (params?: unknown) =>
    apiGet<ListResult<RoleItem>>(
      withQuery('/api/services/app/Role/GetRoles', {
        Permissions: queryValue(params, 'Permissions', 'permissions'),
        Filter: queryValue(params, 'Filter', 'filter'),
      }),
    ),
  roleForEdit: (id?: number) =>
    apiGet<{ role?: RoleItem; permissions: PermissionItem[]; grantedPermissionNames: string[] }>(
      withQuery('/api/services/app/Role/GetRoleForEdit', { Id: id }),
    ),
  saveRole: (role: RoleItem, grantedPermissionNames: string[]) =>
    apiPost<void>('/api/services/app/Role/CreateOrUpdateRole', { role, grantedPermissionNames }),
  deleteRole: (id: number) => apiDelete<void>(withQuery('/api/services/app/Role/DeleteRole', { Id: id })),
  users: (params?: unknown) =>
    apiGet<PageResult<UserItem>>(
      withQuery('/api/services/app/User/GetUsers', {
        Filter: queryValue(params, 'Filter', 'filter'),
        Permissions: queryValue(params, 'Permissions', 'permissions'),
        Role: queryValue(params, 'Role', 'role'),
        OnlyLockedUsers: queryValue(params, 'OnlyLockedUsers', 'onlyLockedUsers'),
        Sorting: queryValue(params, 'Sorting', 'sorting'),
        MaxResultCount: queryValue(params, 'MaxResultCount', 'maxResultCount'),
        SkipCount: queryValue(params, 'SkipCount', 'skipCount'),
      }),
    ),
  userForEdit: (id?: number) =>
    apiGet<{
      user?: UserItem;
      roles: RoleItem[];
      allOrganizationUnits: OrganizationUnit[];
      allLabs: Laboratory[];
      assignedRoleNames: string[];
      memberedOrganizationUnits: number[];
      memberedLabs: string[];
    }>(withQuery('/api/services/app/User/GetUserForEdit', { Id: id })),
  saveUser: (payload: {
    user: UserItem;
    assignedRoleNames: string[];
    organizationUnits: number[];
    labs: string[];
    sendActivationEmail?: boolean;
    setRandomPassword?: boolean;
  }) => apiPost<void>('/api/services/app/User/CreateOrUpdateUser', payload),
  deleteUser: (id: number) => apiDelete<void>(withQuery('/api/services/app/User/DeleteUser', { Id: id })),
  exportUsers: (params?: unknown) =>
    apiGet<FileDto>(
      withQuery('/api/services/app/User/GetUsersToExcel', {
        Filter: queryValue(params, 'Filter', 'filter'),
        Permissions: queryValue(params, 'Permissions', 'permissions'),
        Role: queryValue(params, 'Role', 'role'),
        OnlyLockedUsers: queryValue(params, 'OnlyLockedUsers', 'onlyLockedUsers'),
        Sorting: queryValue(params, 'Sorting', 'sorting'),
      }),
    ),
  importUsersFromExcel: (file: File) => apiUpload<UserImportOutput>('/Users/ImportFromExcel', file),
  userPermissionsForEdit: (id?: number) =>
    apiGet<{ permissions: PermissionItem[]; grantedPermissionNames: string[] }>(
      withQuery('/api/services/app/User/GetUserPermissionsForEdit', { Id: id }),
    ),
  updateUserPermissions: (id: number, grantedPermissionNames: string[]) =>
    apiPut<void>('/api/services/app/User/UpdateUserPermissions', { id, grantedPermissionNames }),
  resetUserSpecificPermissions: (id: number | undefined) =>
    apiPost<void>('/api/services/app/User/ResetUserSpecificPermissions', { id }),
  unlockUser: (id: number | undefined) => apiPost<void>('/api/services/app/User/UnlockUser', { id }),
  resetUserPassword: (id: number | undefined) => apiPost<void>('/api/services/app/User/ResetUserPassword', { id }),
  currentProfile: () => apiGet<CurrentUserProfile>('/api/services/app/Profile/GetCurrentUserProfileForEdit'),
  updateProfile: (profile: CurrentUserProfile) =>
    apiPut<void>('/api/services/app/Profile/UpdateCurrentUserProfile', profile),
  changePassword: (currentPassword: string, newPassword: string) =>
    apiPost<void>('/api/services/app/Profile/ChangePassword', { currentPassword, newPassword }),
  updateProfilePicture: (payload: UpdateProfilePictureInput) =>
    apiPut<void>('/api/services/app/Profile/UpdateProfilePicture', payload),
  uploadProfilePicture: (file: File) => apiUpload<UploadProfilePictureOutput>('/Profile/UploadProfilePicture', file),
  uploadUeditorImage: (file: File) => apiUpload<UeditorUploadOutput>('/UEditor/UploadImage', file),
  profilePicture: () => apiGet<ProfilePictureOutput>('/api/services/app/Profile/GetProfilePicture'),
  profilePictureById: (profilePictureId?: string) =>
    apiGet<ProfilePictureOutput>(withQuery('/api/services/app/Profile/GetProfilePictureById', { profilePictureId })),
  friendProfilePictureById: (payload: { profilePictureId?: string; userId?: number; tenantId?: number | null }) =>
    apiGet<ProfilePictureOutput>(
      withQuery('/api/services/app/Profile/GetFriendProfilePictureById', {
        ProfilePictureId: payload.profilePictureId,
        UserId: payload.userId,
        TenantId: payload.tenantId,
      }),
    ),
  changeUserLanguage: (languageName: string) =>
    apiPost<void>('/api/services/app/Profile/ChangeLanguage', { languageName }),
  updateGoogleAuthenticatorKey: () =>
    apiPut<GoogleAuthenticatorOutput>('/api/services/app/Profile/UpdateGoogleAuthenticatorKey'),
  disableGoogleAuthenticator: () => apiPost<void>('/api/services/app/Profile/DisableGoogleAuthenticator'),
  sendVerificationSms: (phoneNumber?: string) =>
    apiPost<void>('/api/services/app/Profile/SendVerificationSms', { phoneNumber }),
  verifySmsCode: (phoneNumber?: string, code?: string) =>
    apiPost<void>('/api/services/app/Profile/VerifySmsCode', { phoneNumber, code }),
  prepareCollectedData: () => apiPost<void>('/api/services/app/Profile/PrepareCollectedData'),
  passwordComplexity: () =>
    apiGet<{ setting: { requireDigit: boolean; requiredLength: number } }>(
      '/api/services/app/Profile/GetPasswordComplexitySetting',
    ),
  // Original generated client sends account relation reads as GET query parameters.
  delegatedUsers: (params?: unknown) =>
    apiGet<PageResult<UserDelegation>>(
      withQuery('/api/services/app/UserDelegation/GetDelegatedUsers', {
        MaxResultCount: queryValue(params, 'MaxResultCount', 'maxResultCount'),
        SkipCount: queryValue(params, 'SkipCount', 'skipCount'),
        Sorting: queryValue(params, 'Sorting', 'sorting'),
      }),
    ),
  activeUserDelegations: () =>
    apiGet<UserDelegation[]>('/api/services/app/UserDelegation/GetActiveUserDelegations'),
  delegateNewUser: (payload: { targetUserId: number; startTime: string; endTime: string }) =>
    apiPost<void>('/api/services/app/UserDelegation/DelegateNewUser', payload),
  removeDelegation: (id: number) => apiDelete<void>(withQuery('/api/services/app/UserDelegation/RemoveDelegation', { Id: id })),
  linkToUser: (payload: { tenancyName?: string; usernameOrEmailAddress: string; password: string }) =>
    apiPost<void>('/api/services/app/UserLink/LinkToUser', payload),
  linkedUsers: (params?: unknown) =>
    apiGet<PageResult<LinkedUserItem>>(
      withQuery('/api/services/app/UserLink/GetLinkedUsers', {
        MaxResultCount: queryValue(params, 'MaxResultCount', 'maxResultCount'),
        SkipCount: queryValue(params, 'SkipCount', 'skipCount'),
        Sorting: queryValue(params, 'Sorting', 'sorting'),
      }),
    ),
  recentlyUsedLinkedUsers: () => apiGet<ListResult<LinkedUserItem>>('/api/services/app/UserLink/GetRecentlyUsedLinkedUsers'),
  unlinkUser: (userId?: number, tenantId?: number | null) =>
    apiPost<void>('/api/services/app/UserLink/UnlinkUser', { userId, tenantId }),
  recentUserLoginAttempts: () =>
    apiGet<ListResult<UserLoginAttemptItem>>('/api/services/app/UserLogin/GetRecentUserLoginAttempts'),
  languages: () =>
    apiGet<{ languages: LanguageItem[]; defaultLanguageName: string }>('/api/services/app/Language/GetLanguages'),
  languageForEdit: (id?: number) =>
    apiGet<{ language?: LanguageItem; languageNames: ComboboxItem[]; flags: ComboboxItem[] }>(
      withQuery('/api/services/app/Language/GetLanguageForEdit', { Id: id }),
    ),
  saveLanguage: (language: LanguageItem) =>
    apiPost<void>('/api/services/app/Language/CreateOrUpdateLanguage', { language }),
  deleteLanguage: (id: number) => apiDelete<void>(withQuery('/api/services/app/Language/DeleteLanguage', { Id: id })),
  setDefaultLanguage: (languageName: string) =>
    apiPost<void>('/api/services/app/Language/SetDefaultLanguage', { languageName }),
  // Original generated client sends localization text reads as GET query parameters.
  languageTexts: (params?: unknown) =>
    apiGet<PageResult<LanguageTextItem>>(
      withQuery('/api/services/app/Language/GetLanguageTexts', {
        MaxResultCount: queryValue(params, 'MaxResultCount', 'maxResultCount'),
        SkipCount: queryValue(params, 'SkipCount', 'skipCount'),
        Sorting: queryValue(params, 'Sorting', 'sorting'),
        SourceName: queryValue(params, 'SourceName', 'sourceName') ?? 'CapabilityTable',
        BaseLanguageName: queryValue(params, 'BaseLanguageName', 'baseLanguageName'),
        TargetLanguageName: queryValue(params, 'TargetLanguageName', 'targetLanguageName', 'languageName'),
        TargetValueFilter: queryValue(params, 'TargetValueFilter', 'targetValueFilter'),
        FilterText: queryValue(params, 'FilterText', 'filterText', 'filter'),
      }),
    ),
  updateLanguageText: (text: LanguageTextItem) =>
    apiPut<void>('/api/services/app/Language/UpdateLanguageText', {
      ...text,
      value: text.targetValue ?? '',
    }),
  userNotifications: (params?: unknown) =>
    apiGet<{ totalCount: number; items: NotificationItem[]; unreadCount: number }>(userNotificationsQuery(params)),
  setAllNotificationsAsRead: () => apiPost<void>('/api/services/app/Notification/SetAllNotificationsAsRead'),
  setNotificationAsRead: (id: string) =>
    apiPost<void>('/api/services/app/Notification/SetNotificationAsRead', { id }),
  notificationSettings: () => apiGet<NotificationSettings>('/api/services/app/Notification/GetNotificationSettings'),
  updateNotificationSettings: (settings: NotificationSettings) =>
    apiPut<void>('/api/services/app/Notification/UpdateNotificationSettings', settings),
  deleteNotification: (id: string) => apiDelete<void>(withQuery('/api/services/app/Notification/DeleteNotification', { Id: id })),
  deleteAllUserNotifications: (params: unknown = 'ALL') =>
    apiDelete<void>(deleteAllUserNotificationsQuery(params)),
  caches: () => apiGet<ListResult<CacheItem>>('/api/services/app/Caching/GetAllCaches'),
  clearCache: (id: string) => apiPost<void>('/api/services/app/Caching/ClearCache', { id }),
  clearAllCaches: () => apiPost<void>('/api/services/app/Caching/ClearAllCaches'),
  dynamicParameters: () => apiGet<ListResult<DynamicParameterItem>>('/api/services/app/DynamicParameter/GetAll'),
  addDynamicParameter: (item: DynamicParameterItem) =>
    apiPost<void>('/api/services/app/DynamicParameter/Add', item),
  updateDynamicParameter: (item: DynamicParameterItem) =>
    apiPut<void>('/api/services/app/DynamicParameter/Update', item),
  deleteDynamicParameter: (id: number) => apiDelete<void>(withQuery('/api/services/app/DynamicParameter/Delete', { id })),
  findAllowedInputType: (name?: string) =>
    apiPost<DynamicInputTypeInfo>(findAllowedInputTypeQuery(name)),
  dynamicParameterValues: (dynamicParameterId?: number) =>
    apiGet<ListResult<DynamicParameterValueItem>>(dynamicParameterValuesQuery(dynamicParameterId)),
  addDynamicParameterValue: (item: DynamicParameterValueItem) =>
    apiPost<void>('/api/services/app/DynamicParameterValue/Add', item),
  updateDynamicParameterValue: (item: DynamicParameterValueItem) =>
    apiPut<void>('/api/services/app/DynamicParameterValue/Update', item),
  deleteDynamicParameterValue: (id: number) =>
    apiDelete<void>(withQuery('/api/services/app/DynamicParameterValue/Delete', { id })),
  entityDynamicParameters: (entityFullName?: string) =>
    apiGet<ListResult<EntityDynamicParameterItem>>(entityDynamicParametersQuery(entityFullName)),
  addEntityDynamicParameter: (item: EntityDynamicParameterItem) =>
    apiPost<void>('/api/services/app/EntityDynamicParameter/Add', item),
  updateEntityDynamicParameter: (item: EntityDynamicParameterItem) =>
    apiPut<void>('/api/services/app/EntityDynamicParameter/Update', item),
  deleteEntityDynamicParameter: (id: number) =>
    apiDelete<void>(withQuery('/api/services/app/EntityDynamicParameter/Delete', { id })),
  entityDynamicParameterValues: (params?: EntityDynamicParameterValuesQueryParams) =>
    apiGet<ListResult<EntityDynamicParameterValueItem>>(entityDynamicParameterValuesQuery(params)),
  entityDynamicParameterValueOptions: (params: { entityFullName: string; entityId: string }) =>
    apiGet<EntityDynamicParameterValuesOutput>(entityDynamicParameterValuesQuery(params)),
  addEntityDynamicParameterValue: (item: EntityDynamicParameterValueItem) =>
    apiPost<void>('/api/services/app/EntityDynamicParameterValue/Add', item),
  updateEntityDynamicParameterValue: (item: EntityDynamicParameterValueItem) =>
    apiPut<void>('/api/services/app/EntityDynamicParameterValue/Update', item),
  deleteEntityDynamicParameterValue: (id: number) =>
    apiDelete<void>(withQuery('/api/services/app/EntityDynamicParameterValue/Delete', { id })),
  insertOrUpdateEntityDynamicParameterValues: (items: EntityDynamicParameterValuesInputItem[]) =>
    apiPost<void>('/api/services/app/EntityDynamicParameterValue/InsertOrUpdateAllValues', {
      items,
    }),
  cleanEntityDynamicParameterValues: (params: { entityDynamicParameterId: number; entityId: string }) =>
    apiPost<void>('/api/services/app/EntityDynamicParameterValue/CleanValues', params),
  dynamicAllowedInputTypeNames: () =>
    apiGet<string[]>('/api/services/app/DynamicEntityParameterDefinition/GetAllAllowedInputTypeNames'),
  dynamicEntityNames: () => apiGet<string[]>('/api/services/app/DynamicEntityParameterDefinition/GetAllEntities'),
  webhookSubscriptions: () =>
    apiGet<ListResult<WebhookSubscriptionItem>>('/api/services/app/WebhookSubscription/GetAllSubscriptions'),
  webhookSubscription: (subscriptionId: string) =>
    apiGet<WebhookSubscriptionItem>(withQuery('/api/services/app/WebhookSubscription/GetSubscription', { subscriptionId })),
  addWebhookSubscription: (item: WebhookSubscriptionItem) =>
    apiPost<void>('/api/services/app/WebhookSubscription/AddSubscription', item),
  updateWebhookSubscription: (item: WebhookSubscriptionItem) =>
    apiPut<void>('/api/services/app/WebhookSubscription/UpdateSubscription', item),
  activateWebhookSubscription: (subscriptionId: string, isActive: boolean) =>
    apiPost<void>('/api/services/app/WebhookSubscription/ActivateWebhookSubscription', { subscriptionId, isActive }),
  isWebhookSubscribed: (webhookName: string) =>
    apiPost<boolean>(withQuery('/api/services/app/WebhookSubscription/IsSubscribed', { webhookName })),
  webhookSubscriptionsForWebhook: (webhookName: string) =>
    apiGet<ListResult<WebhookSubscriptionItem>>(
      withQuery('/api/services/app/WebhookSubscription/GetAllSubscriptionsIfFeaturesGranted', { webhookName }),
    ),
  availableWebhooks: () =>
    apiGet<ListResult<WebhookDefinitionItem>>('/api/services/app/WebhookSubscription/GetAllAvailableWebhooks'),
  publishTestWebhook: () => apiPost<string>('/api/services/app/WebhookSubscription/PublishTestWebhook'),
  webhookSendAttempts: (params?: { subscriptionId?: string; skipCount?: number; maxResultCount?: number }) =>
    apiGet<PageResult<WebhookSendAttemptItem>>(
      withQuery('/api/services/app/WebhookSendAttempt/GetAllSendAttempts', {
        SubscriptionId: params?.subscriptionId,
        MaxResultCount: params?.maxResultCount,
        SkipCount: params?.skipCount,
      }),
    ),
  webhookSendAttemptsOfEvent: (id?: string) =>
    apiGet<ListResult<WebhookSendAttemptItem>>(
      withQuery('/api/services/app/WebhookSendAttempt/GetAllSendAttemptsOfWebhookEvent', { Id: id }),
    ),
  resendWebhookAttempt: (sendAttemptId: string) =>
    apiPost<void>(withQuery('/api/services/app/WebhookSendAttempt/Resend', { sendAttemptId })),
  webhookEvent: (id: string) => apiGet<WebhookEventItem>(webhookEventQuery(id)),
  uiManagementSettings: () =>
    apiGet<ThemeSettingsItem[]>('/api/services/app/UiCustomizationSettings/GetUiManagementSettings'),
  changeThemeWithDefaultValues: (themeName: string) =>
    apiPost<void>(changeThemeWithDefaultValuesQuery(themeName)),
  updateUiManagementSettings: (settings: ThemeSettingsItem) =>
    apiPut<void>('/api/services/app/UiCustomizationSettings/UpdateUiManagementSettings', settings),
  updateDefaultUiManagementSettings: (settings: ThemeSettingsItem) =>
    apiPut<void>('/api/services/app/UiCustomizationSettings/UpdateDefaultUiManagementSettings', settings),
  useSystemDefaultSettings: () => apiPost<void>('/api/services/app/UiCustomizationSettings/UseSystemDefaultSettings'),
  hostSettings: () => apiGet<HostSettings>('/api/services/app/HostSettings/GetAllSettings'),
  updateHostSettings: (settings: HostSettings) =>
    apiPut<void>('/api/services/app/HostSettings/UpdateAllSettings', settings),
  hostAbilitySettings: () => apiGet<AbilitySettings>('/api/services/app/HostSettings/GetAbilitySettings'),
  updateHostAbilitySettings: (settings: AbilitySettings) =>
    apiPut<void>('/api/services/app/HostSettings/UpdateAbilitySettings', settings),
  sendHostTestEmail: (emailAddress: string) =>
    apiPost<void>('/api/services/app/HostSettings/SendTestEmail', { emailAddress }),
  tenantSettings: () => apiGet<TenantSettings>('/api/services/app/TenantSettings/GetAllSettings'),
  updateTenantSettings: (settings: TenantSettings) =>
    apiPut<void>('/api/services/app/TenantSettings/UpdateAllSettings', settings),
  sendTenantTestEmail: (emailAddress: string) =>
    apiPost<void>('/api/services/app/TenantSettings/SendTestEmail', { emailAddress }),
  clearTenantLogo: () => apiPost<void>('/api/services/app/TenantSettings/ClearLogo'),
  clearTenantCustomCss: () => apiPost<void>('/api/services/app/TenantSettings/ClearCustomCss'),
  uploadTenantLogo: (file: File) => apiUpload<TenantBrandingUploadResult>('/TenantCustomization/UploadLogo', file),
  uploadTenantCustomCss: (file: File) =>
    apiUpload<TenantBrandingUploadResult>('/TenantCustomization/UploadCustomCss', file),
  chatFriends: () => apiGet<ChatFriendsWithSettings>('/api/services/app/Chat/GetUserChatFriendsWithSettings'),
  chatMessages: (userId: number, tenantId?: number | null, minMessageId?: number) =>
    apiGet<ListResult<ChatMessageItem>>(chatMessagesQuery(userId, tenantId, minMessageId)),
  markChatRead: (userId?: number, tenantId?: number | null) =>
    apiPost<void>('/api/services/app/Chat/MarkAllUnreadMessagesOfUserAsRead', { userId, tenantId }),
  sendChatMessage: (userId: number | undefined, tenantId: number | null | undefined, message: string) =>
    apiPost<ChatMessageItem>('/api/services/app/Chat/SendMessage', { userId, tenantId, message }),
  createFriendshipRequest: (userId: number, tenantId?: number | null) =>
    apiPost<FriendItem>('/api/services/app/Friendship/CreateFriendshipRequest', { userId, tenantId }),
  createFriendshipRequestByUserName: (userName: string, tenancyName = '') =>
    apiPost<FriendItem>('/api/services/app/Friendship/CreateFriendshipRequestByUserName', { userName, tenancyName }),
  blockFriend: (userId: number, tenantId?: number | null) =>
    apiPost<void>('/api/services/app/Friendship/BlockUser', { userId, tenantId }),
  unblockFriend: (userId: number, tenantId?: number | null) =>
    apiPost<void>('/api/services/app/Friendship/UnblockUser', { userId, tenantId }),
  acceptFriendshipRequest: (userId: number, tenantId?: number | null) =>
    apiPost<void>('/api/services/app/Friendship/AcceptFriendshipRequest', { userId, tenantId }),
  tenants: (params?: unknown) =>
    apiGet<PageResult<TenantItem>>(
      withQuery('/api/services/app/Tenant/GetTenants', {
        Filter: queryValue(params, 'Filter', 'filter'),
        SubscriptionEndDateStart: queryValue(params, 'SubscriptionEndDateStart', 'subscriptionEndDateStart'),
        SubscriptionEndDateEnd: queryValue(params, 'SubscriptionEndDateEnd', 'subscriptionEndDateEnd'),
        CreationDateStart: queryValue(params, 'CreationDateStart', 'creationDateStart'),
        CreationDateEnd: queryValue(params, 'CreationDateEnd', 'creationDateEnd'),
        EditionId: queryValue(params, 'EditionId', 'editionId'),
        EditionIdSpecified: queryValue(params, 'EditionIdSpecified', 'editionIdSpecified'),
        Sorting: queryValue(params, 'Sorting', 'sorting'),
        MaxResultCount: queryValue(params, 'MaxResultCount', 'maxResultCount'),
        SkipCount: queryValue(params, 'SkipCount', 'skipCount'),
      }),
    ),
  createTenant: (tenant: TenantItem) => apiPost<void>('/api/services/app/Tenant/CreateTenant', tenant),
  tenantForEdit: (id?: number) => apiGet<TenantItem>(withQuery('/api/services/app/Tenant/GetTenantForEdit', { Id: id })),
  updateTenant: (tenant: TenantItem) => apiPut<void>('/api/services/app/Tenant/UpdateTenant', tenant),
  deleteTenant: (id: number) => apiDelete<void>(withQuery('/api/services/app/Tenant/DeleteTenant', { Id: id })),
  tenantFeatures: (id?: number) =>
    apiGet<{ featureValues: FeatureValueItem[]; features: FeatureItem[] }>(
      withQuery('/api/services/app/Tenant/GetTenantFeaturesForEdit', { Id: id }),
    ),
  updateTenantFeatures: (id: number, featureValues: FeatureValueItem[]) =>
    apiPut<void>('/api/services/app/Tenant/UpdateTenantFeatures', { id, featureValues }),
  resetTenantFeatures: (id: number) => apiPost<void>('/api/services/app/Tenant/ResetTenantSpecificFeatures', { id }),
  unlockTenantAdmin: (id: number) => apiPost<void>('/api/services/app/Tenant/UnlockTenantAdmin', { id }),
  editions: () => apiGet<ListResult<EditionItem>>('/api/services/app/Edition/GetEditions'),
  editionForEdit: (id?: number) =>
    apiGet<{ edition: EditionItem; featureValues: FeatureValueItem[]; features: FeatureItem[] }>(
      withQuery('/api/services/app/Edition/GetEditionForEdit', { Id: id }),
    ),
  createEdition: (edition: EditionItem, featureValues: FeatureValueItem[]) =>
    apiPost<void>('/api/services/app/Edition/CreateEdition', { edition, featureValues }),
  updateEdition: (edition: EditionItem, featureValues: FeatureValueItem[]) =>
    apiPut<void>('/api/services/app/Edition/UpdateEdition', { edition, featureValues }),
  deleteEdition: (id: number) => apiDelete<void>(withQuery('/api/services/app/Edition/DeleteEdition', { Id: id })),
  moveTenantsToEdition: (sourceEditionId: number, targetEditionId: number) =>
    apiPost<void>('/api/services/app/Edition/MoveTenantsToAnotherEdition', { sourceEditionId, targetEditionId }),
  editionComboboxItems: (params?: { selectedEditionId?: number; addAllItem?: boolean; onlyFreeItems?: boolean }) =>
    apiGet<SubscribableEditionComboboxItem[]>(editionComboboxItemsQuery(params)),
  editionTenantCount: (id?: number) => apiGet<number>(withQuery('/api/services/app/Edition/GetTenantCount', { editionId: id })),
  paymentInfo: (params?: { upgradeEditionId?: number; editionPaymentType?: number; paymentPeriodType?: number; recurringPaymentEnabled?: boolean }) =>
    apiGet<{ edition?: EditionItem; additionalPrice: number }>(
      withQuery('/api/services/app/Payment/GetPaymentInfo', { UpgradeEditionId: params?.upgradeEditionId }),
    ),
  createPayment: (params: {
    editionId?: number;
    editionPaymentType?: number;
    paymentPeriodType?: number;
    subscriptionPaymentGatewayType?: number;
    recurringPaymentEnabled?: boolean;
    successUrl?: string;
    errorUrl?: string;
  }) => apiPost<number>('/api/services/app/Payment/CreatePayment', params),
  cancelPayment: (paymentId?: string, gateway?: number) =>
    apiPost<void>('/api/services/app/Payment/CancelPayment', { paymentId, gateway }),
  paymentHistory: (params?: { skipCount?: number; maxResultCount?: number; sorting?: string }) =>
    apiGet<PageResult<SubscriptionPaymentItem>>(
      withQuery('/api/services/app/Payment/GetPaymentHistory', {
        Sorting: params?.sorting,
        MaxResultCount: params?.maxResultCount,
        SkipCount: params?.skipCount,
      }),
    ),
  activePaymentGateways: (recurringPaymentsEnabled?: boolean) =>
    apiGet<PaymentGatewayItem[]>(
      withQuery('/api/services/app/Payment/GetActiveGateways', { RecurringPaymentsEnabled: recurringPaymentsEnabled }),
    ),
  paymentAsync: (id?: number) => apiPost<SubscriptionPaymentItem>(paymentAsyncPath, { id }),
  payment: (id?: number) => apiGet<SubscriptionPaymentItem>(paymentDetailQuery(id)),
  lastCompletedPayment: () => apiGet<SubscriptionPaymentItem | null>('/api/services/app/Payment/GetLastCompletedPayment'),
  completePayment: (id?: number) => apiPost<void>(paymentCallbackQuery('BuyNowSucceed', id)),
  newRegistrationSucceed: (id?: number) => apiPost<void>(paymentCallbackQuery('NewRegistrationSucceed', id)),
  upgradeSucceed: (id?: number) => apiPost<void>(paymentCallbackQuery('UpgradeSucceed', id)),
  extendSucceed: (id?: number) => apiPost<void>(paymentCallbackQuery('ExtendSucceed', id)),
  failPayment: (id?: number) => apiPost<void>(paymentCallbackQuery('PaymentFailed', id)),
  switchBetweenFreeEditions: (upgradeEditionId?: number) =>
    apiPost<void>(paymentEditionSwitchQuery(upgradeEditionId)),
  upgradeSubscriptionCostsLessThenMinAmount: (editionId?: number) =>
    apiPost<void>(upgradeSubscriptionCostsLessThenMinAmountQuery(editionId)),
  hasAnyPayment: () => apiPost<boolean>('/api/services/app/Payment/HasAnyPayment'),
  createInvoice: (subscriptionPaymentId?: number) =>
    apiPost<void>('/api/services/app/Invoice/CreateInvoice', { subscriptionPaymentId }),
  invoiceInfo: (id?: number) => apiGet<InvoiceItem | null>(invoiceInfoQuery(id)),
  enableRecurringPayments: () => apiPost<void>('/api/services/app/Subscription/EnableRecurringPayments'),
  disableRecurringPayments: () => apiPost<void>('/api/services/app/Subscription/DisableRecurringPayments'),
  stripeConfiguration: () => apiGet<{ publishableKey: string }>('/api/services/app/StripePayment/GetConfiguration'),
  createStripePaymentSession: (paymentId?: number, successUrl = '', cancelUrl = '') =>
    apiPost<string>('/api/services/app/StripePayment/CreatePaymentSession', { paymentId, successUrl, cancelUrl }),
  confirmStripePayment: (stripeSessionId?: string) =>
    apiPost<void>('/api/services/app/StripePayment/ConfirmPayment', { stripeSessionId }),
  stripePaymentAsync: (stripeSessionId?: string) =>
    apiPost<SubscriptionPaymentItem | null>(stripePaymentAsyncPath, { stripeSessionId }),
  stripePayment: (stripeSessionId?: string) =>
    apiGet<SubscriptionPaymentItem | null>(stripePaymentDetailQuery(stripeSessionId)),
  stripePaymentResult: (paymentId?: number) => apiGet<StripePaymentResultOutput>(stripePaymentResultQuery(paymentId)),
  payPalConfiguration: () =>
    apiGet<{ clientId: string; demoUsername: string; demoPassword: string }>('/api/services/app/PayPalPayment/GetConfiguration'),
  confirmPayPalPayment: (paymentId?: number, paypalOrderId?: string) =>
    apiPost<void>(payPalConfirmPaymentQuery(paymentId, paypalOrderId)),
  isTenantAvailable: (tenancyName: string) =>
    apiPost<{ state: number; tenantId?: number; serverRootAddress?: string }>('/api/services/app/Account/IsTenantAvailable', {
      tenancyName,
    }),
  registerTenant: (payload: RegisterTenantInput) =>
    apiPost<RegisterTenantOutput>('/api/services/app/TenantRegistration/RegisterTenant', payload),
  editionsForSelect: () => apiGet<EditionsSelectOutput>('/api/services/app/TenantRegistration/GetEditionsForSelect'),
  editionForSelect: (editionId?: number) =>
    apiGet<EditionSelectDto | null>(tenantRegistrationEditionQuery(editionId)),
  registerAccount: (payload: { name?: string; surname?: string; userName?: string; emailAddress?: string; password?: string }) =>
    apiPost<{ canLogin: boolean }>('/api/services/app/Account/Register', payload),
  sendPasswordResetCode: (emailAddress: string) =>
    apiPost<void>('/api/services/app/Account/SendPasswordResetCode', { emailAddress }),
  resetPasswordByCode: (userId?: number, resetCode?: string, password?: string) =>
    apiPost<{ canLogin: boolean; userName: string }>('/api/services/app/Account/ResetPassword', { userId, resetCode, password }),
  sendEmailActivationLink: (emailAddress: string) =>
    apiPost<void>('/api/services/app/Account/SendEmailActivationLink', { emailAddress }),
  activateEmail: (userId?: number, confirmationCode?: string) =>
    apiPost<void>('/api/services/app/Account/ActivateEmail', { userId, confirmationCode }),
  impersonate: (userId: number, tenantId?: number | null) =>
    apiPost<{ impersonationToken: string; tenancyName?: string }>('/api/services/app/Account/Impersonate', { userId, tenantId }),
  delegatedImpersonate: (userDelegationId?: number) =>
    apiPost<{ impersonationToken: string; tenancyName?: string }>('/api/services/app/Account/DelegatedImpersonate', {
      userDelegationId,
    }),
  backToImpersonator: () =>
    apiPost<{ impersonationToken: string; tenancyName?: string }>('/api/services/app/Account/BackToImpersonator'),
  switchToLinkedAccount: (targetUserId: number, targetTenantId?: number | null) =>
    apiPost<{ switchAccountToken: string; tenancyName?: string }>('/api/services/app/Account/SwitchToLinkedAccount', {
      targetUserId,
      targetTenantId,
    }),
  linkUserForLocalReplica: (targetUserId?: number, targetTenantId?: number | null) =>
    apiPost<void>('/api/services/app/Account/LinkUserForLocalReplica', { targetUserId, targetTenantId }),
};
