package com.sgs.capability.model;

/** Business line node copied from ABP organization units. */
public class OrganizationUnit {
    public long id;
    public Long parentId;
    public String code;
    public String displayName;
    public int memberCount;
    public int roleCount;
}
