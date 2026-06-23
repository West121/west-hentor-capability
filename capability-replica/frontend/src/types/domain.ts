// Shared business types copied from the decompiled backend shape.
export interface LabAbility {
  labId?: string;
  code: string;
  hasCnas: boolean;
  hasCma: boolean;
  isAbility: boolean;
}

export interface Ability {
  id?: string;
  orgName?: string;
  orgId?: number;
  typeName?: string;
  typeId?: string;
  samplingName?: string;
  samplingId?: string;
  productCode?: string;
  testItem?: string;
  testItemRemark?: string;
  methodName?: string;
  methodRemark?: string;
  methodEngName?: string;
  gbNo?: string;
  gbRemark?: string;
  isoNo?: string;
  isoRemark?: string;
  gbtNo?: string;
  gbtRemark?: string;
  astmNo?: string;
  astmRemark?: string;
  industryStandardNo?: string;
  industryStandardRemark?: string;
  otherNo?: string;
  otherRemark?: string;
  standardNo?: string;
  cycleWorkingDay?: string;
  testTime?: string;
  testTimeRemark?: string;
  massRequired?: string;
  massRequiredRemark?: string;
  sizeRequired?: string;
  sizeRequiredRemark?: string;
  detectionLimit?: string;
  price?: string;
  priceRemark?: string;
  remark?: string;
  standardNoSgs?: string;
  standardNoSop?: string;
  standardNoOthers?: string;
  standardNoDz?: string;
  isCollected?: boolean;
  labAbilities?: LabAbility[];
}

export interface Laboratory {
  id?: string;
  code: string;
  name: string;
  engName?: string;
  describe?: string;
  leader?: string;
  contactInfo?: string;
  address?: string;
  hasCnas?: boolean;
  hasCms?: boolean;
}

export interface SampleType {
  id?: string;
  displayName: string;
  orgId: number;
  orgName: string;
}

export interface Sample {
  id?: string;
  displayName: string;
  engName?: string;
  alias?: string;
  typeId?: string;
  typeName?: string;
}

export interface OrganizationUnit {
  id?: number;
  parentId?: number | null;
  code?: string;
  displayName: string;
  memberCount?: number;
  roleCount?: number;
}

export interface MyOrgSetting {
  orgId: number;
  orgName: string;
  propertyList: string[];
  lab: string[];
  description?: string;
  isPublic: boolean;
}

export interface PermissionItem {
  name: string;
  displayName: string;
  description?: string;
  parentName?: string | null;
  isGrantedByDefault?: boolean;
  level: number;
}

export interface RoleItem {
  id?: number;
  name?: string;
  displayName: string;
  roleId?: number;
  roleName?: string;
  roleDisplayName?: string;
  isAssigned?: boolean;
  inheritedFromOrganizationUnit?: boolean;
  isStatic?: boolean;
  isDefault?: boolean;
  creationTime?: string;
  addedTime?: string;
  grantedPermissionNames?: string[];
  organizationUnits?: number[];
}

export interface UserItem {
  id?: number;
  name: string;
  surname?: string;
  userName: string;
  emailAddress: string;
  phoneNumber?: string;
  password?: string;
  profilePictureId?: string;
  engName?: string;
  isEmailConfirmed?: boolean;
  isActive?: boolean;
  isLockoutEnabled?: boolean;
  isLockedOut?: boolean;
  shouldChangePasswordOnNextLogin?: boolean;
  creationTime?: string;
  addedTime?: string;
  assignedRoleNames?: string[];
  organizationUnits?: number[];
  labs?: string[];
}

export interface ImportUserRow {
  userName?: string;
  name?: string;
  surname?: string;
  emailAddress?: string;
  phoneNumber?: string;
  password?: string;
  assignedRoleNames?: string[];
  exception?: string;
}

export interface UserImportOutput {
  items: ImportUserRow[];
  file?: FileDto;
  errorFile?: FileDto;
  totalCount: number;
  importedCount: number;
  errorCount: number;
  invalidFile?: boolean;
}

export interface LinkedUserItem {
  id?: number;
  tenantId?: number;
  tenancyName?: string;
  username?: string;
}

