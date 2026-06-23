package com.sgs.capability.controller;

import com.sgs.capability.dto.*;
import com.sgs.capability.model.PermissionItem;
import com.sgs.capability.model.RoleItem;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.CapabilityStore;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;

/** Mirrors RoleAppService CRUD and permission edit routes. */
@RestController
@RequestMapping("/api/services/app/Role")
@RequirePermission("Pages.Administration.Roles")
public class RoleController {
    private final CapabilityStore store;

    public RoleController(CapabilityStore store) {
        this.store = store;
    }

    @PostMapping("/GetRoles")
    public AbpResponse<ListResult<RoleItem>> getRoles(@RequestBody(required = false) FindAbilityRequest input) {
        return AbpResponse.ok(new ListResult<>(store.roles(input == null ? null : input.filter)));
    }

    @GetMapping("/GetRoles")
    public AbpResponse<ListResult<RoleItem>> getRolesByQuery(@RequestParam(name = "Permissions", required = false) List<String> permissions,
                                                             @RequestParam(name = "Filter", required = false) String filter) {
        List<RoleItem> roles = store.roles(filter);
        List<String> requiredPermissions = permissions == null ? List.of() : permissions;
        if (!requiredPermissions.isEmpty()) {
            roles = roles.stream()
                    .filter(role -> role.grantedPermissionNames.containsAll(requiredPermissions))
                    .toList();
        }
        return AbpResponse.ok(new ListResult<>(roles));
    }

    @PostMapping("/GetRoleForEdit")
    public AbpResponse<RoleEditOutput> getRoleForEdit(@RequestBody(required = false) IdRequest input) {
        RoleItem role = input == null ? null : store.role(parseInt(input.id)).orElse(null);
        return AbpResponse.ok(new RoleEditOutput(role == null ? new RoleItem() : role, roleEditPermissions(),
                role == null ? List.of() : role.grantedPermissionNames));
    }

    @GetMapping("/GetRoleForEdit")
    public AbpResponse<RoleEditOutput> getRoleForEditByQuery(@RequestParam(name = "Id", required = false) String id) {
        IdRequest request = new IdRequest();
        request.id = id;
        return getRoleForEdit(request);
    }

    @PostMapping("/CreateOrUpdateRole")
    public AbpResponse<Void> createOrUpdate(@RequestBody RoleEditInput input) {
        String validationError = validateRoleEditInput(input);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        store.saveRole(input.role, input.grantedPermissionNames);
        return AbpResponse.ok(null);
    }

    @PostMapping("/DeleteRole")
    public AbpResponse<Void> deleteRole(@RequestBody IdRequest input) {
        store.deleteRole(parseInt(input.id));
        return AbpResponse.ok(null);
    }

    @DeleteMapping("/DeleteRole")
    public AbpResponse<Void> deleteRoleByQuery(@RequestParam(name = "Id", required = false) String id) {
        store.deleteRole(parseInt(id));
        return AbpResponse.ok(null);
    }

    private Integer parseInt(String value) {
        try {
            return value == null || value.isBlank() ? null : Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private List<PermissionItem> roleEditPermissions() {
        return store.permissions().stream()
                .sorted(Comparator.comparing(item -> item.displayName == null ? "" : item.displayName))
                .toList();
    }

    private String validateRoleEditInput(RoleEditInput input) {
        if (input == null || input.role == null || input.grantedPermissionNames == null) {
            return "Validation failed";
        }
        if (safe(input.role.displayName).isBlank()) {
            // 原 CreateOrUpdateRoleInput 要求 Role、GrantedPermissionNames 和 Role.DisplayName。
            return "Validation failed";
        }
        return null;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public record RoleEditOutput(RoleItem role, List<PermissionItem> permissions, List<String> grantedPermissionNames) {
    }
}
