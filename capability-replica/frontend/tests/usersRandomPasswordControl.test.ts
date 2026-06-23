import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const pageSource = readFileSync(new URL('../src/pages/system/UsersPage.tsx', import.meta.url), 'utf8');

assert.ok(pageSource.includes('name="setRandomPassword"'), 'UsersPage should expose the original setRandomPassword field.');
assert.ok(pageSource.includes('label="随机密码"'), 'UsersPage should label the random-password option clearly.');
assert.match(
  pageSource,
  /setRandomPassword:\s*values\.setRandomPassword\s*\?\?\s*false/,
  'UsersPage should submit setRandomPassword with CreateOrUpdateUser.',
);
assert.ok(
  pageSource.includes("dependencies={['setRandomPassword']}"),
  'UsersPage password validation should react to the random-password toggle.',
);
