package com.sgs.capability.dto;

/** Filter copied from GetEntityChangeInput in the original AuditLogAppService. */
public class GetEntityChangeInput {
    public String startDate;
    public String endDate;
    public String userName;
    public String entityTypeFullName;
    public int skipCount = 0;
    public int maxResultCount = 10;
    public String sorting = "changeTime DESC";
}
