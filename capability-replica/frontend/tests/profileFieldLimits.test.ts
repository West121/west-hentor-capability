import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const pageSource = readFileSync(new URL('../src/pages/account/ProfilePage.tsx', import.meta.url), 'utf8');

assert.match(
  pageSource,
  /name="userName"[\s\S]*?<Input disabled maxLength=\{256\} \/>/,
  'Profile userName field should keep the original 256-character limit.',
);
assert.match(
  pageSource,
  /name="name"[\s\S]*?rules=\{\[\{ required: true, max: 64 \}\]\}[\s\S]*?<Input maxLength=\{64\} \/>/,
  'Profile name field should keep the original required 64-character limit.',
);
assert.match(
  pageSource,
  /name="surname"[\s\S]*?rules=\{\[\{ required: true, max: 64 \}\]\}[\s\S]*?<Input maxLength=\{64\} \/>/,
  'Profile surname field should keep the original required 64-character limit.',
);
assert.match(
  pageSource,
  /name="emailAddress"[\s\S]*?rules=\{\[\{ required: true, type: 'email', max: 256 \}\]\}[\s\S]*?<Input maxLength=\{256\} \/>/,
  'Profile emailAddress field should keep the original 256-character limit.',
);
assert.match(
  pageSource,
  /name="phoneNumber"[\s\S]*?label="手机号"[\s\S]*?<Input maxLength=\{24\} \/>/,
  'Profile phoneNumber field should keep the original 24-character limit.',
);
