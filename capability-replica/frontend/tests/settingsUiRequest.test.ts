import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { changeThemeWithDefaultValuesQuery } from '../src/services/requestContracts.ts';

const domainSource = readFileSync(new URL('../src/types/domain.ts', import.meta.url), 'utf8');

assert.equal(
  changeThemeWithDefaultValuesQuery('theme2'),
  '/api/services/app/UiCustomizationSettings/ChangeThemeWithDefaultValues?themeName=theme2',
);

assert.match(
  domainSource,
  /export interface HostSettings \{[\s\S]*?general: GeneralSettings;[\s\S]*?userManagement: \{[\s\S]*?email: EmailSettings;[\s\S]*?tenantManagement: \{[\s\S]*?security: SecuritySettings;/,
  'HostSettings should require original HostSettingsEditDto sections.',
);

assert.match(
  domainSource,
  /export interface TenantSettings \{[\s\S]*?userManagement: \{[\s\S]*?email: TenantEmailSettings;[\s\S]*?security: SecuritySettings;/,
  'TenantSettings should require original TenantSettingsEditDto required sections and ValidateHostSettings email section.',
);