export interface UserLoginAttemptItem {
  id?: number;
  userId?: number;
  tenancyName?: string;
  userNameOrEmail?: string;
  clientIpAddress?: string;
  clientName?: string;
  browserInfo?: string;
  result?: string;
  creationTime?: string;
}

export interface CurrentUserProfile {
  id?: number;
  name: string;
  surname?: string;
  userName: string;
  emailAddress: string;
  phoneNumber?: string;
  engName?: string;
  isPhoneNumberConfirmed?: boolean;
  timezone?: string;
  qrCodeSetupImageUrl?: string;
  isGoogleAuthenticatorEnabled?: boolean;
  preferredLanguageName?: string;
}

export interface ProfilePictureOutput {
  profilePicture?: string;
}

export interface UpdateProfilePictureInput {
  fileToken: string;
  x?: number;
  y?: number;
  width?: number;
  height?: number;
}

export interface GoogleAuthenticatorOutput {
  qrCodeSetupImageUrl?: string;
}

export interface UserDelegation {
  id?: number;
  sourceUserId?: number;
  targetUserId?: number;
  targetUserName?: string;
  targetName?: string;
  startTime?: string;
  endTime?: string;
  active?: boolean;
}

export interface LanguageItem {
  id?: number;
  name: string;
  displayName: string;
  icon?: string;
  isDisabled?: boolean;
  isEnabled?: boolean;
  isDefault?: boolean;
  creationTime?: string;
}

export interface LanguageTextItem {
  id?: number;
  sourceName?: string;
  languageName: string;
  key: string;
  baseValue?: string;
  targetValue?: string;
}

export interface NotificationItem {
  id?: string;
  userId?: number;
  notificationName?: string;
  message?: string;
  severity?: string;
  data?: { binaryObjectId?: string; fileToken?: string; fileType?: string; fileName?: string };
  creationTime?: string;
  readState?: number;
  readTime?: string;
}

export interface NotificationSubscription {
  name: string;
  displayName: string;
  isSubscribed: boolean;
}

export interface NotificationSettings {
  receiveNotifications: boolean;
  notifications: NotificationSubscription[];
}

export interface CacheItem {
  name: string;
}

export interface DynamicParameterItem {
  id?: number;
  parameterName: string;
  displayName: string;
  inputType?: string;
  permission?: string;
}

export interface DynamicInputTypeInfo {
  name: string;
  displayName?: string;
  attributes?: Record<string, unknown>;
}

export interface DynamicParameterValueItem {
  id?: number;
  dynamicParameterId?: number;
  parameterName?: string;
  value: string;
}

export interface EntityDynamicParameterItem {
  id?: number;
  entityFullName: string;
  dynamicParameterId?: number;
  parameterName?: string;
  displayName?: string;
}

export interface EntityDynamicParameterValueItem {
  id?: number;
  entityDynamicParameterId?: number;
  entityFullName?: string;
  entityId?: string;
  dynamicParameterId?: number;
  parameterName?: string;
  value: string;
}

export interface EntityDynamicParameterValuesInputItem {
  entityId: string;
  entityDynamicParameterId: number;
  values: string[];
}

export interface EntityDynamicParameterValuesOutputItem {
  entityDynamicParameterId: number;
  parameterName?: string;
  inputType?: DynamicInputTypeInfo;
  selectedValues: string[];
  allValuesInputTypeHas: string[];
}

export interface EntityDynamicParameterValuesOutput {
  items: EntityDynamicParameterValuesOutputItem[];
}

export interface WebhookDefinitionItem {
  name: string;
  displayName?: string;
  description?: string;
}

export interface WebhookSubscriptionItem {
  id?: string;
  webhookUri: string;
  isActive: boolean;
  webhooks: string[];
  headers?: Record<string, string>;
  secret?: string;
  creationTime?: string;
}

export interface WebhookEventItem {
  id?: string;
  webhookName?: string;
  data?: string;
  creationTime?: string;
}

export interface WebhookSendAttemptItem {
  id?: string;
  webhookEventId?: string;
  webhookSubscriptionId?: string;
  webhookUri?: string;
  webhookName?: string;
  data?: string;
  response?: string;
  responseStatusCode?: number;
  creationTime?: string;
  lastModificationTime?: string;
  retryCount?: number;
}

