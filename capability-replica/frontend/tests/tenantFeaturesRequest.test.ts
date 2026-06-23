import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const apiSource = readFileSync(new URL('../src/services/api.ts', import.meta.url), 'utf8');

assert.match(
  apiSource,
  /updateTenantFeatures: \(id: number, featureValues: FeatureValueItem\[\]\) =>[\s\S]*?apiPut<void>\('\/api\/services\/app\/Tenant\/UpdateTenantFeatures', \{ id, featureValues \}\)/,
  'updateTenantFeatures should require a concrete tenant id and featureValues like the original UpdateTenantFeaturesInput DTO.',
);
