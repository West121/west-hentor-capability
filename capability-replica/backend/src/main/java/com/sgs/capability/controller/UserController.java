package com.sgs.capability.controller;

import com.sgs.capability.dto.*;
import com.sgs.capability.model.*;
import com.sgs.capability.security.AuthContext;
import com.sgs.capability.security.AuthorizationInterceptor;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.CapabilityStore;
import com.sgs.capability.service.ExcelTransferService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Mirrors UserAppService CRUD and permission edit routes. */
@RestController
@RequestMapping("/api/services/app/User")
@RequirePermission("Pages.Administration.Users")
public class UserController {
    private final CapabilityStore store;
    private final ExcelTransferService excel;

    public UserController(CapabilityStore store, ExcelTransferService excel) {
        this.store = store;
        this.excel = excel;
    }

    @PostMapping("/GetUsers")
    public AbpResponse<PageResult<UserItem>> getUsers(@RequestBody(required = false) GetUsersInput input) {
        GetUsersInput safeInput = input == null ? new GetUsersInput() : input;
        String validationError = validatePagedInput(safeInput.skipCount, safeInput.maxResultCount);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        return AbpResponse.ok(store.users(safeInput.filter, safeInput.role, safeInput.permissions, safeInput.onlyLockedUsers,
                safeInput.skipCount, safeInput.maxResultCount, safeInput.sorting));
    }

    @GetMapping("/GetUsers")
    public AbpResponse<PageResult<UserItem>> getUsersByQuery(@RequestParam(name = "Filter", required = false) String filter,
                                                             @RequestParam(name = "Permissions", required = false) List<String> permissions,
                                                             @RequestParam(name = "Role", required = false) Integer role,
                                                             @RequestParam(name = "OnlyLockedUsers", defaultValue = "false") boolean onlyLockedUsers,
                                                             @RequestParam(name = "Sorting", required = false) String sorting,
                                                             @RequestParam(name = "SkipCount", defaultValue = "0") int skipCount,
                                                             @RequestParam(name = "MaxResultCount", defaultValue = "10") int maxResultCount) {
        GetUsersInput input = usersInput(filter, permissions, role, onlyLockedUsers, sorting, skipCount, maxResultCount);
        return getUsers(input);
    }

    @PostMapping("/GetUsersToExcel")
    public AbpResponse<FileDto> getUsersToExcel(@RequestBody(required = false) GetUsersInput input) {
        GetUsersInput safeInput = input == null ? new GetUsersInput() : input;
        return AbpResponse.ok(excel.userExport(store.filteredUsers(safeInput.filter, safeInput.role,
                safeInput.permissions, safeInput.onlyLockedUsers, userExportSorting(safeInput.sorting))));
    }

    @GetMapping("/GetUsersToExcel")
    public AbpResponse<FileDto> getUsersToExcelByQuery(@RequestParam(name = "Filter", required = false) String filter,
                                                       @RequestParam(name = "Permissions", required = false) List<String> permissions,
                                                       @RequestParam(name = "Role", required = false) Integer role,
                                                       @RequestParam(name = "OnlyLockedUsers", defaultValue = "false") boolean onlyLockedUsers,
                                                       @RequestParam(name = "Sorting", required = false) String sorting) {
        return getUsersToExcel(usersInput(filter, permissions, role, onlyLockedUsers, sorting, 0, 10));
    }

    @PostMapping("/GetUserForEdit")
    public AbpResponse<UserEditOutput> getUserForEdit(@RequestBody(required = false) IdRequest input) {
        UserItem user = input == null ? null : store.user(parseLong(input.id)).orElse(null);
        UserItem editUser = user == null ? originalCreateUserDefaults() : user;
        List<String> assignedRoleNames = user == null ? defaultRoleNames() : user.assignedRoleNames;
        return AbpResponse.ok(new UserEditOutput(editUser, userRoleDtos(user, assignedRoleNames), store.orgUnits(), store.labs(),
                assignedRoleNames,
                user == null ? List.of() : user.organizationUnits,
                user == null ? List.of() : user.labs));
    }

    @GetMapping("/GetUserForEdit")
    public AbpResponse<UserEditOutput> getUserForEditByQuery(@RequestParam(name = "Id", required = false) String id) {
        return getUserForEdit(idRequest(id));
    }

