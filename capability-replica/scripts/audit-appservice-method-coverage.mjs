import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const decompiledRoot = path.resolve(root, '..', '.decompiled');
const controllerRoot = path.resolve(root, 'backend/src/main/java/com/sgs/capability/controller');
const backendTestRoot = path.resolve(root, 'backend/src/test/java');
const frontendSrcRoot = path.resolve(root, 'frontend/src');
const frontendTestRoot = path.resolve(root, 'frontend/tests');

const appServiceInterfaceRoots = [
  path.join(decompiledRoot, 'SgsMineral.CapabilityTable.Application.Shared'),
  path.join(decompiledRoot, 'SgsMineral.CapabilityTable.Application'),
];

const serviceAliases = new Map([
  ['WebhookAttempt', 'WebhookSendAttempt'],
]);

function walk(dir, predicate, output = []) {
  if (!fs.existsSync(dir)) {
    return output;
  }
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      walk(fullPath, predicate, output);
    } else if (predicate(fullPath)) {
      output.push(fullPath);
    }
  }
  return output;
}

function normalizeServiceName(interfaceName) {
  const serviceName = interfaceName.replace(/^I/, '').replace(/AppService$/, '');
  return serviceAliases.get(serviceName) ?? serviceName;
}

function expectedMethods() {
  const methods = [];
  for (const dir of appServiceInterfaceRoots) {
    for (const file of walk(dir, (candidate) => /[/\\]I\w+AppService\.cs$/.test(candidate))) {
      const source = fs.readFileSync(file, 'utf8');
      const interfaceName = source.match(/public\s+interface\s+(I\w+AppService)\b/)?.[1];
      if (!interfaceName) {
        continue;
      }
      const service = normalizeServiceName(interfaceName);
      for (const line of source.split(/\r?\n/)) {
        if (!line.includes(');')) {
          continue;
        }
        const method = line.match(/^\s*[\w.<>?,\[\]\s]+\s+(\w+)\s*\(/)?.[1];
        if (!method) {
          continue;
        }
        methods.push({
          interface: interfaceName,
          service,
          method,
          route: `/api/services/app/${service}/${method}`,
        });
      }
    }
  }
  return methods;
}

function captureAnnotationArguments(source, atIndex) {
  const start = source.indexOf('(', atIndex);
  if (start < 0) {
    return '';
  }
  let depth = 0;
  for (let i = start; i < source.length; i += 1) {
    if (source[i] === '(') {
      depth += 1;
    }
    if (source[i] === ')') {
      depth -= 1;
      if (depth === 0) {
        return source.slice(start + 1, i);
      }
    }
  }
  return '';
}

function extractStringArguments(annotationArguments) {
  return [...annotationArguments.matchAll(/"([^"]+)"/g)].map((match) => match[1]);
}

function combineRoute(baseRoute, methodRoute) {
  if (methodRoute.startsWith('/api/services/app/')) {
    return methodRoute;
  }
  if (!baseRoute) {
    return methodRoute;
  }
  return `${baseRoute.replace(/\/$/, '')}/${methodRoute.replace(/^\//, '')}`;
}

function backendRoutes() {
  const routes = new Set();
  for (const file of walk(controllerRoot, (candidate) => candidate.endsWith('.java'))) {
    const source = fs.readFileSync(file, 'utf8');
    const classIndex = source.search(/public\s+class\s+\w+/);
    const classPrefix = classIndex >= 0 ? source.slice(0, classIndex) : '';
    const classBases = [...classPrefix.matchAll(/@(RequestMapping)\b/g)].flatMap((match) =>
      extractStringArguments(captureAnnotationArguments(classPrefix, match.index)),
    );
    const baseRoutes = classBases.length ? classBases : [''];
    const classBody = classIndex >= 0 ? source.slice(classIndex) : source;
    for (const match of classBody.matchAll(/@(GetMapping|PostMapping|PutMapping|DeleteMapping|RequestMapping)\b/g)) {
      const methodRoutes = extractStringArguments(captureAnnotationArguments(classBody, match.index));
      for (const baseRoute of baseRoutes) {
        for (const methodRoute of methodRoutes) {
          const route = combineRoute(baseRoute, methodRoute);
          if (route.startsWith('/api/services/app/')) {
            routes.add(route);
          }
        }
      }
    }
  }
  return routes;
}

function textCorpus(files) {
  return files.map((file) => fs.readFileSync(file, 'utf8')).join('\n');
}

const methods = expectedMethods();
const routeSet = backendRoutes();
const frontendSource = textCorpus(walk(frontendSrcRoot, (file) => /\.(ts|tsx)$/.test(file)));
const backendTests = textCorpus(walk(backendTestRoot, (file) => file.endsWith('.java')));
const frontendTests = textCorpus(walk(frontendTestRoot, (file) => /\.(ts|tsx)$/.test(file)));

const rows = methods.map((item) => {
  const serviceMethod = `${item.service}/${item.method}`;
  return {
    ...item,
    backendRoute: routeSet.has(item.route),
    frontendSourceMention: frontendSource.includes(item.route) || frontendSource.includes(serviceMethod),
    backendTestMention: backendTests.includes(item.route) || backendTests.includes(serviceMethod) || backendTests.includes(item.method),
    frontendTestMention: frontendTests.includes(item.route) || frontendTests.includes(serviceMethod) || frontendTests.includes(item.method),
  };
});

function missingBy(field) {
  return rows
    .filter((row) => !row[field])
    .map((row) => `${row.service}.${row.method}`);
}

function countByInterface(field) {
  const counts = new Map();
  for (const row of rows) {
    if (row[field]) {
      continue;
    }
    counts.set(row.interface, (counts.get(row.interface) ?? 0) + 1);
  }
  return [...counts.entries()].sort((left, right) => right[1] - left[1] || left[0].localeCompare(right[0]));
}

const report = {
  expectedAppServiceMethods: rows.length,
  backendRouteMissingCount: missingBy('backendRoute').length,
  frontendSourceMissingCount: missingBy('frontendSourceMention').length,
  backendTestMissingCount: missingBy('backendTestMention').length,
  frontendTestMissingCount: missingBy('frontendTestMention').length,
  backendRouteMissing: missingBy('backendRoute'),
  frontendSourceMissing: missingBy('frontendSourceMention'),
  backendTestMissing: missingBy('backendTestMention'),
  frontendTestMissing: missingBy('frontendTestMention'),
  frontendSourceMissingByInterface: countByInterface('frontendSourceMention'),
  backendTestMissingByInterface: countByInterface('backendTestMention'),
  frontendTestMissingByInterface: countByInterface('frontendTestMention'),
};

console.log(JSON.stringify(report, null, 2));

if (report.backendRouteMissingCount > 0) {
  process.exitCode = 1;
}
