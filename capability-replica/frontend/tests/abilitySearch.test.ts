import assert from 'node:assert/strict';
import {
  buildAbilityExportPayload,
  buildAbilitySearchPayload,
  defaultAbilityPageSize,
  queryLabAbilityText,
} from '../src/pages/ability/abilitySearch.ts';

assert.equal(defaultAbilityPageSize, 10);

assert.deepEqual(buildAbilitySearchPayload({ filter: '铜', typeName: '矿石' }, 2, 25, 50), {
  filter: '铜',
  orgId: 2,
  maxResultCount: 25,
  typeName: '矿石',
  samplingName: undefined,
  testItem: undefined,
  standardNo: undefined,
  methodName: undefined,
  methodEngName: undefined,
  filterItems: [{ field: 'typeName', value: '矿石' }],
  skipCount: 50,
});

assert.deepEqual(buildAbilityExportPayload({ filter: '铜', typeName: '矿石', labAbility: 'TJ', ability: 'ALL' }, 2), {
  filter: '铜',
  orgId: 2,
  typeName: '矿石',
  samplingName: undefined,
  testItem: undefined,
  standardNo: undefined,
  methodName: undefined,
  methodEngName: undefined,
  filterItems: [
    { field: 'typeName', value: '矿石' },
    { field: 'labAbility', value: 'TJ' },
    { field: 'ability', value: 'ALL' },
  ],
});

assert.equal(
  queryLabAbilityText([
    { code: 'TJ', isAbility: true, hasCnas: true, hasCma: true },
    { code: 'SH', isAbility: true, hasCnas: true, hasCma: false },
    { code: 'GZ', isAbility: true, hasCnas: false, hasCma: true },
    { code: 'BJ', isAbility: true, hasCnas: false, hasCma: false },
    { code: 'CD', isAbility: false, hasCnas: true, hasCma: true },
  ]),
  'TJ[CNASCMA];SH[CNAS];GZ[CMA];BJ;',
);
