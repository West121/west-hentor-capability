import assert from 'node:assert/strict';
import { tenantBrandingUrls } from '../src/pages/system/tenantBrandingUrls.ts';

assert.deepEqual(tenantBrandingUrls('http://localhost:9901/', 2, 12345), {
  logo: 'http://localhost:9901/TenantCustomization/GetTenantLogo?skin=light&tenantId=2&v=12345',
  customCss: 'http://localhost:9901/TenantCustomization/GetCustomCss?tenantId=2&v=12345',
});

assert.deepEqual(tenantBrandingUrls('http://localhost:9901', undefined, 12345), {
  logo: 'http://localhost:9901/TenantCustomization/GetTenantLogo?skin=light&v=12345',
  customCss: 'http://localhost:9901/TenantCustomization/GetCustomCss?v=12345',
});
