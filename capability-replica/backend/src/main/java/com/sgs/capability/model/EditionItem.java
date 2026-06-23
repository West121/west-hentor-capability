package com.sgs.capability.model;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** Edition row matching EditionListDto and EditionEditDto fields. */
public class EditionItem {
    public Integer id;
    public String name;
    public String displayName;
    public BigDecimal dailyPrice;
    public BigDecimal weeklyPrice;
    public BigDecimal monthlyPrice;
    public BigDecimal annualPrice;
    public Integer waitingDayAfterExpire;
    public Integer trialDayCount;
    public Integer expiringEditionId;
    public String expiringEditionDisplayName;
    public boolean isFree;
    public Map<String, String> featureValues = new LinkedHashMap<>();
}
