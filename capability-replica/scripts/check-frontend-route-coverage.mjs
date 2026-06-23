import fs from 'node:fs';

const originalAngularRoutes = [
  '/passport/login',
  '/dashboard/v1',
  '/widgets',
  '/widgets/datatable',
  '/style',
  '/style/gridmasonry',
  '/style/typography',
  '/style/colors',
  '/delon',
  '/delon/st',
  '/delon/util',
  '/delon/print',
  '/delon/acl',
  '/delon/guard',
  '/delon/guard/leave',
  '/delon/guard/auth',
  '/delon/guard/admin',
  '/delon/auth',
  '/delon/admin',
  '/delon/cache',
  '/delon/qr',
  '/delon/downfile',
  '/delon/xlsx',
  '/delon/zip',
  '/delon/demo.docx',
  '/delon/小程序标志.zip',
  '/delon/form',
  '/extras',
  '/extras/helpcenter',
  '/extras/settings',
  '/extras/poi',
  '/pro',
  '/pro/form',
  '/pro/form/basic-form',
  '/pro/form/step-form',
  '/pro/form/advanced-form',
  '/pro/list',
  '/pro/list/table-list',
  '/pro/list/basic-list',
  '/pro/list/card-list',
  '/pro/list/articles',
  '/pro/list/projects',
  '/pro/list/applications',
  '/pro/profile',
  '/pro/profile/basic',
  '/pro/profile/advanced',
  '/pro/result',
  '/pro/result/success',
  '/pro/result/fail',
  '/pro/account',
  '/pro/account/center',
  '/pro/account/center/articles',
  '/pro/account/center/projects',
  '/pro/account/center/applications',
  '/pro/account/settings',
  '/pro/account/settings/base',
  '/pro/account/settings/security',
  '/pro/account/settings/binding',
  '/pro/account/settings/notification',
  '/exception/403',
  '/exception/404',
  '/exception/500',
  '/sys/roles',
  '/sys/users',
  '/sys/auditlogs',
  '/sys/orgunits',
  '/sys/standard-update',
  '/business/lab',
  '/business/sampleType',
  '/business/sample',
  '/business/org-ability-property-setting',
  '/ablibity/list',
  '/ablibity/query',
  '/ablibity/subcontract-ability',
  '/logs/ability_history',
  '/logs/audit_log',
  '/my/list',
  '/demo/datatable',
];

function collectAppRoutes() {
  const source = fs.readFileSync('frontend/src/App.tsx', 'utf8');
  return [...source.matchAll(/<Route\s+path="([^"]+)"/g)]
    .map((match) => match[1])
    .filter((route) => route.startsWith('/'));
}

function collectTemplateRoutes() {
  const source = fs.readFileSync('frontend/src/config/templateRoutes.ts', 'utf8');
  return [...source.matchAll(/path:\s*'([^']+)'/g)].map((match) => match[1]);
}

const actualRoutes = new Set([...collectAppRoutes(), ...collectTemplateRoutes()]);
const uniqueOriginalRoutes = [...new Set(originalAngularRoutes)];
const missing = uniqueOriginalRoutes.filter((route) => !actualRoutes.has(route));

console.log(
  JSON.stringify(
    {
      originalAngularRoutes: uniqueOriginalRoutes.length,
      reactRoutes: actualRoutes.size,
      missingCount: missing.length,
      missing,
    },
    null,
    2,
  ),
);
