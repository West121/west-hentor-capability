import assert from 'node:assert/strict';
import {
  abilityQuerySearchFields,
  abilityQueryTableFields,
  originalAbilityQueryColumnFields,
} from '../src/pages/ability/abilityQueryColumns.ts';

assert.deepEqual(
  abilityQuerySearchFields.map((field) => field.name),
  ['samplingName', 'testItem', 'standardNo', 'methodName', 'methodEngName', 'labAbility', 'ability'],
);

assert.deepEqual(originalAbilityQueryColumnFields, [
  'samplingName',
  'testItem',
  'price',
  'standardNo',
  'methodName',
  'methodEngName',
  'detectionLimit',
  'cycleWorkingDay',
  'massRequired',
  'sizeRequired',
  'remark',
  'labAbilities',
]);

assert.ok(abilityQueryTableFields.includes('orgName'), 'query table keeps the business-line column');
assert.ok(abilityQueryTableFields.includes('actions'), 'query table keeps the history action column');
assert.equal(abilityQueryTableFields.includes('isCollected'), false);
assert.equal(abilityQueryTableFields.includes('typeName'), false);
