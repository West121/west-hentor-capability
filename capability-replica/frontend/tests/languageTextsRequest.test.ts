import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const apiSource = readFileSync(new URL('../src/services/api.ts', import.meta.url), 'utf8');

assert.match(
  apiSource,
  /languageTexts: \(params\?: unknown\) =>[\s\S]*?apiGet<PageResult<LanguageTextItem>>\([\s\S]*?withQuery\('\/api\/services\/app\/Language\/GetLanguageTexts'/,
  'Language text reads should use the original generated GET route.',
);

assert.ok(
  apiSource.includes("SourceName: queryValue(params, 'SourceName', 'sourceName') ?? 'CapabilityTable'"),
  'Language text reads should send the original required SourceName.',
);

assert.ok(
  apiSource.includes("TargetLanguageName: queryValue(params, 'TargetLanguageName', 'targetLanguageName', 'languageName')"),
  'Language text reads should map local languageName to original TargetLanguageName.',
);
