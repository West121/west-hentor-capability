package com.sgs.capability.dto;

/** Input copied from DeleteAllDto; deletes ability rows by organization name. */
public class DeleteAllAbilityRequest {
    public String orgName;
    public String OrgName;

    public String orgName() {
        return orgName != null ? orgName : OrgName;
    }
}
