package com.sgs.capability.dto;

import com.sgs.capability.model.SubcontractAbility;

import java.util.ArrayList;
import java.util.List;

/** Save payload for confirmed subcontract ability Excel rows. */
public class SaveSubcontractAbilityExcelInput {
    public boolean onlySaveNew;
    public FileDto file;
    public List<SubcontractAbility> dataList = new ArrayList<>();
}
