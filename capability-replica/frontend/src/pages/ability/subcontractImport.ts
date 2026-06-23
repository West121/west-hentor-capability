import type { FileDto, SubcontractAbility, UploadSubcontractAbilityOutput } from '../../types/domain';

export interface SaveSubcontractExcelInput {
  file: FileDto;
  dataList: SubcontractAbility[];
}

export function buildSubcontractSaveInput(output: UploadSubcontractAbilityOutput): SaveSubcontractExcelInput {
  return {
    file: output.file,
    dataList: output.items,
  };
}
