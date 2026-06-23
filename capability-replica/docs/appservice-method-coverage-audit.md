# AppService Method Coverage Audit

This audit cross-checks decompiled AppService interface methods against the Java backend, React frontend API source, and test corpora.

Source of truth:

- Decompiled interfaces under `/Users/west/Downloads/capability/.decompiled/SgsMineral.CapabilityTable.Application.Shared`
- Decompiled interfaces under `/Users/west/Downloads/capability/.decompiled/SgsMineral.CapabilityTable.Application`

Command:

```bash
node scripts/audit-appservice-method-coverage.mjs
```

Current result:

```json
{
  "expectedAppServiceMethods": 271,
  "backendRouteMissingCount": 0,
  "frontendSourceMissingCount": 0,
  "backendTestMissingCount": 0,
  "frontendTestMissingCount": 0
}
```

Interpretation:

- `backendRouteMissingCount=0`: every decompiled AppService method has a matching Java `/api/services/app/...` route entry point.
- `frontendSourceMissingCount=0`: every decompiled AppService method route is represented in the React frontend source, either as a direct API wrapper or preserved request contract.
- `backendTestMissingCount=0`: every decompiled AppService method route has direct backend test coverage, including smoke coverage for methods previously exercised only indirectly.
- `frontendTestMissingCount=0`: every decompiled AppService method route has direct frontend test coverage, including contract coverage for methods previously exercised only indirectly.

Backend smoke coverage:

- `backend/src/test/java/com/sgs/capability/AppServiceDirectMethodSmokeParityTest.java` directly calls the 34 routes that were previously covered only indirectly.
- The smoke test asserts that each route is a real HTTP entry point and returns an ABP response envelope instead of falling through to 404/405.

Frontend contract coverage:

- `frontend/tests/appserviceMethodCoverage.test.ts` lists the AppService methods that were previously direct-test gaps and asserts their generated `/api/services/app/...` routes are represented in `api.ts` or `requestContracts.ts`.

Recent audit fix:

- Added explicit React request-contract coverage for original `Payment/GetPaymentAsync`, all five `Payment` status callbacks, and `StripePayment/GetPaymentAsync`.
- Kept existing compatibility GET routes such as `Payment/GetPayment` and `StripePayment/GetPayment` for current UI flows.
