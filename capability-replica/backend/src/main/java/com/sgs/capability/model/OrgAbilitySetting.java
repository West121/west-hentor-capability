package com.sgs.capability.model;

import java.util.ArrayList;
import java.util.List;

/** Stores which ability columns are enabled for a business line. */
public class OrgAbilitySetting {
    public long orgId;
    public List<String> propertyName = new ArrayList<>();
    public List<String> lab = new ArrayList<>();
    public boolean isPublic;
    public String description;
}
