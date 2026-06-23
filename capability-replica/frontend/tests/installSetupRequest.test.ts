import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const domainSource = readFileSync(new URL('../src/types/domain.ts', import.meta.url), 'utf8');
const apiSource = readFileSync(new URL('../src/services/api.ts', import.meta.url), 'utf8');

assert.match(
  domainSource,
  /export interface InstallDto \{[\s\S]*?connectionString: string;[\s\S]*?adminPassword: string;[\s\S]*?webSiteUrl: string;[\s\S]*?defaultLanguage: string;/,
  'InstallDto should require ConnectionString, AdminPassword, WebSiteUrl, and DefaultLanguage like the original InstallDto.',
);

assert.match(
  apiSource,
  /installSetup: \(payload: InstallDto\) => apiPost<void>\('\/api\/services\/app\/Install\/Setup', payload\)/,
  'Install Setup should keep the original InstallDto request body.',
);
