import fs from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();
const sourceRoot = path.resolve(repoRoot, '..');
const decompiledRoot = path.join(sourceRoot, '.decompiled');
const wwwroot = path.join(sourceRoot, 'wwwroot');

function listFiles(dir, predicate) {
  if (!fs.existsSync(dir)) {
    return [];
  }
  return fs.readdirSync(dir, { withFileTypes: true })
    .filter((entry) => entry.isFile())
    .map((entry) => entry.name)
    .filter(predicate)
    .sort();
}

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

const firstPartyDlls = listFiles(
  sourceRoot,
  (name) => name.startsWith('SgsMineral.CapabilityTable.') && name.endsWith('.dll'),
);

const assemblies = firstPartyDlls.map((dll) => {
  const assemblyName = dll.replace(/\.dll$/, '');
  const outputDir = path.join(decompiledRoot, assemblyName);
  const csFiles = walk(outputDir, (file) => file.endsWith('.cs'));
  return {
    assemblyName,
    dll: path.join(sourceRoot, dll),
    decompiledDir: outputDir,
    decompiled: fs.existsSync(outputDir) && csFiles.length > 0,
    csFiles: csFiles.length,
  };
});

const missingOrEmpty = assemblies
  .filter((assembly) => !assembly.decompiled)
  .map((assembly) => assembly.assemblyName);

const frontendJsFiles = walk(wwwroot, (file) => file.endsWith('.js'));
const topLevelAngularBundles = listFiles(wwwroot, (name) => name.endsWith('.js'));
const frontendSourceMaps = walk(wwwroot, (file) => file.endsWith('.map'));
const businessSourceMaps = frontendSourceMaps.filter((file) => !file.includes('/assets/ueditor/third-party/'));

const result = {
  firstPartyDlls: assemblies.length,
  decompiledAssemblies: assemblies.filter((assembly) => assembly.decompiled).length,
  missingOrEmpty,
  assemblies: assemblies.map(({ assemblyName, csFiles }) => ({ assemblyName, csFiles })),
  frontend: {
    topLevelAngularBundles: topLevelAngularBundles.length,
    allJsFiles: frontendJsFiles.length,
    sourceMaps: frontendSourceMaps.length,
    businessSourceMaps: businessSourceMaps.length,
    sourceMapFiles: frontendSourceMaps.map((file) => path.relative(sourceRoot, file)),
  },
};

console.log(JSON.stringify(result, null, 2));

if (missingOrEmpty.length > 0) {
  process.exitCode = 1;
}
