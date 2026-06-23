import assert from 'node:assert/strict';
import {
  dynamicParameterValuesQuery,
  entityDynamicParametersQuery,
  entityDynamicParameterValuesQuery,
  findAllowedInputTypeQuery,
} from '../src/services/requestContracts.ts';
import { readFileSync } from 'node:fs';

const requestContractsSource = readFileSync(new URL('../src/services/requestContracts.ts', import.meta.url), 'utf8');
const apiSource = readFileSync(new URL('../src/services/api.ts', import.meta.url), 'utf8');

assert.equal(
  findAllowedInputTypeQuery('DATE'),
  '/api/services/app/DynamicParameter/FindAllowedInputType?name=DATE',
);

assert.equal(
  dynamicParameterValuesQuery(7),
  '/api/services/app/DynamicParameterValue/GetAllValuesOfDynamicParameter?Id=7',
);

assert.equal(
  entityDynamicParametersQuery('Capability.RouteEntity'),
  '/api/services/app/EntityDynamicParameter/GetAllParametersOfAnEntity?EntityFullName=Capability.RouteEntity',
);

assert.equal(
  entityDynamicParameterValuesQuery({ entityDynamicParameterId: 9, entityId: 'entity-42' }),
  '/api/services/app/EntityDynamicParameterValue/GetAll?EntityId=entity-42&ParameterId=9',
);

assert.equal(
  entityDynamicParameterValuesQuery({ entityFullName: 'Capability.RouteEntity', entityId: 'entity-42' }),
  '/api/services/app/EntityDynamicParameterValue/GetAllEntityDynamicParameterValues?EntityFullName=Capability.RouteEntity&EntityId=entity-42',
);

assert.match(
  requestContractsSource,
  /export type EntityDynamicParameterValuesQueryParams =[\s\S]*?\{ entityDynamicParameterId\?: number; entityId\?: string \}[\s\S]*?\{ entityFullName: string; entityId: string \};/,
  'GetAllEntityDynamicParameterValues query params should require entityFullName and entityId together like GetAllEntityDynamicParameterValuesInput.',
);

assert.match(
  apiSource,
  /entityDynamicParameterValues: \(params\?: EntityDynamicParameterValuesQueryParams\) =>/,
  'Entity dynamic parameter value API should expose the stricter original query parameter type.',
);
assert.match(
  apiSource,
  /insertOrUpdateEntityDynamicParameterValues: \(items: EntityDynamicParameterValuesInputItem\[\]\) =>[\s\S]*?apiPost<void>\('\/api\/services\/app\/EntityDynamicParameterValue\/InsertOrUpdateAllValues', \{[\s\S]*?items,[\s\S]*?\}\)/,
  'InsertOrUpdateAllValues should use the original Items DTO and void response contract.',
);
assert.match(
  apiSource,
  /cleanEntityDynamicParameterValues: \(params: \{ entityDynamicParameterId: number; entityId: string \}\) =>[\s\S]*?apiPost<void>\('\/api\/services\/app\/EntityDynamicParameterValue\/CleanValues', params\)/,
  'CleanValues should use the original EntityDynamicParameterId and EntityId DTO contract.',
);
