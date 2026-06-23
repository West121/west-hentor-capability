package com.sgs.capability.dto;

/** Filter copied from GetEntityTypeChangeInput for one tracked entity instance. */
public class GetEntityTypeChangeInput {
    public String entityTypeFullName;
    public String entityId;
    public int skipCount = 0;
    public int maxResultCount = 10;
    public String sorting = "changeTime DESC";
}
