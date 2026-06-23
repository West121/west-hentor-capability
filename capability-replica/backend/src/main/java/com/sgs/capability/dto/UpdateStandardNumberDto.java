package com.sgs.capability.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/** One standard-number replacement row from the uploaded Excel file. */
public class UpdateStandardNumberDto {
    public String old;
    @JsonProperty("new")
    @JsonAlias("newValue")
    public String newValue;
    public String name;
    public String statu;
    public String remark;
    @JsonIgnore
    public int matchedCount;
    @JsonIgnore
    public int updatedCount;
    @JsonIgnore
    public String exception;
}
