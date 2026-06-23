import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const pageSource = readFileSync(new URL('../src/pages/LoginPage.tsx', import.meta.url), 'utf8');

assert.ok(!pageSource.includes('二次验证码 123456'), 'Login page should not tell users to enter a non-original fixed 2FA code.');
assert.ok(!pageSource.includes('本地二次验证码：123456'), 'Login page should not expose a fixed local 2FA code in the alert.');
assert.ok(pageSource.includes('auth.shouldResetPassword'), 'Login page should stop token login when the original password-reset challenge is returned.');
assert.ok(pageSource.includes('useSearchParams'), 'Login page should read the copied MVC returnUrl query parameter.');
assert.ok(pageSource.includes("returnUrl.startsWith('/')"), 'Login page should only honor local returnUrl paths after login.');
assert.ok(pageSource.includes('navigate(nextUrl)'), 'Login page should navigate to the preserved returnUrl after successful login.');
