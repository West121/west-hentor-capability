import assert from 'node:assert/strict';
import { buildSubcontractSearchPayload, defaultSubcontractPageSize } from '../src/pages/ability/subcontractSearch.ts';

assert.equal(defaultSubcontractPageSize, 10);

assert.deepEqual(buildSubcontractSearchPayload({ filter: '外包', sorting: 'labName asc' }, 3, 25), {
  filter: '外包',
  sorting: 'labName asc',
  maxResultCount: 25,
  skipCount: 50,
});

assert.deepEqual(buildSubcontractSearchPayload({}, 1), {
  filter: undefined,
  sorting: undefined,
  maxResultCount: 10,
  skipCount: 0,
});
