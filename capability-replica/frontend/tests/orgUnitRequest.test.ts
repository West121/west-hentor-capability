import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const apiSource = readFileSync(new URL('../src/services/api.ts', import.meta.url), 'utf8');

assert.match(
  apiSource,
  /moveOrgUnit: \(id: number, newParentId\?: number \| null\) =>[\s\S]*?apiPost<OrganizationUnit>\('\/api\/services\/app\/OrganizationUnit\/MoveOrganizationUnit', \{ id, newParentId \}\)/,
  'MoveOrganizationUnit should require id like MoveOrganizationUnitInput.',
);

assert.match(
  apiSource,
  /orgUnitUsers: \(id: number, params\?: \{ sorting\?: string; maxResultCount\?: number; skipCount\?: number \}\) =>[\s\S]*?Id: id/,
  'GetOrganizationUnitUsers should require id like GetOrganizationUnitUsersInput.',
);

assert.match(
  apiSource,
  /orgUnitRoles: \(id: number, params\?: \{ sorting\?: string; maxResultCount\?: number; skipCount\?: number \}\) =>[\s\S]*?Id: id/,
  'GetOrganizationUnitRoles should require id like GetOrganizationUnitRolesInput.',
);

assert.match(
  apiSource,
  /addUsersToOrgUnit: \(organizationUnitId: number, userIds: number\[\]\) =>[\s\S]*?AddUsersToOrganizationUnit', \{ organizationUnitId, userIds \}\)/,
  'AddUsersToOrganizationUnit should require organizationUnitId like UsersToOrganizationUnitInput.',
);

assert.match(
  apiSource,
  /removeUserFromOrgUnit: \(organizationUnitId: number, userId: number\) =>[\s\S]*?UserId: userId,[\s\S]*?OrganizationUnitId: organizationUnitId/,
  'RemoveUserFromOrganizationUnit should require both ids like UserToOrganizationUnitInput.',
);

assert.match(
  apiSource,
  /addRolesToOrgUnit: \(organizationUnitId: number, roleIds: number\[\]\) =>[\s\S]*?AddRolesToOrganizationUnit', \{ organizationUnitId, roleIds \}\)/,
  'AddRolesToOrganizationUnit should require organizationUnitId like RolesToOrganizationUnitInput.',
);

assert.match(
  apiSource,
  /removeRoleFromOrgUnit: \(organizationUnitId: number, roleId: number\) =>[\s\S]*?RoleId: roleId,[\s\S]*?OrganizationUnitId: organizationUnitId/,
  'RemoveRoleFromOrganizationUnit should require both ids like RoleToOrganizationUnitInput.',
);