export interface ThemeSettingsItem {
  theme: string;
  isActive?: boolean;
  layout: { layoutType?: string };
  header: {
    desktopFixedHeader?: boolean;
    mobileFixedHeader?: boolean;
    headerSkin?: string;
    minimizeDesktopHeaderType?: string;
    headerMenuArrows?: boolean;
  };
  subHeader: {
    fixedSubHeader?: boolean;
    subheaderStyle?: string;
  };
  menu: {
    position?: string;
    asideSkin?: string;
    fixedAside?: boolean;
    allowAsideMinimizing?: boolean;
    defaultMinimizedAside?: boolean;
    submenuToggle?: string;
    searchActive?: boolean;
  };
  footer: {
    fixedFooter?: boolean;
  };
}

export interface SessionUserInfo {
  id: number;
  name: string;
  surname?: string;
  userName: string;
  emailAddress: string;
  profilePictureId?: string;
}

export interface SessionEditionInfo {
  id?: number;
  displayName?: string;
  trialDayCount?: number;
  monthlyPrice?: number;
  annualPrice?: number;
  isHighestEdition?: boolean;
  isFree?: boolean;
}

export interface SessionTenantInfo {
  id?: number;
  tenancyName?: string;
  name?: string;
  logoId?: string;
  logoFileType?: string;
  customCssId?: string;
  subscriptionEndDateUtc?: string;
  isInTrialPeriod?: boolean;
  subscriptionPaymentType?: number;
  edition?: SessionEditionInfo | null;
  creationTime?: string;
  paymentPeriodType?: number;
  subscriptionDateString?: string;
  creationTimeString?: string;
}

export interface TenantBrandingUploadResult {
  id: string;
  tenantId: number;
  fileType?: string;
}

export interface SessionApplicationInfo {
  version?: string;
  releaseDate?: string;
  currency?: string;
  currencySign?: string;
  allowTenantsToChangeEmailSettings?: boolean;
  userDelegationIsEnabled?: boolean;
  features?: Record<string, boolean>;
  settings?: Record<string, string>;
}

export interface SessionLoginInfo {
  user: SessionUserInfo;
  tenant?: SessionTenantInfo | null;
  application?: SessionApplicationInfo;
  theme?: ThemeSettingsItem;
  permissions: string[];
}

export interface UpdateUserSignInTokenOutput {
  signInToken: string;
  encodedUserId: string;
  encodedTenantId: string;
}

export interface ExternalLoginProviderInfo {
  name: string;
  clientId: string;
  additionalParams?: Record<string, string>;
}

export interface ExternalAuthenticateInput {
  authProvider: string;
  providerKey: string;
  providerAccessCode: string;
  returnUrl?: string;
  singleSignIn?: boolean;
}

export interface ExternalAuthenticateResult {
  accessToken: string;
  encryptedAccessToken?: string;
  expireInSeconds?: number;
  waitingForActivation?: boolean;
  returnUrl?: string;
  refreshToken?: string;
  refreshTokenExpireInSeconds?: number;
}

export interface GeneralSettings {
  timezone?: string;
  timezoneForComparison?: string;
}

export interface EmailSettings {
  defaultFromAddress?: string;
  defaultFromDisplayName?: string;
  smtpHost?: string;
  smtpPort?: number;
  smtpUserName?: string;
  smtpPassword?: string;
  smtpDomain?: string;
  smtpEnableSsl?: boolean;
  smtpUseDefaultCredentials?: boolean;
}

export interface InstallDto {
  connectionString: string;
  adminPassword: string;
  webSiteUrl: string;
  serverUrl?: string;
  defaultLanguage: string;
  smtpSettings?: EmailSettings;
  billInfo?: { legalName?: string; address?: string };
}

export interface AppSettingsJsonDto {
  webSiteUrl?: string;
  serverSiteUrl?: string;
  languages: NameValue[];
}

export interface CheckDatabaseOutput {
  isDatabaseExist: boolean;
}

export interface TenantEmailSettings extends EmailSettings {
  useHostDefaultEmailSettings?: boolean;
}

export interface SessionTimeOutSettings {
  isEnabled?: boolean;
  timeOutSecond?: number;
  showTimeOutNotificationSecond?: number;
  showLockScreenWhenTimedOut?: boolean;
}

