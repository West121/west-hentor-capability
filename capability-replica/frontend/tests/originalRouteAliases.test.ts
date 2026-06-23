import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const appSource = readFileSync(new URL('../src/App.tsx', import.meta.url), 'utf8');
const menuSource = readFileSync(new URL('../src/config/menu.tsx', import.meta.url), 'utf8');
const templateRoutesSource = readFileSync(new URL('../src/config/templateRoutes.ts', import.meta.url), 'utf8');

[
  'path="/passport/login"',
  'path="/ablibity/subcontract-ability"',
  'path="/sys/auditlogs"',
  'path="/exception/403"',
  'path="/exception/404"',
  'path="/exception/500"',
  'path="/exception/trigger"',
].forEach((routeSnippet) => {
  assert.ok(appSource.includes(routeSnippet), `Expected original Angular route alias: ${routeSnippet}`);
});

assert.ok(
  appSource.includes('element={<SubcontractAbilityPage />}'),
  'Expected original subcontract route alias to reuse the subcontract ability page',
);

assert.ok(
  appSource.includes('element={<LoginPage />}'),
  'Expected original passport login route alias to reuse the login page',
);

assert.ok(
  appSource.includes('element={<AuditLogPage />}'),
  'Expected original sys/auditlogs route alias to reuse the audit log page',
);

[
  "key: '/ablibity/subcontract-ability'",
  "key: '/logs/audit_log'",
].forEach((menuSnippet) => {
  assert.ok(menuSource.includes(menuSnippet), `Expected menu to use original Angular route: ${menuSnippet}`);
});

assert.ok(
  !menuSource.includes("key: '/sys/auditlogs'"),
  'Expected sys/auditlogs to stay as a route alias instead of replacing the original app-data menu link',
);

[
  '/widgets',
  '/widgets/datatable',
  '/demo/datatable',
  '/style/gridmasonry',
  '/style/typography',
  '/style/colors',
  '/extras/helpcenter',
  '/extras/settings',
  '/extras/poi',
  '/pro/form/basic-form',
  '/pro/form/step-form',
  '/pro/form/advanced-form',
  '/pro/list/table-list',
  '/pro/list/basic-list',
  '/pro/list/card-list',
  '/pro/list/articles',
  '/pro/list/projects',
  '/pro/list/applications',
  '/pro/profile/basic',
  '/pro/profile/advanced',
  '/pro/result/success',
  '/pro/result/fail',
  '/pro/account/center/articles',
  '/pro/account/center/projects',
  '/pro/account/center/applications',
  '/pro/account/settings/base',
  '/pro/account/settings/security',
  '/pro/account/settings/binding',
  '/pro/account/settings/notification',
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
].forEach((routePath) => {
  assert.ok(
    templateRoutesSource.includes(`'${routePath}'`),
    `Expected template route copied from original Angular bundle: ${routePath}`,
  );
});

assert.ok(
  appSource.includes('templateDemoRoutes.map'),
  'Expected React router to mount the copied NG-Alain template routes from one route table',
);

assert.ok(
  appSource.includes('<TemplateDemoPage route={route} />'),
  'Expected copied template routes to render the shared Ant Design template route page',
);

[
  ["path: '/pro/account/center'", "redirectTo: '/pro/account/center/articles'"],
  ["path: '/pro/account/settings'", "redirectTo: '/pro/account/settings/base'"],
].forEach(([routeSnippet, redirectSnippet]) => {
  const routeIndex = templateRoutesSource.indexOf(routeSnippet);
  const redirectIndex = templateRoutesSource.indexOf(redirectSnippet);
  assert.ok(routeIndex >= 0, `Expected copied template parent route: ${routeSnippet}`);
  assert.ok(redirectIndex > routeIndex, `Expected original default child redirect: ${redirectSnippet}`);
});

assert.ok(
  appSource.includes('route.redirectTo ? <Navigate to={route.redirectTo} replace /> : <TemplateDemoPage route={route} />'),
  'Expected copied template parent routes to support original Angular default child redirects',
);
