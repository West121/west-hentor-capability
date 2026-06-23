import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { strict as assert } from 'node:assert';

const source = readFileSync(join(process.cwd(), 'src/pages/account/NotificationsPage.tsx'), 'utf8');

assert.match(
  source,
  /name=\{\[field\.name,\s*'name'\]\}[\s\S]*?hidden[\s\S]*?<Input maxLength=\{96\}/,
  'Notification subscription name input should keep NotificationSubscriptionDto Name 96-character limit.',
);
