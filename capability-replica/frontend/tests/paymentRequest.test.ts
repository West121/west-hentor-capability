import assert from 'node:assert/strict';
import {
  payPalConfirmPaymentQuery,
  paymentAsyncPath,
  paymentCallbackQuery,
  paymentEditionSwitchQuery,
  stripePaymentAsyncPath,
  stripePaymentResultQuery,
  upgradeSubscriptionCostsLessThenMinAmountQuery,
} from '../src/services/requestContracts.ts';

assert.equal(paymentAsyncPath, '/api/services/app/Payment/GetPaymentAsync');

assert.equal(
  paymentCallbackQuery('BuyNowSucceed', 42),
  '/api/services/app/Payment/BuyNowSucceed?paymentId=42',
);

assert.equal(
  paymentCallbackQuery('PaymentFailed', 43),
  '/api/services/app/Payment/PaymentFailed?paymentId=43',
);

assert.equal(
  paymentEditionSwitchQuery(3),
  '/api/services/app/Payment/SwitchBetweenFreeEditions?upgradeEditionId=3',
);

assert.equal(
  upgradeSubscriptionCostsLessThenMinAmountQuery(2),
  '/api/services/app/Payment/UpgradeSubscriptionCostsLessThenMinAmount?editionId=2',
);

assert.equal(
  payPalConfirmPaymentQuery(42, 'PAYPAL-ORDER-42'),
  '/api/services/app/PayPalPayment/ConfirmPayment?paymentId=42&paypalOrderId=PAYPAL-ORDER-42',
);

assert.equal(
  stripePaymentResultQuery(42),
  '/api/services/app/StripePayment/GetPaymentResult?PaymentId=42',
);

assert.equal(stripePaymentAsyncPath, '/api/services/app/StripePayment/GetPaymentAsync');
