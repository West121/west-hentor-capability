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
  const record = params as Record<string, unknown>;
  for (const key of keys) {
    if (record[key] !== undefined) {
      return record[key];
    }
  }
  return undefined;
};

const withQuery = (path: string, params: Record<string, unknown>) => {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => appendQueryValue(search, key, value));
  const query = search.toString();
  return query ? `${path}?${query}` : path;
};

export const userNotificationsQuery = (params?: unknown) =>
  withQuery('/api/services/app/Notification/GetUserNotifications', {
    State: queryValue(params, 'State', 'state') ?? 'ALL',
    StartDate: queryValue(params, 'StartDate', 'startDate'),
    EndDate: queryValue(params, 'EndDate', 'endDate'),
    MaxResultCount: queryValue(params, 'MaxResultCount', 'maxResultCount'),
    SkipCount: queryValue(params, 'SkipCount', 'skipCount'),
    // Local search keeps the replica UI usable while preserving original names.
    Filter: queryValue(params, 'Filter', 'filter'),
  });

export const deleteAllUserNotificationsQuery = (params?: unknown) =>
  withQuery('/api/services/app/Notification/DeleteAllUserNotifications', {
    State: queryValue(params, 'State', 'state') ?? (typeof params === 'string' ? params : 'ALL'),
    StartDate: queryValue(params, 'StartDate', 'startDate'),
    EndDate: queryValue(params, 'EndDate', 'endDate'),
  });

export const hostDashboardTopStatsQuery = (params?: unknown) =>
  withQuery('/api/services/app/HostDashboard/GetTopStatsData', {
    StartDate: queryValue(params, 'StartDate', 'startDate'),
    EndDate: queryValue(params, 'EndDate', 'endDate'),
  });

export const hostDashboardIncomeStatisticsQuery = (params?: unknown) =>
  withQuery('/api/services/app/HostDashboard/GetIncomeStatistics', {
    IncomeStatisticsDateInterval: queryValue(params, 'IncomeStatisticsDateInterval', 'incomeStatisticsDateInterval'),
    StartDate: queryValue(params, 'StartDate', 'startDate'),
    EndDate: queryValue(params, 'EndDate', 'endDate'),
  });

export const hostDashboardEditionTenantStatisticsQuery = (params?: unknown) =>
  withQuery('/api/services/app/HostDashboard/GetEditionTenantStatistics', {
    StartDate: queryValue(params, 'StartDate', 'startDate'),
    EndDate: queryValue(params, 'EndDate', 'endDate'),
  });

export const tenantDashboardDataQuery = (salesSummaryDatePeriod = 1) =>
  withQuery('/api/services/app/TenantDashboard/GetDashboardData', { SalesSummaryDatePeriod: salesSummaryDatePeriod });

export const tenantSalesSummaryQuery = (salesSummaryDatePeriod = 1) =>
  withQuery('/api/services/app/TenantDashboard/GetSalesSummary', { SalesSummaryDatePeriod: salesSummaryDatePeriod });

export const demoSendAndGetDateQuery = (date?: unknown) =>
  withQuery('/api/services/app/DemoUiComponents/SendAndGetDate', { date });

export const demoSendAndGetDateTimeQuery = (date?: unknown) =>
  withQuery('/api/services/app/DemoUiComponents/SendAndGetDateTime', { date });

export const demoSendAndGetDateRangeQuery = (params?: { startDate?: unknown; endDate?: unknown }) =>
  withQuery('/api/services/app/DemoUiComponents/SendAndGetDateRange', {
    startDate: params?.startDate,
    endDate: params?.endDate,
  });

export const demoCountriesQuery = (searchTerm?: string) =>
  withQuery('/api/services/app/DemoUiComponents/GetCountries', { searchTerm });

export const demoSendAndGetValueQuery = (input?: string) =>
  withQuery('/api/services/app/DemoUiComponents/SendAndGetValue', { input });

export const abilityPropertySettingQuery = (orgId?: number) =>
  withQuery('/api/services/app/AbilityProperty/GetOrgAbilitySetting', { OrgId: orgId });

export const editionComboboxItemsQuery = (params?: {
  selectedEditionId?: number;
  addAllItem?: boolean;
  onlyFreeItems?: boolean;
}) =>
  withQuery('/api/services/app/Edition/GetEditionComboboxItems', {
    selectedEditionId: params?.selectedEditionId,
    addAllItem: params?.addAllItem,
    onlyFreeItems: params?.onlyFreeItems,
  });

export const favoriteAbilitiesQuery = (myFavoriteId?: string) =>
  withQuery('/api/services/app/MyFavorite/GetMyFavoriteAbilityList', { MyFavoriteId: myFavoriteId });

export const favoriteForEditQuery = (id?: string) =>
  withQuery('/api/services/app/MyFavorite/GetMyFavoriteForEdit', { Id: id });

