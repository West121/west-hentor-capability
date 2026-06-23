package com.sgs.capability.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.List;

/** Output copied from AbilityTableController.UploadAbilityTable. */
public class AbilityTableUploadOutput {
    public List<ImportAbilityTableDto> abilityTableList = new ArrayList<>();
    public List<String> labCodeList = new ArrayList<>();
    @JsonIgnore
    public FileDto file;
    @JsonIgnore
    public FileDto errorFile;
    @JsonIgnore
    public int totalCount;
    @JsonIgnore
    public int errorCount;
    @JsonIgnore
    public int duplicateCount;
}
