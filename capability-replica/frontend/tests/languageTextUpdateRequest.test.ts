import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const apiSource = readFileSync(new URL('../src/services/api.ts', import.meta.url), 'utf8');

assert.ok(
  apiSource.includes("apiPut<void>('/api/services/app/Language/UpdateLanguageText'"),
  'Language text updates should use the original PUT route.',
);
assert.ok(
  apiSource.includes("value: text.targetValue ?? ''"),
  'Language text updates should send the original UpdateLanguageTextInput value field.',
);
