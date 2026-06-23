import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { buildSubcontractSaveInput } from '../src/pages/ability/subcontractImport.ts';

const output = {
  file: {
    fileName: 'subcontract.xlsx',
    fileToken: 'token-1',
  },
  items: [
    {
      labName: 'TDD Lab',
      testCategory: 'TDD Category',
    },
  ],
};

const saveInput = buildSubcontractSaveInput(output);

assert.deepEqual(saveInput, {
  file: output.file,
  dataList: output.items,
});
assert.equal('onlySaveNew' in saveInput, false, 'Original subcontract upload save input does not set OnlySaveNew.');

const pageSource = readFileSync(new URL('../src/pages/ability/SubcontractAbilityPage.tsx', import.meta.url), 'utf8');

assert.equal(pageSource.includes('importOpen'), false, 'Original subcontract upload saves immediately without preview modal.');
assert.equal(pageSource.includes('subcontractTemplate'), false, 'Original subcontract page does not call a SubcontractAbility template AppService.');
