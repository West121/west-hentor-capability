package com.sgs.capability.model;

/** Audit log row compatible with AuditLogListDto. */
public class AuditLog {
    public Long id;
    public Long userId;
    public String time;
    public String userName;
    public Integer impersonatorTenantId;
    public Long impersonatorUserId;
    public String serviceName;
    public String methodName;
    public String parameters;
    public String executionTime;
    public Integer executionDuration;
    public String clientIpAddress;
    public String clientName;
    public String browserInfo;
    public String exception;
    public String customData;
    public String result;
}