export interface PasswordComplexitySetting {
  requireDigit?: boolean;
  requireLowercase?: boolean;
  requireNonAlphanumeric?: boolean;
  requireUppercase?: boolean;
  requiredLength?: number;
}

export interface SecuritySettings {
  allowOneConcurrentLoginPerUser?: boolean;
  useDefaultPasswordComplexitySettings?: boolean;
  passwordComplexity?: PasswordComplexitySetting;
  defaultPasswordComplexity?: PasswordComplexitySetting;
  userLockOut?: {
    isEnabled?: boolean;
    maxFailedAccessAttemptsBeforeLockout?: number;
    defaultAccountLockoutSeconds?: number;
  };
  twoFactorLogin?: {
    isEnabledForApplication?: boolean;
    isEnabled?: boolean;
    isEmailProviderEnabled?: boolean;
    isSmsProviderEnabled?: boolean;
    isRememberBrowserEnabled?: boolean;
    isGoogleAuthenticatorEnabled?: boolean;
  };
}

export interface ExternalLoginProviderSettings {
  facebook?: { appId?: string; appSecret?: string };
  google?: { clientId?: string; clientSecret?: string; userInfoEndpoint?: string };
  twitter?: { consumerKey?: string; consumerSecret?: string };
  microsoft?: { clientId?: string; clientSecret?: string };
}

export interface HostSettings {
  general: GeneralSettings;
  userManagement: {
    isEmailConfirmationRequiredForLogin?: boolean;
    smsVerificationEnabled?: boolean;
    isCookieConsentEnabled?: boolean;
    isQuickThemeSelectEnabled?: boolean;
    useCaptchaOnLogin?: boolean;
    sessionTimeOutSettings?: SessionTimeOutSettings;
  };
  email: EmailSettings;
  tenantManagement: {
    allowSelfRegistration?: boolean;
    isNewRegisteredTenantActiveByDefault?: boolean;
    useCaptchaOnRegistration?: boolean;
    defaultEditionId?: number | null;
  };
  security: SecuritySettings;
  billing?: { legalName?: string; address?: string };
  otherSettings?: { isQuickThemeSelectEnabled?: boolean };
  externalLoginProviderSettings?: ExternalLoginProviderSettings;
}

export interface TenantSettings {
  general?: GeneralSettings;
  userManagement: {
    allowSelfRegistration?: boolean;
    isNewRegisteredUserActiveByDefault?: boolean;
    isEmailConfirmationRequiredForLogin?: boolean;
    useCaptchaOnRegistration?: boolean;
    useCaptchaOnLogin?: boolean;
    isCookieConsentEnabled?: boolean;
    isQuickThemeSelectEnabled?: boolean;
    sessionTimeOutSettings?: SessionTimeOutSettings;
  };
  email: TenantEmailSettings;
  ldap?: {
    isModuleEnabled?: boolean;
    isEnabled?: boolean;
    domain?: string;
    userName?: string;
    password?: string;
  };
  security: SecuritySettings;
  billing?: { legalName?: string; address?: string; taxVatNo?: string };
  otherSettings?: { isQuickThemeSelectEnabled?: boolean };
  externalLoginProviderSettings?: ExternalLoginProviderSettings;
}

export interface AbilitySettings {
  description?: string;
}

export interface FriendItem {
  userId?: number;
  tenantId?: number | null;
  friendUserId: number;
  friendTenantId?: number | null;
  friendUserName: string;
  friendTenancyName?: string;
  friendProfilePictureId?: string;
  unreadMessageCount?: number;
  isOnline?: boolean;
  state?: number;
  creationTime?: string;
}

export interface ChatMessageItem {
  id?: number;
  userId?: number;
  tenantId?: number | null;
  targetUserId?: number;
  targetTenantId?: number | null;
  side?: number;
  readState?: number;
  receiverReadState?: number;
  message?: string;
  creationTime?: string;
  sharedMessageId?: string;
}

export interface ChatFriendsWithSettings {
  serverTime: string;
  friends: FriendItem[];
}

