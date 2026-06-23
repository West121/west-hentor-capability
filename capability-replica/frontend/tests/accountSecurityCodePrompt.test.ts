import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const pageSource = readFileSync(new URL('../src/pages/account/AccountSecurityPage.tsx', import.meta.url), 'utf8');

assert.ok(!pageSource.includes('本地固定验证码'), 'Account reset/activation prompts should not advertise a non-original fixed code.');
assert.ok(!pageSource.includes("resetCode: '123456'"), 'Password reset form should not prefill the non-original fixed code.');
assert.ok(!pageSource.includes("confirmationCode: '123456'"), 'Email activation form should not prefill the non-original fixed code.');
