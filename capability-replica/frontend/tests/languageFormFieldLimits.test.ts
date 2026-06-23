import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const pageSource = readFileSync(new URL('../src/pages/system/LanguagesPage.tsx', import.meta.url), 'utf8');

assert.match(
  pageSource,
  /name="name"[\s\S]*?label="语言代码"[\s\S]*?rules=\{\[\{ required: true, max: 128 \}\]\}[\s\S]*?<Select[\s\S]*?showSearch[\s\S]*?options=\{languageNameOptions\}/,
  'Language form name should keep the original required 128-character limit and use original language combobox options.',
);
assert.match(
  pageSource,
  /name="icon"[\s\S]*?label="图标"[\s\S]*?rules=\{\[\{ max: 128 \}\]\}[\s\S]*?<Select[\s\S]*?showSearch[\s\S]*?options=\{flagOptions\}/,
  'Language form icon should keep the original 128-character limit and use original flag combobox options.',
);
assert.match(
  pageSource,
  /name="isEnabled"[\s\S]*?label="是否启用"[\s\S]*?valuePropName="checked"[\s\S]*?<Switch \/>/,
  'Language form should use the original ApplicationLanguageEditDto isEnabled field.',
);
assert.match(
  pageSource,
  /name="key"[\s\S]*?label="键"[\s\S]*?<Input disabled maxLength=\{256\} \/>/,
  'Language text key should keep the original 256-character limit even when displayed read-only.',
);
assert.match(
  pageSource,
  /name="targetValue"[\s\S]*?label="目标文本"[\s\S]*?rules=\{\[\{ required: true \}\]\}[\s\S]*?<Input\.TextArea autoSize=\{\{ minRows: 3 \}\} \/>/,
  'Language text value should remain required like the original UpdateLanguageTextInput value.',
);
