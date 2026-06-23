export function tenantBrandingUrls(baseUrl: string, tenantId: number | null | undefined, version: number | string) {
  const root = baseUrl.replace(/\/+$/, '');
  const logo = new URLSearchParams({ skin: 'light' });
  if (tenantId !== undefined && tenantId !== null) {
    logo.append('tenantId', String(tenantId));
  }
  logo.append('v', String(version));

  const customCss = new URLSearchParams();
  if (tenantId !== undefined && tenantId !== null) {
    customCss.append('tenantId', String(tenantId));
  }
  customCss.append('v', String(version));

  return {
    logo: `${root}/TenantCustomization/GetTenantLogo?${logo.toString()}`,
    customCss: `${root}/TenantCustomization/GetCustomCss?${customCss.toString()}`,
  };
}
