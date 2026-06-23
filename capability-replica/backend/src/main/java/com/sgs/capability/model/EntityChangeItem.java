package com.sgs.capability.model;

/** Entity history row compatible with EntityChangeListDto. */
public class EntityChangeItem {
    public Long id;
    public Long userId;
    public String userName;
    public String changeTime;
    public String entityTypeFullName;
    public String entityTypeDescription;
    public String entityId;
    public Integer changeType;
    public String changeTypeName;
    public Long entityChangeSetId;
    public Integer tenantId;
}
