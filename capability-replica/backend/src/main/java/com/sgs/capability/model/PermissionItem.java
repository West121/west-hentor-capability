package com.sgs.capability.model;

/** Flat permission node used by role and user permission screens. */
public class PermissionItem {
    public String name;
    public String displayName;
    public String description;
    public String parentName;
    public boolean isGrantedByDefault;
    public int level;
}
