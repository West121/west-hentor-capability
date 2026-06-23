import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const pageSource = readFileSync(new URL('../src/pages/system/UsersPage.tsx', import.meta.url), 'utf8');

assert.ok(pageSource.includes('max: 32'), 'UsersPage create password validation should keep the original 32-character limit.');
assert.ok(pageSource.includes('maxLength={32}'), 'UsersPage create password input should cap entry at the original 32-character limit.');
