import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const pageSource = readFileSync(new URL('../src/pages/account/AccountSecurityPage.tsx', import.meta.url), 'utf8');

assert.match(
  pageSource,
  /<Card title="密码重置">[\s\S]*?name="emailAddress"[\s\S]*?rules=\{\[\{ required: true, type: 'email', max: 256 \}\]\}[\s\S]*?<Input maxLength=\{256\} \/>/,
  'Password reset email field should keep the original SendPasswordResetCodeInput 256-character limit.',
);
