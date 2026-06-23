package com.sgs.capability.dto;

import java.util.ArrayList;
import java.util.List;

/** Current user's visible ability-column settings for one organization. */
public class MyOrgSettingDto {
    public long orgId;
    public String orgName;
    public List<String> propertyList = new ArrayList<>();
    public List<String> lab = new ArrayList<>();
    public String description;
    public boolean isPublic;
}
