import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const domainSource = readFileSync(new URL('../src/types/domain.ts', import.meta.url), 'utf8');
const pageSource = readFileSync(new URL('../src/pages/business/LabPage.tsx', import.meta.url), 'utf8');

assert.match(
  domainSource,
  /export interface Laboratory \{[\s\S]*?code: string;[\s\S]*?name: string;/,
  'Laboratory should require code and name like LaboratoryDto.',
);

assert.match(
  pageSource,
  /name="code"[\s\S]*?label="简称"[\s\S]*?rules=\{\[\{ required: true \}\]\}[\s\S]*?<Input \/>/,
  'LabPage should require code before save.',
);

assert.match(
  pageSource,
  /name="name"[\s\S]*?label="名称"[\s\S]*?rules=\{\[\{ required: true \}\]\}[\s\S]*?<Input \/>/,
  'LabPage should require name before save.',
);
