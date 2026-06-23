import type { Ability, LabAbility } from '../../types/domain';

export const qualificationOptions = [
  { label: '无', value: '无' },
  { label: 'ALL', value: 'ALL' },
  { label: 'CNAS', value: 'CNAS' },
  { label: 'CMA', value: 'CMA' },
];

export const defaultAbilityPageSize = 10;

// Keeps the React forms compatible with the original ABP dynamic filter payload.
export function buildAbilitySearchPayload(
  values?: Record<string, unknown>,
  orgId?: number,
  maxResultCount = 1000,
  skipCount = 0,
) {
  const formValues = values ?? {};
  const filterItems = ['typeName', 'samplingName', 'testItem', 'standardNo', 'methodName', 'methodEngName', 'labAbility', 'ability']
    .map((field) => ({ field, value: formValues[field] }))
    .filter((item) => item.value !== undefined && item.value !== null && String(item.value).trim() !== '');

  return {
    filter: typeof formValues.filter === 'string' ? formValues.filter : undefined,
    orgId,
    maxResultCount,
    typeName: formValues.typeName,
    samplingName: formValues.samplingName,
    testItem: formValues.testItem,
    standardNo: formValues.standardNo,
    methodName: formValues.methodName,
    methodEngName: formValues.methodEngName,
    filterItems,
    skipCount,
  };
}

export function buildAbilityExportPayload(values?: Record<string, unknown>, orgId?: number) {
  const { maxResultCount: _maxResultCount, skipCount: _skipCount, ...payload } = buildAbilitySearchPayload(values, orgId);
  return payload;
}

export function activeLabAbilities(values?: Ability['labAbilities']) {
  return values?.filter((lab) => lab.isAbility) ?? [];
}

export function labAbilityText(lab: LabAbility) {
  const marks = [lab.hasCnas ? 'CNAS' : '', lab.hasCma ? 'CMA' : ''].filter(Boolean).join('/');
  return marks ? `${lab.code}[${marks}]` : lab.code;
}

export function queryLabAbilityText(values?: Ability['labAbilities']) {
  return activeLabAbilities(values)
    .map((lab) => {
      const marks = `${lab.hasCnas ? 'CNAS' : ''}${lab.hasCma ? 'CMA' : ''}`;
      return marks ? `${lab.code}[${marks}]` : lab.code;
    })
    .map((text) => `${text};`)
    .join('');
}
