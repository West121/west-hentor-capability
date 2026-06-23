import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const pageSource = readFileSync(new URL('../src/pages/system/OrgUnitsPage.tsx', import.meta.url), 'utf8');

assert.match(
  pageSource,
  /name="displayName"[\s\S]*?label="名称"[\s\S]*?rules=\{\[\{ required: true, max: 128 \}\]\}[\s\S]*?<Input maxLength=\{128\} \/>/,
  'Organization unit displayName field should keep the original 128-character limit.',
);
