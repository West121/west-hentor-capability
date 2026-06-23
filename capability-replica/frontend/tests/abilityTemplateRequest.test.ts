import assert from 'node:assert/strict';
import { abilityTemplateInput } from '../src/pages/ability/abilityTemplateRequest.ts';

assert.deepEqual(abilityTemplateInput(3), { orgId: 3 });
assert.deepEqual(abilityTemplateInput(undefined), {});
