import assert from 'node:assert/strict';
import { defaultExternalLoginForm, externalProviderOptions } from '../src/pages/account/externalLoginForm.ts';

const providers = [
  { name: 'Facebook', clientId: 'configured-facebook-app', additionalParams: {} },
  { name: 'Google', clientId: 'configured-google-client', additionalParams: { UserInfoEndpoint: 'https://example.local/userinfo' } },
];

assert.deepEqual(externalProviderOptions(providers), [
  { label: 'Facebook / configured-facebook-app', value: 'Facebook' },
  { label: 'Google / configured-google-client', value: 'Google' },
]);

assert.deepEqual(defaultExternalLoginForm(providers), {
  authProvider: 'Google',
  providerKey: 'admin@example.local',
  providerAccessCode: 'local-google-code',
  returnUrl: '/account/security',
  singleSignIn: true,
});

assert.equal(defaultExternalLoginForm([]).authProvider, '');
