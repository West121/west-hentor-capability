import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const apiSource = readFileSync(new URL('../src/services/api.ts', import.meta.url), 'utf8');

assert.match(
  apiSource,
  /sendPasswordResetCode: \(emailAddress: string\) =>[\s\S]*?apiPost<void>\('\/api\/services\/app\/Account\/SendPasswordResetCode', \{ emailAddress \}\)/,
  'sendPasswordResetCode should require emailAddress like SendPasswordResetCodeInput.',
);

assert.match(
  apiSource,
  /sendEmailActivationLink: \(emailAddress: string\) =>[\s\S]*?apiPost<void>\('\/api\/services\/app\/Account\/SendEmailActivationLink', \{ emailAddress \}\)/,
  'sendEmailActivationLink should require emailAddress like SendEmailActivationLinkInput.',
);
