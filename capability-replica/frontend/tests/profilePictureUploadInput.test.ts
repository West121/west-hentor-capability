import assert from 'node:assert/strict';
import { profilePictureUpdateInput } from '../src/pages/account/profilePictureUploadInput.ts';

assert.deepEqual(
  profilePictureUpdateInput({ fileToken: 'token-1', width: 96, height: 80 }),
  { fileToken: 'token-1', x: 0, y: 0, width: 96, height: 80 },
);

assert.deepEqual(
  profilePictureUpdateInput({ fileToken: 'token-2' }),
  { fileToken: 'token-2', x: 0, y: 0, width: 0, height: 0 },
);