export interface FeatureItem {
  parentName?: string;
  name: string;
  displayName?: string;
  description?: string;
  defaultValue?: string;
  inputType?: Record<string, unknown>;
}

export interface FeatureValueItem {
  name: string;
  value: string;
}

export interface EditionItem {
  id?: number;
  name?: string;
  displayName: string;
  dailyPrice?: number;
  weeklyPrice?: number;
  monthlyPrice?: number;
  annualPrice?: number;
  waitingDayAfterExpire?: number;
  trialDayCount?: number;
  expiringEditionId?: number | null;
  expiringEditionDisplayName?: string;
  isFree?: boolean;
  featureValues?: Record<string, string>;
}

export interface TenantItem {
  id?: number;
  tenancyName: string;
  name: string;
  editionDisplayName?: string;
  connectionString?: string;
  isActive?: boolean;
  creationTime?: string;
  subscriptionEndDateUtc?: string;
  subscriptionPaymentType?: number;
  editionId?: number | null;
  isInTrialPeriod?: boolean;
  adminEmailAddress?: string;
  adminPassword?: string;
  shouldChangePasswordOnNextLogin?: boolean;
  sendActivationEmail?: boolean;
  featureValues?: Record<string, string>;
}

export interface RegisterTenantInput {
  tenancyName: string;
  name: string;
  adminEmailAddress: string;
  adminPassword?: string;
  captchaResponse?: string;
  subscriptionStartType?: number;
  editionId?: number | null;
}

export interface RegisterTenantOutput {
  tenantId: number;
  tenancyName: string;
  name: string;
  userName: string;
  emailAddress: string;
  isTenantActive: boolean;
  isActive: boolean;
  isEmailConfirmationRequired: boolean;
}

export interface EditionSelectDto {
  id: number;
  name?: string;
  displayName: string;
  expiringEditionId?: number | null;
  dailyPrice?: number;
  weeklyPrice?: number;
  monthlyPrice?: number;
  annualPrice?: number;
  trialDayCount?: number;
  waitingDayAfterExpire?: number;
  isFree?: boolean;
  additionalData?: Record<string, Record<string, string>>;
}

export interface FlatFeatureSelectDto {
  parentName?: string;
  name: string;
  displayName: string;
  description?: string;
  defaultValue?: string;
  inputType?: Record<string, unknown>;
  textHtmlColor?: string | null;
}

export interface EditionWithFeaturesDto {
  edition: EditionSelectDto;
  featureValues: NameValue[];
}

export interface EditionsSelectOutput {
  allFeatures: FlatFeatureSelectDto[];
  editionsWithFeatures: EditionWithFeaturesDto[];
}

export interface PaymentGatewayItem {
  gatewayType: number;
  supportsRecurringPayments: boolean;
}

export interface SubscriptionPaymentItem {
  id?: number;
  description?: string;
  gateway?: number;
  gatewayName?: string;
  amount?: number;
  editionId?: number;
  tenantId?: number;
  dayCount?: number;
  paymentPeriodType?: number;
  paymentPeriodTypeName?: string;
  paymentId?: string;
  payerId?: string;
  editionDisplayName?: string;
  invoiceNo?: string;
  status?: number;
  statusName?: string;
  isRecurring?: boolean;
  externalPaymentId?: string;
  successUrl?: string;
  errorUrl?: string;
  editionPaymentType?: number;
  editionPaymentTypeName?: string;
  creationTime?: string;
}

export interface StripePaymentResultOutput {
  paymentDone: boolean;
}

export interface InvoiceItem {
  id?: number;
  subscriptionPaymentId?: number;
  amount?: number;
  editionDisplayName?: string;
  invoiceNo?: string;
  invoiceDate?: string;
  tenantLegalName?: string;
  tenantAddress?: string[];
  tenantTaxNo?: string;
  hostLegalName?: string;
  hostAddress?: string[];
}

export interface AbilityProperty {
  name: string;
  camelCase?: string;
  title: string;
  enabled: boolean;
}

export interface OrgAbilitySetting {
  orgId: number;
  propertyName: string[];
  lab: string[];
  isPublic: boolean;
  description: string;
}

export interface AbilityHistoryItem {
  id?: number;
  entityId?: string;
  changeTime: string;
  changeType: string;
  user: string;
  reason?: string;
  displayName?: string;
  propertyName?: string;
  originalValue?: string;
  newValue?: string;
}

