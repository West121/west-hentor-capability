# Decompile Inventory

This file records the current reverse-engineering coverage for the local Capability Table package at `/Users/west/Downloads/capability`.

## Backend Assemblies

All local `SgsMineral.*.dll` business assemblies have a corresponding ILSpy project output under `/Users/west/Downloads/capability/.decompiled`.

| Assembly | Decompiled C# files |
| --- | ---: |
| `SgsMineral.CapabilityTable.Application.Shared` | 290 |
| `SgsMineral.CapabilityTable.Application` | 238 |
| `SgsMineral.CapabilityTable.Core.Shared` | 27 |
| `SgsMineral.CapabilityTable.Core` | 171 |
| `SgsMineral.CapabilityTable.EntityFrameworkCore` | 139 |
| `SgsMineral.CapabilityTable.GraphQL` | 21 |
| `SgsMineral.CapabilityTable.Migrator` | 6 |
| `SgsMineral.CapabilityTable.Web.Core` | 82 |
| `SgsMineral.CapabilityTable.Web.Host.Views` | 9 |
| `SgsMineral.CapabilityTable.Web.Host` | 18 |

Coverage check:

- Command: `node scripts/check-decompile-inventory.mjs`
- Result: `firstPartyDlls=10`, `decompiledAssemblies=10`, `missingOrEmpty=[]`.
- Scope: this covers first-party `SgsMineral.CapabilityTable.*.dll` assemblies. Third-party framework and library DLLs are dependency packages, not application feature code.

## Frontend Bundles

The deployed frontend contains 35 top-level Angular JavaScript bundles under `/Users/west/Downloads/capability/wwwroot` and 111 JavaScript files in total when static assets such as UEditor are included. No Angular/TypeScript business source maps are present for those bundles; the only `.map` file found is for `assets/ueditor/third-party/jquery-1.10.2.min.map`.

Practical frontend reconstruction evidence therefore comes from:

- Minified bundle string extraction for generated Angular service proxy routes.
- Component-template snippets from chunk files such as `7-es2015.c2ab7e2927d83b89f1df.js`.
- Runtime route/menu data from static bundle contents and `wwwroot/index.html`.

## Commands Used

```bash
DOTNET_ROOT=/Users/west/Downloads/capability/.tools/dotnet \
  /Users/west/Downloads/capability/.tools/dotnet-tools9/ilspycmd \
  -p -o /Users/west/Downloads/capability/.decompiled/<AssemblyName> \
  -r /Users/west/Downloads/capability \
  /Users/west/Downloads/capability/<AssemblyName>.dll
```

```bash
find /Users/west/Downloads/capability/wwwroot -type f -name '*.map'
```
