import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const apiSource = readFileSync(new URL('../src/services/api.ts', import.meta.url), 'utf8');

assert.match(
  apiSource,
  /impersonate: \(userId: number, tenantId\?: number \| null\) =>[\s\S]*?apiPost<\{ impersonationToken: string; tenancyName\?: string \}>\('\/api\/services\/app\/Account\/Impersonate', \{ userId, tenantId \}\)/,
  'Account/Impersonate should require userId like ImpersonateInput.',
);

assert.match(
  apiSource,
  /switchToLinkedAccount: \(targetUserId: number, targetTenantId\?: number \| null\) =>[\s\S]*?apiPost<\{ switchAccountToken: string; tenancyName\?: string \}>\('\/api\/services\/app\/Account\/SwitchToLinkedAccount'/,
  'Account/SwitchToLinkedAccount should require targetUserId like SwitchToLinkedAccountInput.',
);

assert.match(
  apiSource,
  /linkToUser: \(payload: \{ tenancyName\?: string; usernameOrEmailAddress: string; password: string \}\) =>[\s\S]*?apiPost<void>\('\/api\/services\/app\/UserLink\/LinkToUser', payload\)/,
  'UserLink/LinkToUser should require usernameOrEmailAddress and password like LinkToUserInput.',
);