export interface AbilityHistoryDetailItem {
  displayName?: string;
  propertyName?: string;
  originalValue?: string;
  newValue?: string;
}

export interface AuditLog {
  id?: number;
  userId?: number;
  time?: string;
  userName?: string;
  impersonatorTenantId?: number;
  impersonatorUserId?: number;
  serviceName?: string;
  methodName?: string;
  parameters?: string;
  executionTime?: string;
  executionDuration?: number;
  clientIpAddress?: string;
  clientName?: string;
  browserInfo?: string;
  exception?: string;
  customData?: string;
  result?: string;
}

export interface EntityHistoryObjectType {
  name: string;
  value: string;
}

export interface EntityChangeItem {
  id?: number;
  userId?: number;
  userName?: string;
  changeTime?: string;
  entityTypeFullName?: string;
  entityTypeDescription?: string;
  entityId?: string;
  changeType?: number;
  changeTypeName?: string;
  entityChangeSetId?: number;
  tenantId?: number;
}

export interface EntityPropertyChangeItem {
  id?: number;
  entityChangeId?: number;
  newValue?: string;
  originalValue?: string;
  propertyName?: string;
  propertyTypeFullName?: string;
  tenantId?: number;
}

export interface WebLogOutput {
  latestWebLogLines: string[];
}

export interface SubscribableEditionComboboxItem {
  value: string;
  displayText: string;
  isFree?: boolean | null;
  isSelected?: boolean;
}

export interface FavoriteGroup {
  id?: string;
  name: string;
  userId?: number;
  abilityIds?: string[];
}

export interface SubcontractAbility {
  id?: string;
  isExist?: boolean;
  existId?: string;
  labName?: string;
  contactDetails?: string;
  testCategory?: string;
  cmaOrCnas?: string;
  gist?: string;
  appraiser?: string;
  evaluationResult?: string;
  exception?: string;
}

export interface FileDto {
  fileName: string;
  fileType?: string;
  fileToken: string;
}

export interface UploadFileOutput {
  id: string;
  fileName: string;
}

export interface UeditorUploadOutput {
  state: string;
  url: string;
  title: string;
  original: string;
  type: string;
  size: number;
}

export interface UploadProfilePictureOutput {
  fileToken: string;
  fileName?: string;
  fileType?: string;
  width?: number;
  height?: number;
}

export interface ImportAbilityTableDto {
  rowNumber?: number;
  isExist?: boolean;
  existId?: string;
  orgName?: string;
  typeName?: string;
  samplingName?: string;
  productCode?: string;
  testItem?: string;
  testItemRemark?: string;
  methodName?: string;
  methodRemark?: string;
  methodEngName?: string;
  gbNo?: string;
  gbRemark?: string;
  isoNo?: string;
  isoRemark?: string;
  gbtNo?: string;
  gbtRemark?: string;
  astmNo?: string;
  astmRemark?: string;
  industryStandardNo?: string;
  industryStandardRemark?: string;
  otherNo?: string;
  otherRemark?: string;
  cycleWorkingDay?: string;
  testTime?: string;
  testTimeRemark?: string;
  massRequired?: string;
  massRequiredRemark?: string;
  sizeRequired?: string;
  sizeRequiredRemark?: string;
  detectionLimit?: string;
  price?: string;
  priceRemark?: string;
  standardNo?: string;
  remark?: string;
  labData?: Record<string, string>;
  exception?: string;
  standardNoSgs?: string;
  standardNoSop?: string;
  standardNoOthers?: string;
  standardNoDz?: string;
}

export interface AbilityTableUploadOutput {
  abilityTableList: ImportAbilityTableDto[];
  labCodeList: string[];
  file?: FileDto;
  errorFile?: FileDto;
  totalCount?: number;
  errorCount?: number;
  duplicateCount?: number;
}

export interface UploadSubcontractAbilityOutput {
  items: SubcontractAbility[];
  file: FileDto;
  errorFile?: FileDto;
  totalCount?: number;
  errorCount?: number;
  duplicateCount?: number;
}

