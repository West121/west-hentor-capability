package com.sgs.capability.dto;

import com.sgs.capability.model.RoleItem;

import java.util.List;

/** Request body for RoleAppService.CreateOrUpdateRole. */
public class RoleEditInput {
    public RoleItem role;
    public List<String> grantedPermissionNames;
}
