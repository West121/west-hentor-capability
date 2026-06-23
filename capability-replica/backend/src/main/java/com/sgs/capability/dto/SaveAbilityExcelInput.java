package com.sgs.capability.dto;

import java.util.ArrayList;
import java.util.List;

/** Save payload for confirmed ability Excel rows. */
public class SaveAbilityExcelInput {
    public boolean onlySaveNew;
    public FileDto file;
    public List<ImportAbilityTableDto> dataList = new ArrayList<>();
}