export interface UpdateStandardNumber {
  old?: string;
  'new'?: string;
  newValue?: string;
  name?: string;
  statu?: string;
  remark?: string;
  matchedCount?: number;
  updatedCount?: number;
  exception?: string;
}

export interface UploadStandardOutput {
  items: UpdateStandardNumber[];
  file: FileDto;
  errorFile?: FileDto;
  totalCount?: number;
  errorCount?: number;
  matchedCount?: number;
  updatedCount?: number;
}

export interface DashboardStatistics {
  abilityCount: number;
  laboratoryCount: number;
  organizationCount: number;
  changeCountInWeek: number;
  changeCountInMonth?: number;
  deleteCountInWeek?: number;
}

export interface DashboardWidgetItem {
  widgetId: string;
  height: number;
  width: number;
  positionX: number;
  positionY: number;
}

export interface DashboardPageItem {
  id: string;
  name: string;
  widgets: DashboardWidgetItem[];
}

export interface DashboardCustomizationItem {
  application?: string;
  dashboardName: string;
  pages: DashboardPageItem[];
}

export interface WidgetFilterOutput {
  id: string;
  name: string;
}

export interface WidgetOutput {
  id: string;
  name: string;
  description?: string;
  filters?: WidgetFilterOutput[];
}

export interface DashboardDefinitionOutput {
  name: string;
  widgets: WidgetOutput[];
}

export interface HostTopStatsData {
  newTenantsCount: number;
  newSubscriptionAmount: number;
  dashboardPlaceholder1: number;
  dashboardPlaceholder2: number;
}

export interface RecentTenant {
  id: number;
  name: string;
  creationTime: string;
}

export interface HostRecentTenantsOutput {
  recentTenantsDayCount: number;
  maxRecentTenantsShownCount: number;
  tenantCreationStartDate: string;
  recentTenants: RecentTenant[];
}

export interface ExpiringTenant {
  tenantName: string;
  remainingDayCount: number;
}

export interface HostExpiringTenantsOutput {
  expiringTenants: ExpiringTenant[];
  subscriptionEndAlertDayCount: number;
  maxExpiringTenantsShownCount: number;
  subscriptionEndDateStart: string;
  subscriptionEndDateEnd: string;
}

export interface IncomeStatistic {
  label: string;
  date: string;
  amount: number;
}

export interface HostIncomeStatisticsOutput {
  incomeStatistics: IncomeStatistic[];
}

export interface TenantEditionStat {
  label: string;
  value: number;
}

export interface HostEditionTenantStatisticsOutput {
  editionStatistics: TenantEditionStat[];
}

export interface SalesSummaryData {
  period: string;
  sales: number;
  profit: number;
}

export interface TenantDashboardData {
  totalProfit: number;
  newFeedbacks: number;
  newOrders: number;
  newUsers: number;
  salesSummary: SalesSummaryData[];
  totalSales: number;
  revenue: number;
  expenses: number;
  growth: number;
  transactionPercent: number;
  newVisitPercent: number;
  bouncePercent: number;
  dailySales: number[];
  profitShares: number[];
}

export interface TenantTopStats {
  totalProfit: number;
  newFeedbacks: number;
  newOrders: number;
  newUsers: number;
}

export interface TenantSalesSummaryOutput {
  totalSales: number;
  revenue: number;
  expenses: number;
  growth: number;
  salesSummary: SalesSummaryData[];
}

export interface MemberActivity {
  name: string;
  earnings: string;
  cases: number;
  closed: number;
  rate: string;
}

export interface RegionalStatCountry {
  countryName: string;
  sales: number;
  change: number[];
  averagePrice: number;
  totalPrice: number;
}

export interface TenantGeneralStats {
  transactionPercent: number;
  newVisitPercent: number;
  bouncePercent: number;
}

export interface OrgCount {
  orgName: string;
  count: number;
}

export interface NameValue {
  name: string;
  value: number | string;
}

export interface ComboboxItem {
  value: string;
  displayText: string;
  isSelected?: boolean;
}

export interface DateToStringOutput {
  dateString?: string;
}

export interface StringOutput {
  output?: string;
}

export interface PageResult<T> {
  totalCount: number;
  items: T[];
}

export interface ListResult<T> {
  items: T[];
}
