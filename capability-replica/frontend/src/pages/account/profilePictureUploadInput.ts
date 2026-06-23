import type { UpdateProfilePictureInput, UploadProfilePictureOutput } from '../../types/domain';

// Profile upload returns image dimensions; ProfileAppService persists the selected crop.
export function profilePictureUpdateInput(uploaded: UploadProfilePictureOutput): UpdateProfilePictureInput {
  return {
    fileToken: uploaded.fileToken,
    x: 0,
    y: 0,
    width: uploaded.width ?? 0,
    height: uploaded.height ?? 0,
  };
}
