package com.sgs.capability.model;

import java.util.LinkedHashMap;
import java.util.Map;

/** Feature row copied from FlatFeatureDto. */
public class FeatureItem {
    public String parentName;
    public String name;
    public String displayName;
    public String description;
    public String defaultValue;
    public Map<String, Object> inputType = new LinkedHashMap<>();
}
