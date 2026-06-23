import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const pageSource = readFileSync(new URL('../src/pages/account/ProfilePage.tsx', import.meta.url), 'utf8');

assert.ok(!pageSource.includes('短信验证码已发送：123456'), 'Profile page should not expose a non-original fixed SMS code.');