    @PostMapping("/GetUserPermissionsForEdit")
    public AbpResponse<UserPermissionsEditOutput> getUserPermissionsForEdit(@RequestBody(required = false) IdRequest input) {
        Long userId = parseLong(input == null ? null : input.id);
        return AbpResponse.ok(new UserPermissionsEditOutput(store.permissions(), store.userSpecificPermissionNames(userId)));
    }

    @GetMapping("/GetUserPermissionsForEdit")
    public AbpResponse<UserPermissionsEditOutput> getUserPermissionsForEditByQuery(@RequestParam(name = "Id", required = false) String id) {
        return getUserPermissionsForEdit(idRequest(id));
    }

    @PostMapping("/UpdateUserPermissions")
    public AbpResponse<Void> updateUserPermissions(@RequestBody(required = false) UpdateUserPermissionsInput input) {
        String validationError = validateUpdateUserPermissionsInput(input);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        store.updateUserPermissions(input.id, input.grantedPermissionNames);
        return AbpResponse.ok(null);
    }

    @PutMapping("/UpdateUserPermissions")
    public AbpResponse<Void> putUpdateUserPermissions(@RequestBody(required = false) UpdateUserPermissionsInput input) {
        return updateUserPermissions(input);
    }

    @PostMapping("/ResetUserSpecificPermissions")
    public AbpResponse<Void> resetUserSpecificPermissions(@RequestBody(required = false) IdRequest input) {
        store.resetUserSpecificPermissions(parseLong(input == null ? null : input.id));
        return AbpResponse.ok(null);
    }

    @PostMapping("/CreateOrUpdateUser")
    public AbpResponse<Void> createOrUpdate(@RequestBody UserEditInput input, HttpServletRequest request) {
        UserEditInput safeInput = input == null ? new UserEditInput() : input;
        String validationError = validateUserEditInput(safeInput);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        if (safeInput.setRandomPassword) {
            safeInput.user.password = store.createRandomPassword(currentTenantId(request));
        }
        safeInput.user.surname = "-";
        store.saveUser(safeInput.user, safeInput.assignedRoleNames, safeInput.organizationUnits, safeInput.labs);
        return AbpResponse.ok(null);
    }

    @PostMapping("/DeleteUser")
    public AbpResponse<Void> deleteUser(@RequestBody IdRequest input, HttpServletRequest request) {
        Long userId = parseLong(input.id);
        // Original UserAppService blocks deleting the signed-in user.
        if (isCurrentUser(request, userId)) {
            return AbpResponse.failed("You can not delete own user account!");
        }
        store.deleteUser(userId);
        return AbpResponse.ok(null);
    }

    @DeleteMapping("/DeleteUser")
    public AbpResponse<Void> deleteUserByQuery(@RequestParam(name = "Id", required = false) String id,
                                               HttpServletRequest request) {
        Long userId = parseLong(id);
        // Original UserAppService blocks deleting the signed-in user.
        if (isCurrentUser(request, userId)) {
            return AbpResponse.failed("You can not delete own user account!");
        }
        store.deleteUser(userId);
        return AbpResponse.ok(null);
    }

    @PostMapping("/UnlockUser")
    public AbpResponse<Void> unlockUser(@RequestBody(required = false) IdRequest input) {
        store.unlockUser(parseLong(input == null ? null : input.id));
        return AbpResponse.ok(null);
    }

    @PostMapping("/ResetUserPassword")
    public AbpResponse<Void> resetUserPassword(@RequestBody(required = false) IdRequest input) {
        store.resetUserPassword(parseLong(input == null ? null : input.id));
        return AbpResponse.ok(null);
    }

