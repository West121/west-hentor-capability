import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const decompiledRoot = path.resolve(root, '..', '.decompiled');
const controllerRoot = path.resolve(root, 'backend/src/main/java/com/sgs/capability/controller');

const appServiceInterfaceRoots = [
  path.join(decompiledRoot, 'SgsMineral.CapabilityTable.Application.Shared'),
  path.join(decompiledRoot, 'SgsMineral.CapabilityTable.Application'),
];

const serviceAliases = new Map([
  ['WebhookAttempt', 'WebhookSendAttempt'],
]);

function walk(dir, predicate, output = []) {
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

function expectedRoutes() {
  const routes = [];
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

        const methodName = line.match(/^\s*[\w.<>?,\[\]\s]+\s+(\w+)\s*\(/)?.[1];
        if (!methodName) {
          continue;
        }

        routes.push({
          interface: interfaceName,
          method: methodName,
          route: `/api/services/app/${service}/${methodName}`,
        });
      }
    }
  }
  return routes;
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

function requestMethods(annotationName, annotationArguments) {
  if (annotationName === 'GetMapping') {
    return ['GET'];
  }
  if (annotationName === 'PostMapping') {
    return ['POST'];
  }
  if (annotationName === 'PutMapping') {
    return ['PUT'];
  }
  if (annotationName === 'DeleteMapping') {
    return ['DELETE'];
  }

  const methods = [...annotationArguments.matchAll(/RequestMethod\.(GET|POST|PUT|DELETE|PATCH)/g)].map(
    (match) => match[1],
  );
  return methods.length ? methods : ['ANY'];
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

function javaRouteVariants() {
  const variants = [];
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
      const annotationName = match[1];
      const annotationArguments = captureAnnotationArguments(classBody, match.index);
      const methodRoutes = extractStringArguments(annotationArguments);
      if (!methodRoutes.length) {
        continue;
      }

      for (const baseRoute of baseRoutes) {
        for (const methodRoute of methodRoutes) {
          const route = combineRoute(baseRoute, methodRoute);
          if (!route.startsWith('/api/services/app/')) {
            continue;
          }

          for (const method of requestMethods(annotationName, annotationArguments)) {
            variants.push({ method, route, file });
          }
        }
      }
    }
  }
  return variants;
}

const expected = expectedRoutes();
const javaVariants = javaRouteVariants();
const javaRoutePaths = new Set(javaVariants.map((variant) => variant.route));
const javaRouteVariantSet = new Set(javaVariants.map((variant) => `${variant.method} ${variant.route}`));
const interfaces = new Set(expected.map((route) => route.interface));
const missing = expected.filter((route) => !javaRoutePaths.has(route.route));

console.log(
  JSON.stringify(
    {
      interfaces: interfaces.size,
      expectedAppServiceMethods: expected.length,
      javaAppRoutePaths: javaRoutePaths.size,
      javaAppRouteVariants: javaRouteVariantSet.size,
      missingCount: missing.length,
      missing,
    },
    null,
    2,
  ),
);
