package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.dto.ListResult;
import com.sgs.capability.model.PermissionItem;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.CapabilityStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Mirrors PermissionAppService for role and user permission pickers. */
@RestController
@RequestMapping("/api/services/app/Permission")
@RequirePermission
public class PermissionController {
    private final CapabilityStore store;

    public PermissionController(CapabilityStore store) {
        this.store = store;
    }

    @GetMapping("/GetAllPermissions")
    public AbpResponse<ListResult<PermissionItem>> getAllPermissions() {
        return AbpResponse.ok(new ListResult<>(store.permissions()));
    }
}
