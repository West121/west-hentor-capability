import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { strict as assert } from 'node:assert';

const source = readFileSync(join(process.cwd(), 'src/pages/system/SettingsPage.tsx'), 'utf8');

assert.match(
  source,
  /<Input\s+maxLength=\{256\}[\s\S]*?value=\{hostEmail\}/,
  'Host test email input should keep SendTestEmailInput EmailAddress 256-character limit.',
);

assert.match(
  source,
  /<Input\s+maxLength=\{256\}[\s\S]*?value=\{tenantEmail\}/,
  'Tenant test email input should keep SendTestEmailInput EmailAddress 256-character limit.',
);
