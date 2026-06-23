# Frontend Route Inventory

This inventory is extracted from the deployed Angular bundles under `/Users/west/Downloads/capability/wwwroot` and the bundled menu file `/Users/west/Downloads/capability/wwwroot/assets/tmp/app-data.json`.

Coverage check:

- Command: `node scripts/check-frontend-route-coverage.mjs`
- Result: `originalAngularRoutes=78`, `reactRoutes=96`, `missingCount=0`.
- The React route set is intentionally a superset because it includes compatibility aliases such as `/login`, `/ablibity/subcontract`, and extra replicated administration pages.

Notable route sources:

- `main-es2015.0c7bf50954abfc6c54e6.js` defines the root lazy routes: `dashboard`, `widgets`, `style`, `delon`, `extras`, `pro`, `exception`, `sys`, `business`, `ablibity`, `logs`, `my`, `demo`, and `passport/login`.
- Lazy chunks define module children, for example `7-es2015...js` has `ablibity/list`, `ablibity/query`, and `ablibity/subcontract-ability`; `10-es2015...js` has `demo/datatable`; `17-es2015...js` has `sys/roles`, `sys/users`, `sys/auditlogs`, `sys/orgunits`, and `sys/standard-update`.
- `assets/tmp/app-data.json` exposes 12 visible menu links. All 12 are present in the React replica menu or route table.

Scope note:

- This verifies route reachability against the deployed Angular bundle. Because the deployed frontend has no business TypeScript source maps, component internals are reconstructed from minified bundle behavior and visible UI patterns, not restored from original source.
