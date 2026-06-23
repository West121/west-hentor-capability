import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const pageSource = readFileSync(new URL('../src/pages/system/TenantsEditionsPage.tsx', import.meta.url), 'utf8');

assert.match(
  pageSource,
  /name="tenancyName"[\s\S]*?label="租户名"[\s\S]*?rules=\{\[\{ required: true, max: 64, pattern: \/\^\[a-zA-Z\]\[a-zA-Z0-9_-\]\{1,\}\$\/ \}\]\}[\s\S]*?<Input maxLength=\{64\} \/>/,
  'Tenant create form tenancyName should keep the original regex and 64-character limit.',
);
assert.match(
  pageSource,
  /name="name"[\s\S]*?label="名称"[\s\S]*?rules=\{\[\{ required: true, max: 128 \}\]\}[\s\S]*?<Input maxLength=\{128\} \/>/,
  'Tenant form name should keep the original 128-character limit.',
);
assert.match(
  pageSource,
  /name="adminEmailAddress"[\s\S]*?label="管理员邮箱"[\s\S]*?rules=\{\[\{ required: true, type: 'email', max: 256 \}\]\}[\s\S]*?<Input maxLength=\{256\} \/>/,
  'Tenant create form adminEmailAddress should keep the original required email 256-character limit.',
);
assert.match(
  pageSource,
  /name="connectionString"[\s\S]*?label="连接字符串"[\s\S]*?<Input\.TextArea maxLength=\{1024\} autoSize=\{\{ minRows: 2, maxRows: 4 \}\} \/>/,
  'Tenant create form connectionString should keep the original 1024-character limit.',
);
