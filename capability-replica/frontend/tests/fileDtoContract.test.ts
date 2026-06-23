import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const domainSource = readFileSync(new URL('../src/types/domain.ts', import.meta.url), 'utf8');

assert.match(
  domainSource,
  /export interface FileDto \{[\s\S]*?fileName: string;[\s\S]*?fileType\?: string;[\s\S]*?fileToken: string;/,
  'FileDto should require original FileName/FileToken while keeping FileType optional.',
);
