package com.sgs.capability.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Main capability table row copied from MineralAbilityTable fields. */
public class Ability {
    public UUID id;
    public String creationTime;
    public String orgName;
    public Long orgId;
    public String typeName;
    public UUID typeId;
    public String samplingName;
    public UUID samplingId;
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
    public String standardNo;
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
    public String remark;
    public String standardNoSgs;
    public String standardNoSop;
    public String standardNoOthers;
    public String standardNoDz;
    public boolean isCollected;
    public List<LabAbility> labAbilities = new ArrayList<>();
}
