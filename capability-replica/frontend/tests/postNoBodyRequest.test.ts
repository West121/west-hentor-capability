import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const apiSource = readFileSync(new URL('../src/services/api.ts', import.meta.url), 'utf8');

[
  "apiPost<{ impersonationToken: string; tenancyName?: string }>('/api/services/app/Account/BackToImpersonator')",
  "apiPost<Ability[]>('/api/services/app/Ability/FindAllAblibities')",
  "apiPost<AbilityProperty[]>('/api/services/app/AbilityProperty/AbilityPropertyList')",
  "apiPost<CheckDatabaseOutput>('/api/services/app/Install/CheckDatabase')",
  "apiPost<{ list: Laboratory[] }>('/api/services/app/Laboratory/List')",
  "apiPost<DashboardStatistics>('/api/services/app/Dashboard/Statistics')",
  "apiPost<ListResult<OrgCount>>('/api/services/app/Dashboard/OrgCount')",
  "apiPost<ListResult<NameValue>>('/api/services/app/Dashboard/ChangeCountInWeek')",
  "apiPost<FileDto>('/api/services/app/WebLog/DownloadWebLogs')",
].forEach((snippet) => {
  assert.ok(apiSource.includes(snippet), `Expected original POST no-body API call: ${snippet}`);
});
