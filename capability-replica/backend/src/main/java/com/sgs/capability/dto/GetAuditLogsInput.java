package com.sgs.capability.dto;

/** Filter copied from GetAuditLogsInput in the original AuditLogAppService. */
public class GetAuditLogsInput {
    public String startDate;
    public String endDate;
    public String userName;
    public String serviceName;
    public String methodName;
    public String browserInfo;
    public Boolean hasException;
    public Integer minExecutionDuration;
    public Integer maxExecutionDuration;
    public int skipCount = 0;
    public int maxResultCount = 10;
    public String sorting = "executionTime DESC";
}
