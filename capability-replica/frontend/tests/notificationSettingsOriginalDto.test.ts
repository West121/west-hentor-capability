import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const pageSource = readFileSync(new URL('../src/pages/account/NotificationsPage.tsx', import.meta.url), 'utf8');
const domainSource = readFileSync(new URL('../src/types/domain.ts', import.meta.url), 'utf8');
const notificationSettings = domainSource.match(/export interface NotificationSettings \{[\s\S]*?\n\}/)?.[0] ?? '';

assert.ok(notificationSettings.includes('receiveNotifications: boolean;'), 'Original settings output includes receiveNotifications.');
assert.ok(notificationSettings.includes('notifications: NotificationSubscription[];'), 'Original settings output includes notifications.');
assert.ok(!notificationSettings.includes('userId'), 'Original notification settings output does not include userId.');
assert.ok(!notificationSettings.includes('desktopNotifications'), 'Original notification settings output does not include desktopNotifications.');
assert.ok(!notificationSettings.includes('emailNotifications'), 'Original notification settings output does not include emailNotifications.');
assert.ok(!notificationSettings.includes('smsNotifications'), 'Original notification settings output does not include smsNotifications.');
assert.ok(!pageSource.includes('desktopNotifications'), 'Notification settings page should not render a desktop notification switch.');
assert.ok(!pageSource.includes('emailNotifications'), 'Notification settings page should not render an email notification switch.');
assert.ok(!pageSource.includes('smsNotifications'), 'Notification settings page should not render an SMS notification switch.');
