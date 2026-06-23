import type { ExternalAuthenticateInput, ExternalLoginProviderInfo } from '../../types/domain';

export function externalProviderOptions(providers: ExternalLoginProviderInfo[]) {
  return providers.map((provider) => ({
    label: `${provider.name} / ${provider.clientId || '-'}`,
    value: provider.name,
  }));
}

export function defaultExternalLoginForm(providers: ExternalLoginProviderInfo[]): ExternalAuthenticateInput {
  const preferredProvider = providers.find((provider) => provider.name === 'Google') ?? providers[0];
  return {
    authProvider: preferredProvider?.name ?? '',
    providerKey: 'admin@example.local',
    providerAccessCode: 'local-google-code',
    returnUrl: '/account/security',
    singleSignIn: true,
  };
}
