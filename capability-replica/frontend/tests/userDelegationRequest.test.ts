import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const apiSource = readFileSync(new URL('../src/services/api.ts', import.meta.url), 'utf8');
const profileSource = readFileSync(new URL('../src/pages/account/ProfilePage.tsx', import.meta.url), 'utf8');

assert.match(
  apiSource,
  /delegateNewUser: \(payload: \{ targetUserId: number; startTime: string; endTime: string \}\) =>/,
  'delegateNewUser payload should require targetUserId, startTime, and endTime like CreateUserDelegationDto.',
);

assert.match(
  profileSource,
  /name="range"[\s\S]*?label="委托时间"[\s\S]*?rules=\{\[\{ required: true \}\]\}[\s\S]*?<RangePicker showTime/,
  'User delegation date range should be required like CreateUserDelegationDto StartTime and EndTime.',
);
