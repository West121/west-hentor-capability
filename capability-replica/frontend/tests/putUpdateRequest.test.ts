import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const apiSource = readFileSync(new URL('../src/services/api.ts', import.meta.url), 'utf8');

[
  "apiPut<UpdateUserSignInTokenOutput>('/api/services/app/Session/UpdateUserSignInToken')",
  "apiPut<void>('/api/services/app/Ability/UpdateAbility'",
  "apiPut<OrganizationUnit>('/api/services/app/OrganizationUnit/UpdateOrganizationUnit'",
  "apiPut<void>('/api/services/app/User/UpdateUserPermissions'",
  "apiPut<void>('/api/services/app/Profile/UpdateCurrentUserProfile'",
  "apiPut<void>('/api/services/app/Profile/UpdateProfilePicture'",
  "apiPut<GoogleAuthenticatorOutput>('/api/services/app/Profile/UpdateGoogleAuthenticatorKey')",
  "apiPut<void>('/api/services/app/Language/UpdateLanguageText'",
  "apiPut<void>('/api/services/app/DynamicParameter/Update'",
  "apiPut<void>('/api/services/app/DynamicParameterValue/Update'",
  "apiPut<void>('/api/services/app/EntityDynamicParameter/Update'",
  "apiPut<void>('/api/services/app/EntityDynamicParameterValue/Update'",
  "apiPut<void>('/api/services/app/WebhookSubscription/UpdateSubscription'",
  "apiPut<void>('/api/services/app/Tenant/UpdateTenant'",
  "apiPut<void>('/api/services/app/Tenant/UpdateTenantFeatures'",
  "apiPut<void>('/api/services/app/Edition/UpdateEdition'",
].forEach((snippet) => {
  assert.ok(apiSource.includes(snippet), `Expected original PUT update API call: ${snippet}`);
});
