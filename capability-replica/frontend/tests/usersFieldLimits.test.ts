import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const pageSource = readFileSync(new URL('../src/pages/system/UsersPage.tsx', import.meta.url), 'utf8');

assert.match(
  pageSource,
  /name="userName"[\s\S]*?rules=\{\[\{ required: true, max: 256 \}\]\}[\s\S]*?<Input maxLength=\{256\} \/>/,
  'UsersPage userName field should keep the original 256-character limit.',
);
assert.match(
  pageSource,
  /name="name"[\s\S]*?rules=\{\[\{ required: true, max: 64 \}\]\}[\s\S]*?<Input maxLength=\{64\} \/>/,
  'UsersPage name field should keep the original 64-character limit.',
);
assert.match(
  pageSource,
  /name="surname"[\s\S]*?label="姓氏"[\s\S]*?rules=\{\[\{ max: 64 \}\]\}[\s\S]*?<Input maxLength=\{64\} \/>/,
  'UsersPage surname field should keep the original 64-character limit.',
);
assert.match(
  pageSource,
  /name="emailAddress"[\s\S]*?rules=\{\[\{ required: true, type: 'email', max: 256 \}\]\}[\s\S]*?<Input maxLength=\{256\} \/>/,
  'UsersPage emailAddress field should keep the original 256-character limit.',
);
assert.match(
  pageSource,
  /name="phoneNumber"[\s\S]*?label="电话"[\s\S]*?<Input maxLength=\{24\} \/>/,
  'UsersPage phoneNumber field should keep the original 24-character limit.',
);
