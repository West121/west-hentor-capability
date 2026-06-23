package com.sgs.capability.model;

/** User delegation row copied from the AspNet Zero account platform shape. */
public class UserDelegation {
    public Long id;
    public Long sourceUserId;
    public Long targetUserId;
    public Integer tenantId;
    public String targetUserName;
    public String targetName;
    public String startTime;
    public String endTime;
    public boolean active;
}
