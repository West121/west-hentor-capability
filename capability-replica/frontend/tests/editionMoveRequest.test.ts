import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { strict as assert } from 'node:assert';

const apiSource = readFileSync(join(process.cwd(), 'src/services/api.ts'), 'utf8');

assert.match(
  apiSource,
  /moveTenantsToEdition:\s*\(sourceEditionId:\s*number,\s*targetEditionId:\s*number\)\s*=>/,
  'Edition move service should require both edition ids like MoveTenantsToAnotherEditionDto Range inputs.',
);
