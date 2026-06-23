import type { UserImportOutput } from '../../types/domain';

type StatusType = 'success' | 'warning';

export interface UserImportStatus {
  alertType: StatusType;
  messageType: StatusType;
  message: string;
  title: string;
}

// Keeps the local import summary aligned with the original background import notifications.
export function userImportStatus(output: UserImportOutput): UserImportStatus {
  if (output.invalidFile) {
    return {
      alertType: 'warning',
      messageType: 'warning',
      message: 'User import process has failed. File is invalid.',
      title: '用户导入失败：文件无效',
    };
  }
  if (output.errorCount) {
    return {
      alertType: 'warning',
      messageType: 'warning',
      message: `已导入 ${output.importedCount} 个用户，${output.errorCount} 行失败`,
      title: `上次导入 ${output.totalCount} 行，成功 ${output.importedCount} 行，失败 ${output.errorCount} 行`,
    };
  }
  return {
    alertType: 'success',
    messageType: 'success',
    message: `已导入 ${output.importedCount} 个用户`,
    title: `上次导入 ${output.totalCount} 行，成功 ${output.importedCount} 行，失败 ${output.errorCount} 行`,
  };
}
