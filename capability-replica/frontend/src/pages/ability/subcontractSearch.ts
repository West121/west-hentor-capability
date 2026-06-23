export const defaultSubcontractPageSize = 10;

// Mirrors the original SubcontractAbility FindList paging input.
export function buildSubcontractSearchPayload(
  values?: Record<string, unknown>,
  current = 1,
  pageSize = defaultSubcontractPageSize,
) {
  const formValues = values ?? {};
  return {
    filter: typeof formValues.filter === 'string' ? formValues.filter : undefined,
    sorting: typeof formValues.sorting === 'string' ? formValues.sorting : undefined,
    maxResultCount: pageSize,
    skipCount: (current - 1) * pageSize,
  };
}
