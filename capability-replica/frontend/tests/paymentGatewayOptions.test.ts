import assert from 'node:assert/strict';
import { paymentGatewayOptions } from '../src/pages/system/paymentGatewayOptions.ts';

assert.deepEqual(
  paymentGatewayOptions([
    { gatewayType: 1, supportsRecurringPayments: false },
    { gatewayType: 2, supportsRecurringPayments: true },
  ]),
  [
    { label: 'PayPal', value: 1 },
    { label: 'Stripe', value: 2 },
  ],
);

assert.deepEqual(paymentGatewayOptions([{ gatewayType: 99, supportsRecurringPayments: false }]), [
  { label: 'Gateway 99', value: 99 },
]);
