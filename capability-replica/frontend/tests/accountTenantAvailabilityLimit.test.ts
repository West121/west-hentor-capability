import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const pageSource = readFileSync(new URL('../src/pages/account/AccountSecurityPage.tsx', import.meta.url), 'utf8');

assert.match(
  pageSource,
  /<Card title="租户可用性">[\s\S]*?name="tenancyName"[\s\S]*?rules=\{\[\{ required: true, max: 64 \}\]\}[\s\S]*?<Input maxLength=\{64\} style=\{\{ width: 220 \}\} \/>/,
  'Tenant availability tenancyName field should keep the original IsTenantAvailableInput 64-character limit.',
);
