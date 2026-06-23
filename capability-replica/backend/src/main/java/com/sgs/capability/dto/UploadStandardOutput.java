package com.sgs.capability.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.List;

/** Standard upload output keeps the original Items + File shape. */
public class UploadStandardOutput {
    public List<UpdateStandardNumberDto> items = new ArrayList<>();
    public FileDto file;
    @JsonIgnore
    public FileDto errorFile;
    @JsonIgnore
    public int totalCount;
    @JsonIgnore
    public int errorCount;
    @JsonIgnore
    public int matchedCount;
    @JsonIgnore
    public int updatedCount;
}
