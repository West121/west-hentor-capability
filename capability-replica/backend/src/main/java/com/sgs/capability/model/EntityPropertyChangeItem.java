package com.sgs.capability.model;

/** Property-level entity history row compatible with EntityPropertyChangeDto. */
public class EntityPropertyChangeItem {
    public Long id;
    public Long entityChangeId;
    public String newValue;
    public String originalValue;
    public String propertyName;
    public String propertyTypeFullName;
    public Integer tenantId;
}
