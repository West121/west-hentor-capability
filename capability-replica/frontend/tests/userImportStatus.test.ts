import assert from 'node:assert/strict';
import { userImportStatus } from '../src/pages/system/userImportStatus.ts';

assert.deepEqual(
  userImportStatus({ items: [], totalCount: 0, importedCount: 0, errorCount: 0, invalidFile: true }),
  {
    alertType: 'warning',
    messageType: 'warning',
    message: 'User import process has failed. File is invalid.',
    title: '用户导入失败：文件无效',
  },
);

assert.deepEqual(
  userImportStatus({ items: [], totalCount: 3, importedCount: 2, errorCount: 1 }),
  {
    alertType: 'warning',
    messageType: 'warning',
    message: '已导入 2 个用户，1 行失败',
    title: '上次导入 3 行，成功 2 行，失败 1 行',
  },
);

assert.deepEqual(
  userImportStatus({ items: [], totalCount: 2, importedCount: 2, errorCount: 0 }),
  {
    alertType: 'success',
    messageType: 'success',
    message: '已导入 2 个用户',
    title: '上次导入 2 行，成功 2 行，失败 0 行',
  },
);
