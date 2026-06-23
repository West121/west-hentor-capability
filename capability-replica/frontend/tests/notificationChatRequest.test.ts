import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { chatMessagesQuery, deleteAllUserNotificationsQuery, userNotificationsQuery } from '../src/services/requestContracts.ts';

const apiSource = readFileSync(new URL('../src/services/api.ts', import.meta.url), 'utf8');

assert.equal(
  userNotificationsQuery({
    state: 'UNREAD',
    startDate: '2026-01-01T00:00:00.000Z',
    endDate: '2026-12-31T23:59:59.999Z',
    maxResultCount: 1,
    skipCount: 0,
  }),
  '/api/services/app/Notification/GetUserNotifications?State=UNREAD&StartDate=2026-01-01T00%3A00%3A00.000Z&EndDate=2026-12-31T23%3A59%3A59.999Z&MaxResultCount=1&SkipCount=0',
);

assert.equal(
  chatMessagesQuery(2, null, 0),
  '/api/services/app/Chat/GetUserChatMessages?UserId=2&MinMessageId=0',
);

assert.match(
  apiSource,
  /chatMessages: \(userId: number, tenantId\?: number \| null, minMessageId\?: number\) =>[\s\S]*?chatMessagesQuery\(userId, tenantId, minMessageId\)/,
  'GetUserChatMessages should require userId like GetUserChatMessagesInput.',
);

assert.match(
  apiSource,
  /createFriendshipRequest: \(userId: number, tenantId\?: number \| null\) =>[\s\S]*?CreateFriendshipRequest', \{ userId, tenantId \}\)/,
  'CreateFriendshipRequest should require userId like CreateFriendshipRequestInput.',
);

assert.match(
  apiSource,
  /blockFriend: \(userId: number, tenantId\?: number \| null\) =>[\s\S]*?BlockUser', \{ userId, tenantId \}\)/,
  'BlockUser should require userId like BlockUserInput.',
);

assert.match(
  apiSource,
  /unblockFriend: \(userId: number, tenantId\?: number \| null\) =>[\s\S]*?UnblockUser', \{ userId, tenantId \}\)/,
  'UnblockUser should require userId like UnblockUserInput.',
);

assert.match(
  apiSource,
  /acceptFriendshipRequest: \(userId: number, tenantId\?: number \| null\) =>[\s\S]*?AcceptFriendshipRequest', \{ userId, tenantId \}\)/,
  'AcceptFriendshipRequest should require userId like AcceptFriendshipRequestInput.',
);

assert.equal(
  deleteAllUserNotificationsQuery({
    state: 'UNREAD',
    startDate: '2999-01-01T00:00:00.000Z',
    endDate: '2999-12-31T23:59:59.999Z',
  }),
  '/api/services/app/Notification/DeleteAllUserNotifications?State=UNREAD&StartDate=2999-01-01T00%3A00%3A00.000Z&EndDate=2999-12-31T23%3A59%3A59.999Z',
);
