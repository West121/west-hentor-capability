import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const domainSource = readFileSync(new URL('../src/types/domain.ts', import.meta.url), 'utf8');
const inputBody = domainSource.match(/export interface RegisterTenantInput \{(?<body>[\s\S]*?)\n\}/)?.groups?.body ?? '';

assert.match(
  inputBody,
  /tenancyName: string;/,
  'RegisterTenantInput.tenancyName should be required like the original RegisterTenantInput DTO.',
);
assert.match(
  inputBody,
  /name: string;/,
  'RegisterTenantInput.name should be required like the original RegisterTenantInput DTO.',
);
assert.match(
  inputBody,
  /adminEmailAddress: string;/,
  'RegisterTenantInput.adminEmailAddress should be required like the original RegisterTenantInput DTO.',
);
assert.match(
  inputBody,
  /adminPassword\?: string;/,
  'RegisterTenantInput.adminPassword should stay optional while retaining the original max-32 backend validation.',
);
