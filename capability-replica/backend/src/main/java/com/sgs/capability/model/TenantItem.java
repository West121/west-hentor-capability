package com.sgs.capability.model;

import java.util.LinkedHashMap;
import java.util.Map;

/** Tenant row matching TenantListDto and TenantEditDto fields. */
public class TenantItem {
    public Integer id;
    public String tenancyName;
    public String name;
    public String editionDisplayName;
    public String connectionString;
    public boolean isActive;
    public String creationTime;
    public String subscriptionEndDateUtc;
    public Integer subscriptionPaymentType;
    public Integer editionId;
    public boolean isInTrialPeriod;
    public String adminEmailAddress;
    public String adminPassword;
    public boolean shouldChangePasswordOnNextLogin;
    public boolean sendActivationEmail;
    public String logoId;
    public String logoFileType;
    public String logoContentBase64;
    public String customCssId;
    public String customCssContentBase64;
    public Map<String, String> featureValues = new LinkedHashMap<>();
}
