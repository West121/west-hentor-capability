package com.sgs.capability.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Role row matching RoleListDto/RoleEditDto fields. */
public class RoleItem {
    public Integer id;
    public String name;
    public String displayName;
    public boolean isStatic;
    public boolean isDefault;
    public LocalDateTime creationTime;
    public List<String> grantedPermissionNames = new ArrayList<>();
    public List<Long> organizationUnits = new ArrayList<>();
}
