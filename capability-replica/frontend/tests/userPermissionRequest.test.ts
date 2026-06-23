import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { strict as assert } from 'node:assert';

const apiSource = readFileSync(join(process.cwd(), 'src/services/api.ts'), 'utf8');

assert.match(
  apiSource,
  /updateUserPermissions:\s*\(id:\s*number,\s*grantedPermissionNames:\s*string\[\]\)\s*=>/,
  'User permission update service should require id like UpdateUserPermissionsInput Range input.',
);
