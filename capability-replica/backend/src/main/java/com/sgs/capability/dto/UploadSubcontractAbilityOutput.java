package com.sgs.capability.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sgs.capability.model.SubcontractAbility;

import java.util.ArrayList;
import java.util.List;

/** Output copied from UploadSubcontractAbilityDto. */
public class UploadSubcontractAbilityOutput {
    public List<SubcontractAbility> items = new ArrayList<>();
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
