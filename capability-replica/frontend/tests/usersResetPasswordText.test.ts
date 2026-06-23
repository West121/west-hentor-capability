import { readFileSync } from 'node:fs';

const source = readFileSync(new URL('../src/pages/system/UsersPage.tsx', import.meta.url), 'utf8');

if (!source.includes('密码会重置为 qazwsxEDCRFV')) {
  throw new Error('UsersPage reset-password confirmation should show the original default password.');
}

if (source.includes('标记下次登录改密') || source.includes('密码已重置为 123qwe')) {
  throw new Error('UsersPage reset-password copy should not describe the old local-only behavior.');
}
