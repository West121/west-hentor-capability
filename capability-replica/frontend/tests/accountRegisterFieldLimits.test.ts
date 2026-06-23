import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const pageSource = readFileSync(new URL('../src/pages/account/AccountSecurityPage.tsx', import.meta.url), 'utf8');

assert.match(
  pageSource,
  /name="userName"[\s\S]*?rules=\{\[\{ required: true, max: 256 \}\]\}[\s\S]*?<Input maxLength=\{256\} \/>/,
  'Account registration userName field should keep the original 256-character limit.',
);
assert.match(
  pageSource,
  /name="emailAddress"[\s\S]*?label="邮箱"[\s\S]*?rules=\{\[\{ required: true, type: 'email', max: 256 \}\]\}[\s\S]*?<Input maxLength=\{256\} \/>/,
  'Account registration emailAddress field should keep the original 256-character limit.',
);
assert.match(
  pageSource,
  /name="name"[\s\S]*?rules=\{\[\{ required: true, max: 64 \}\]\}[\s\S]*?<Input maxLength=\{64\} \/>/,
  'Account registration name field should keep the original 64-character limit.',
);
assert.match(
  pageSource,
  /name="surname"[\s\S]*?rules=\{\[\{ required: true, max: 64 \}\]\}[\s\S]*?<Input maxLength=\{64\} \/>/,
  'Account registration surname field should keep the original required 64-character limit.',
);
