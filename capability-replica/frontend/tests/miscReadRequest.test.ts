import assert from 'node:assert/strict';
import {
  abilityPropertySettingQuery,
  editionComboboxItemsQuery,
  favoriteAbilitiesQuery,
  favoriteForEditQuery,
  invoiceInfoQuery,
  paymentDetailQuery,
  stripePaymentDetailQuery,
  tenantRegistrationEditionQuery,
  webhookEventQuery,
} from '../src/services/requestContracts.ts';

assert.equal(
  favoriteAbilitiesQuery('favorite-1'),
  '/api/services/app/MyFavorite/GetMyFavoriteAbilityList?MyFavoriteId=favorite-1',
);

assert.equal(
  favoriteForEditQuery('favorite-1'),
  '/api/services/app/MyFavorite/GetMyFavoriteForEdit?Id=favorite-1',
);

assert.equal(
  webhookEventQuery('event-1'),
  '/api/services/app/WebhookEvent/Get?id=event-1',
);

assert.equal(
  invoiceInfoQuery(42),
  '/api/services/app/Invoice/GetInvoiceInfo?Id=42',
);

assert.equal(
  tenantRegistrationEditionQuery(3),
  '/api/services/app/TenantRegistration/GetEdition?editionId=3',
);

assert.equal(
  paymentDetailQuery(42),
  '/api/services/app/Payment/GetPayment?paymentId=42',
);

assert.equal(
  stripePaymentDetailQuery('cs_test_42'),
  '/api/services/app/StripePayment/GetPayment?StripeSessionId=cs_test_42',
);

assert.equal(
  abilityPropertySettingQuery(2),
  '/api/services/app/AbilityProperty/GetOrgAbilitySetting?OrgId=2',
);

assert.equal(
  editionComboboxItemsQuery({ selectedEditionId: 3, addAllItem: true, onlyFreeItems: true }),
  '/api/services/app/Edition/GetEditionComboboxItems?selectedEditionId=3&addAllItem=true&onlyFreeItems=true',
);
