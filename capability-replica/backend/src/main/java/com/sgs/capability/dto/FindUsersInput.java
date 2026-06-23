package com.sgs.capability.dto;

/** Input copied from CommonLookup FindUsersInput. */
public class FindUsersInput {
    public Integer tenantId;
    public boolean excludeCurrentUser;
    public String filter;
    public int skipCount = 0;
    public int maxResultCount = 10;
}
