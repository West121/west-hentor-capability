import type { PaymentGatewayItem } from '../../types/domain';

const gatewayLabels: Record<number, string> = {
  1: 'PayPal',
  2: 'Stripe',
};

export function paymentGatewayOptions(gateways: PaymentGatewayItem[]) {
  return gateways.map((item) => ({
    label: gatewayLabels[item.gatewayType] ?? `Gateway ${item.gatewayType}`,
    value: item.gatewayType,
  }));
}