    private Long parseLong(String value) {
        try {
            return value == null || value.isBlank() ? null : Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private IdRequest idRequest(String id) {
        IdRequest request = new IdRequest();
        request.id = id;
        return request;
    }

    private boolean isCurrentUser(HttpServletRequest request, Long userId) {
        Object context = request.getAttribute(AuthorizationInterceptor.AUTH_CONTEXT);
        return context instanceof AuthContext authContext && userId != null && userId.equals(authContext.user().id);
    }

    private Integer currentTenantId(HttpServletRequest request) {
        Object context = request.getAttribute(AuthorizationInterceptor.AUTH_CONTEXT);
        return context instanceof AuthContext authContext ? authContext.tenantId() : null;
    }

    private String validateUserEditInput(UserEditInput input) {
        if (input == null || input.user == null
                || input.assignedRoleNames == null
                || !hasText(input.user.name)
                || !hasText(input.user.userName)
                || !hasText(input.user.emailAddress)
                || !input.user.emailAddress.contains("@")
                || isTooLong(input.user.name, 64)
                || isTooLong(input.user.surname, 64)
                || isTooLong(input.user.userName, 256)
                || isTooLong(input.user.emailAddress, 256)
                || isTooLong(input.user.phoneNumber, 24)
                || isTooLong(input.user.password, 32)) {
            // 原 CreateOrUpdateUserInput 要求 User 和 AssignedRoleNames 必填，UserEditDto 字段也有长度限制。
            return "Validation failed";
        }
        return null;
    }

    private String validateUpdateUserPermissionsInput(UpdateUserPermissionsInput input) {
        if (input == null || input.id == null || input.id < 1 || input.grantedPermissionNames == null) {
            // 原 UpdateUserPermissionsInput 要求 Id 为正数且 GrantedPermissionNames 必填。
            return "Validation failed";
        }
        return null;
    }

    private String validatePagedInput(int skipCount, int maxResultCount) {
        if (skipCount < 0 || maxResultCount < 1 || maxResultCount > 1000) {
            // 原 PagedInputDto 要求 MaxResultCount 为 1-1000，SkipCount 不能为负。
            return "Validation failed";
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isTooLong(String value, int maxLength) {
        return value != null && value.length() > maxLength;
    }

    private GetUsersInput usersInput(String filter, List<String> permissions, Integer role, boolean onlyLockedUsers,
                                     String sorting, int skipCount, int maxResultCount) {
        GetUsersInput input = new GetUsersInput();
        input.filter = filter;
        input.permissions = permissions == null ? List.of() : permissions;
        input.role = role;
        input.onlyLockedUsers = onlyLockedUsers;
        input.sorting = sorting;
        input.skipCount = skipCount;
        input.maxResultCount = maxResultCount;
        return input;
    }

    private String userExportSorting(String sorting) {
        // Original GetUsersToExcelInput.Normalize defaults blank sorting to Name,Surname.
        return sorting == null || sorting.isBlank() ? "Name,Surname" : sorting;
    }

    private UserItem originalCreateUserDefaults() {
        UserItem user = new UserItem();
        user.isActive = true;
        user.password = "qazwsxEDCRFV";
        user.shouldChangePasswordOnNextLogin = false;
        user.isTwoFactorEnabled = false;
        user.isLockoutEnabled = false;
        return user;
    }

    private List<String> defaultRoleNames() {
        return store.roles(null).stream()
                .filter(role -> role.isDefault)
                .map(role -> role.name)
                .toList();
    }

    private List<UserRoleDto> userRoleDtos(UserItem user, List<String> assignedRoleNames) {
        List<String> assigned = assignedRoleNames == null ? List.of() : assignedRoleNames;
        return store.roles(null).stream()
                .map(role -> new UserRoleDto(role.id, role.name, role.displayName,
                        role.id, role.name, role.displayName,
                        assigned.stream().anyMatch(name -> equalsText(name, role.name)),
                        inheritedFromOrganizationUnit(user, role)))
                .toList();
    }

    private boolean inheritedFromOrganizationUnit(UserItem user, RoleItem role) {
        if (user == null || user.organizationUnits == null || role.organizationUnits == null) {
            return false;
        }
        return role.organizationUnits.stream().anyMatch(user.organizationUnits::contains);
    }

    private boolean equalsText(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    public record UserEditOutput(UserItem user, List<UserRoleDto> roles, List<OrganizationUnit> allOrganizationUnits,
                                 List<Laboratory> allLabs, List<String> assignedRoleNames,
                                 List<Long> memberedOrganizationUnits, List<java.util.UUID> memberedLabs) {
    }

    public record UserRoleDto(Integer id, String name, String displayName, Integer roleId, String roleName,
                              String roleDisplayName, boolean isAssigned, boolean inheritedFromOrganizationUnit) {
    }

    public record UserPermissionsEditOutput(List<PermissionItem> permissions, List<String> grantedPermissionNames) {
    }

    public static class GetUsersInput {
        public String filter;
        public List<String> permissions = List.of();
        public Integer role;
        public boolean onlyLockedUsers;
        public String sorting;
        public int skipCount = 0;
        public int maxResultCount = 10;
    }

    public static class UpdateUserPermissionsInput {
        public Long id;
        public List<String> grantedPermissionNames;
    }
}
