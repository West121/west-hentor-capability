import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { paymentCallbackQuery } from '../src/services/requestContracts.ts';

const apiSource = readFileSync(new URL('../src/services/api.ts', import.meta.url), 'utf8');

[
  "apiGet<ListResult<OrganizationUnit>>('/api/services/app/Ability/GetAllUnits')",
  "apiPost<number | null>('/api/services/app/Account/ResolveTenantId'",
  "apiPost<{ impersonationToken: string; tenancyName?: string }>('/api/services/app/Account/DelegatedImpersonate'",
  "apiPost<OrganizationUnit>('/api/services/app/OrganizationUnit/MoveOrganizationUnit'",
  "apiGet<void>(withQuery('/api/TokenAuth/TestNotification'",
  "logoutToken: () => apiGet<void>('/api/TokenAuth/LogOut')",
  'apiPost<{ accessToken: string; refreshToken?: string }>(`/api/TokenAuth/ImpersonatedAuthenticate?impersonationToken=${impersonationToken}`)',
  'apiPost<{ accessToken: string }>(`/api/TokenAuth/LinkedAccountAuthenticate?switchAccountToken=${switchAccountToken}`)',
].forEach((snippet) => {
  assert.ok(apiSource.includes(snippet), `Expected API method for original route: ${snippet}`);
});

assert.equal(
  paymentCallbackQuery('NewRegistrationSucceed', 44),
  '/api/services/app/Payment/NewRegistrationSucceed?paymentId=44',
);

assert.equal(
  paymentCallbackQuery('UpgradeSucceed', 45),
  '/api/services/app/Payment/UpgradeSucceed?paymentId=45',
);

assert.equal(
  paymentCallbackQuery('ExtendSucceed', 46),
  '/api/services/app/Payment/ExtendSucceed?paymentId=46',
);
