import assert from 'node:assert/strict';
import { tokenAuthAuthenticatePayload } from '../src/services/tokenAuthRequest.ts';

const payload = tokenAuthAuthenticatePayload({
  userName: 'admin',
  password: '123qwe',
  twoFactorVerificationCode: '654321',
  rememberClient: true,
});

assert.equal(payload?.userNameOrEmailAddress, 'admin');
assert.equal(payload?.password, 'MTIzcXdl');
assert.equal(payload?.twoFactorVerificationCode, '654321');
assert.equal(payload?.rememberClient, true);
assert.equal(Object.hasOwn(payload ?? {}, 'userName'), false);

assert.equal(tokenAuthAuthenticatePayload(undefined), undefined);
