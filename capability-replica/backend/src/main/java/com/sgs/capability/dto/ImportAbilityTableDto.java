package com.sgs.capability.dto;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Parsed ability Excel row shown before final save. */
public class ImportAbilityTableDto {
    public int rowNumber;
    public boolean isExist;
    public UUID existId;
    public String orgName;
    public String typeName;
    public String samplingName;
    public String productCode;
    public String testItem;
    public String testItemRemark;
    public String methodName;
    public String methodRemark;
    public String methodEngName;
    public String gbNo;
    public String gbRemark;
    public String isoNo;
    public String isoRemark;
    public String gbtNo;
    public String gbtRemark;
    public String astmNo;
    public String astmRemark;
    public String industryStandardNo;
    public String industryStandardRemark;
    public String otherNo;
    public String otherRemark;
    public String cycleWorkingDay;
    public String testTime;
    public String testTimeRemark;
    public String massRequired;
    public String massRequiredRemark;
    public String sizeRequired;
    public String sizeRequiredRemark;
    public String detectionLimit;
    public String price;
    public String priceRemark;
    public String standardNo;
    public String remark;
    public Map<String, String> labData = new LinkedHashMap<>();
    public String exception;
    public String standardNoSgs;
    public String standardNoSop;
    public String standardNoOthers;
    public String standardNoDz;
}
