import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const pageSource = readFileSync(new URL('../src/pages/system/CacheManagementPage.tsx', import.meta.url), 'utf8');
const domainSource = readFileSync(new URL('../src/types/domain.ts', import.meta.url), 'utf8');
const cacheItem = domainSource.match(/export interface CacheItem \{[\s\S]*?\n\}/)?.[0] ?? '';

assert.ok(cacheItem.includes('name: string;'), 'CacheItem should keep the original CacheDto name field.');
assert.ok(!cacheItem.includes('displayName'), 'Original CacheDto does not include displayName.');
assert.ok(!cacheItem.includes('itemCount'), 'Original CacheDto does not include itemCount.');
assert.ok(!cacheItem.includes('lastClearTime'), 'Original CacheDto does not include lastClearTime.');
assert.ok(!pageSource.includes('displayName'), 'Cache page should render the original CacheDto name field.');
assert.ok(!pageSource.includes('itemCount'), 'Cache page should not depend on local cache item counts.');
assert.ok(!pageSource.includes('lastClearTime'), 'Cache page should not depend on local clear timestamps.');