export const webhookEventQuery = (id?: string) =>
  withQuery('/api/services/app/WebhookEvent/Get', { id });

export const invoiceInfoQuery = (id?: number) =>
  withQuery('/api/services/app/Invoice/GetInvoiceInfo', { Id: id });

export const tenantRegistrationEditionQuery = (editionId?: number) =>
  withQuery('/api/services/app/TenantRegistration/GetEdition', { editionId });

export const paymentDetailQuery = (paymentId?: number) =>
  withQuery('/api/services/app/Payment/GetPayment', { paymentId });

export const paymentAsyncPath = '/api/services/app/Payment/GetPaymentAsync';

export type PaymentCallbackAction =
  | 'BuyNowSucceed'
  | 'NewRegistrationSucceed'
  | 'UpgradeSucceed'
  | 'ExtendSucceed'
  | 'PaymentFailed';

export const paymentCallbackPaths: Record<PaymentCallbackAction, string> = {
  BuyNowSucceed: '/api/services/app/Payment/BuyNowSucceed',
  NewRegistrationSucceed: '/api/services/app/Payment/NewRegistrationSucceed',
  UpgradeSucceed: '/api/services/app/Payment/UpgradeSucceed',
  ExtendSucceed: '/api/services/app/Payment/ExtendSucceed',
  PaymentFailed: '/api/services/app/Payment/PaymentFailed',
};

export const paymentCallbackQuery = (
  action: PaymentCallbackAction,
  paymentId?: number,
) => withQuery(paymentCallbackPaths[action], { paymentId });

export const paymentEditionSwitchQuery = (upgradeEditionId?: number) =>
  withQuery('/api/services/app/Payment/SwitchBetweenFreeEditions', { upgradeEditionId });

export const upgradeSubscriptionCostsLessThenMinAmountQuery = (editionId?: number) =>
  withQuery('/api/services/app/Payment/UpgradeSubscriptionCostsLessThenMinAmount', { editionId });

export const stripePaymentDetailQuery = (stripeSessionId?: string) =>
  withQuery('/api/services/app/StripePayment/GetPayment', { StripeSessionId: stripeSessionId });

export const stripePaymentAsyncPath = '/api/services/app/StripePayment/GetPaymentAsync';

export const stripePaymentResultQuery = (paymentId?: number) =>
  withQuery('/api/services/app/StripePayment/GetPaymentResult', { PaymentId: paymentId });

export const payPalConfirmPaymentQuery = (paymentId?: number, paypalOrderId?: string) =>
  withQuery('/api/services/app/PayPalPayment/ConfirmPayment', { paymentId, paypalOrderId });

export const chatMessagesQuery = (userId?: number, tenantId?: number | null, minMessageId?: number) =>
  withQuery('/api/services/app/Chat/GetUserChatMessages', {
    TenantId: tenantId,
    UserId: userId,
    MinMessageId: minMessageId,
  });

export const findAllowedInputTypeQuery = (name?: string) =>
  withQuery('/api/services/app/DynamicParameter/FindAllowedInputType', { name });

export const dynamicParameterValuesQuery = (dynamicParameterId?: number) =>
  dynamicParameterId === undefined || dynamicParameterId === null
    ? '/api/services/app/DynamicParameterValue/GetAll'
    : withQuery('/api/services/app/DynamicParameterValue/GetAllValuesOfDynamicParameter', { Id: dynamicParameterId });

export const entityDynamicParametersQuery = (entityFullName?: string) =>
  entityFullName
    ? withQuery('/api/services/app/EntityDynamicParameter/GetAllParametersOfAnEntity', { EntityFullName: entityFullName })
    : '/api/services/app/EntityDynamicParameter/GetAll';

export type EntityDynamicParameterValuesQueryParams =
  | { entityDynamicParameterId?: number; entityId?: string }
  | { entityFullName: string; entityId: string };

export const entityDynamicParameterValuesQuery = (params?: EntityDynamicParameterValuesQueryParams) => {
  if (params && 'entityFullName' in params) {
    return withQuery('/api/services/app/EntityDynamicParameterValue/GetAllEntityDynamicParameterValues', {
      EntityFullName: params.entityFullName,
      EntityId: params.entityId,
    });
  }
  if (params && (params.entityDynamicParameterId !== undefined || params.entityId)) {
    return withQuery('/api/services/app/EntityDynamicParameterValue/GetAll', {
      EntityId: params.entityId,
      ParameterId: params.entityDynamicParameterId,
    });
  }
  return '/api/services/app/EntityDynamicParameterValue/GetAll';
};

export const changeThemeWithDefaultValuesQuery = (themeName: string) =>
  withQuery('/api/services/app/UiCustomizationSettings/ChangeThemeWithDefaultValues', { themeName });
