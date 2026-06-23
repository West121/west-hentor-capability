package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.dto.IdRequest;
import com.sgs.capability.dto.ListResult;
import com.sgs.capability.model.NameValueItem;
import com.sgs.capability.dto.PageResult;
import com.sgs.capability.model.OrganizationUnit;
import com.sgs.capability.model.RoleItem;
import com.sgs.capability.model.UserItem;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.CapabilityStore;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Mirrors the business line tree endpoint. */
@RestController
@RequestMapping("/api/services/app/OrganizationUnit")
@RequirePermission("Pages.Administration.OrganizationUnits")
public class OrganizationUnitController {
    private final CapabilityStore store;

    public OrganizationUnitController(CapabilityStore store) {
        this.store = store;
    }

    @GetMapping("/GetOrganizationUnits")
    public AbpResponse<ListResult<OrganizationUnit>> getOrganizationUnits() {
        return AbpResponse.ok(new ListResult<>(store.orgUnits()));
    }

    @PostMapping("/CreateOrganizationUnit")
    public AbpResponse<OrganizationUnit> create(@RequestBody OrganizationUnit input) {
        String validationError = validateOrganizationUnitInput(input, false);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        input.id = 0L;
        return AbpResponse.ok(store.saveOrganizationUnit(input));
    }

    @PostMapping("/UpdateOrganizationUnit")
    public AbpResponse<OrganizationUnit> update(@RequestBody OrganizationUnit input) {
        String validationError = validateOrganizationUnitInput(input, true);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        return AbpResponse.ok(store.saveOrganizationUnit(input));
    }

    @PutMapping("/UpdateOrganizationUnit")
    public AbpResponse<OrganizationUnit> putUpdate(@RequestBody OrganizationUnit input) {
        return update(input);
    }

    @PostMapping("/MoveOrganizationUnit")
    public AbpResponse<OrganizationUnit> move(@RequestBody Map<String, Object> input) {
        Long id = longValue(input.get("id"));
        if (id == null || id <= 0) {
            // 原 MoveOrganizationUnitInput.Id 要求大于 0。
            return AbpResponse.failed("Validation failed");
        }
        return AbpResponse.ok(store.moveOrganizationUnit(id, longValue(input.get("newParentId"))));
    }

    @PostMapping("/DeleteOrganizationUnit")
    public AbpResponse<Void> delete(@RequestBody IdRequest input) {
        store.deleteOrganizationUnit(longValue(input.id));
        return AbpResponse.ok(null);
    }

    @DeleteMapping("/DeleteOrganizationUnit")
    public AbpResponse<Void> deleteByQuery(@RequestParam(name = "Id", required = false) String id) {
        store.deleteOrganizationUnit(longValue(id));
        return AbpResponse.ok(null);
    }

    @PostMapping("/GetOrganizationUnitUsers")
    public AbpResponse<PageResult<OrganizationUnitUserDto>> users(@RequestBody(required = false) Map<String, Object> input) {
        Long id = inputId(input);
        int skipCount = intValue(input == null ? null : input.get("skipCount"));
        int maxResultCount = maxResultCount(input);
        String validationError = validateOrganizationUnitPagedInput(id, skipCount, maxResultCount);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        List<OrganizationUnitUserDto> users = store.organizationUsers(id).stream()
                .map(this::organizationUser)
                .sorted(organizationUserComparator(sortingValue(input)))
                .toList();
        return AbpResponse.ok(page(users, skipCount, maxResultCount));
    }

    // Match the generated Angular client: GET with Id and paging query parameters.
    @GetMapping("/GetOrganizationUnitUsers")
    public AbpResponse<PageResult<OrganizationUnitUserDto>> usersByQuery(@RequestParam(name = "Id", required = false) String id,
                                                                         @RequestParam(name = "Sorting", required = false) String sorting,
                                                                         @RequestParam(name = "SkipCount", defaultValue = "0") int skipCount,
                                                                         @RequestParam(name = "MaxResultCount", defaultValue = "10") int maxResultCount) {
        Long organizationUnitId = longValue(id);
        String validationError = validateOrganizationUnitPagedInput(organizationUnitId, skipCount, maxResultCount);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        List<OrganizationUnitUserDto> users = store.organizationUsers(organizationUnitId).stream()
                .map(this::organizationUser)
                .sorted(organizationUserComparator(sorting))
                .toList();
        return AbpResponse.ok(page(users, skipCount, maxResultCount));
    }

    @PostMapping("/GetOrganizationUnitRoles")
    public AbpResponse<PageResult<OrganizationUnitRoleDto>> roles(@RequestBody(required = false) Map<String, Object> input) {
        Long id = inputId(input);
        int skipCount = intValue(input == null ? null : input.get("skipCount"));
        int maxResultCount = maxResultCount(input);
        String validationError = validateOrganizationUnitPagedInput(id, skipCount, maxResultCount);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        List<OrganizationUnitRoleDto> roles = store.organizationRoles(id).stream()
                .map(this::organizationRole)
                .sorted(organizationRoleComparator(sortingValue(input)))
                .toList();
        return AbpResponse.ok(page(roles, skipCount, maxResultCount));
    }

    // Match the generated Angular client: GET with Id and paging query parameters.
    @GetMapping("/GetOrganizationUnitRoles")
    public AbpResponse<PageResult<OrganizationUnitRoleDto>> rolesByQuery(@RequestParam(name = "Id", required = false) String id,
                                                                         @RequestParam(name = "Sorting", required = false) String sorting,
                                                                         @RequestParam(name = "SkipCount", defaultValue = "0") int skipCount,
                                                                         @RequestParam(name = "MaxResultCount", defaultValue = "10") int maxResultCount) {
        Long organizationUnitId = longValue(id);
        String validationError = validateOrganizationUnitPagedInput(organizationUnitId, skipCount, maxResultCount);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        List<OrganizationUnitRoleDto> roles = store.organizationRoles(organizationUnitId).stream()
                .map(this::organizationRole)
                .sorted(organizationRoleComparator(sorting))
                .toList();
        return AbpResponse.ok(page(roles, skipCount, maxResultCount));
    }

    @PostMapping("/AddUsersToOrganizationUnit")
    public AbpResponse<Void> addUsers(@RequestBody(required = false) Map<String, Object> input) {
        Long organizationUnitId = longValue(input == null ? null : input.get("organizationUnitId"));
        if (!isPositive(organizationUnitId)) {
            // 原 UsersToOrganizationUnitInput.OrganizationUnitId 要求大于 0。
            return AbpResponse.failed("Validation failed");
        }
        store.addUsersToOrganization(organizationUnitId, longList(input == null ? null : input.get("userIds")));
        return AbpResponse.ok(null);
    }

    @PostMapping("/AddRolesToOrganizationUnit")
    public AbpResponse<Void> addRoles(@RequestBody(required = false) Map<String, Object> input) {
        Long organizationUnitId = longValue(input == null ? null : input.get("organizationUnitId"));
        if (!isPositive(organizationUnitId)) {
            // 原 RolesToOrganizationUnitInput.OrganizationUnitId 要求大于 0。
            return AbpResponse.failed("Validation failed");
        }
        store.addRolesToOrganization(organizationUnitId, intList(input == null ? null : input.get("roleIds")));
        return AbpResponse.ok(null);
    }

    @PostMapping("/RemoveUserFromOrganizationUnit")
    public AbpResponse<Void> removeUser(@RequestBody(required = false) Map<String, Object> input) {
        Long organizationUnitId = longValue(input == null ? null : input.get("organizationUnitId"));
        Long userId = longValue(input == null ? null : input.get("userId"));
        String validationError = validateUserToOrganizationUnitInput(userId, organizationUnitId);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        store.removeUserFromOrganization(organizationUnitId, userId);
        return AbpResponse.ok(null);
    }

    @DeleteMapping("/RemoveUserFromOrganizationUnit")
    public AbpResponse<Void> removeUserByQuery(@RequestParam(name = "UserId", required = false) String userId,
                                               @RequestParam(name = "OrganizationUnitId", required = false) String organizationUnitId) {
        Long user = longValue(userId);
        Long organization = longValue(organizationUnitId);
        String validationError = validateUserToOrganizationUnitInput(user, organization);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        store.removeUserFromOrganization(organization, user);
        return AbpResponse.ok(null);
    }

    @PostMapping("/RemoveRoleFromOrganizationUnit")
    public AbpResponse<Void> removeRole(@RequestBody(required = false) Map<String, Object> input) {
        Long organizationUnitId = longValue(input == null ? null : input.get("organizationUnitId"));
        Integer roleId = intValue(input == null ? null : input.get("roleId"));
        String validationError = validateRoleToOrganizationUnitInput(roleId, organizationUnitId);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        store.removeRoleFromOrganization(organizationUnitId, roleId);
        return AbpResponse.ok(null);
    }

    @DeleteMapping("/RemoveRoleFromOrganizationUnit")
    public AbpResponse<Void> removeRoleByQuery(@RequestParam(name = "RoleId", required = false) String roleId,
                                               @RequestParam(name = "OrganizationUnitId", required = false) String organizationUnitId) {
        Integer role = intValue(roleId);
        Long organization = longValue(organizationUnitId);
        String validationError = validateRoleToOrganizationUnitInput(role, organization);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        store.removeRoleFromOrganization(organization, role);
        return AbpResponse.ok(null);
    }

    @PostMapping("/FindUsers")
    public AbpResponse<PageResult<NameValueItem>> findUsers(@RequestBody(required = false) Map<String, Object> input) {
        int skipCount = intValue(input == null ? null : input.get("skipCount"));
        int maxResultCount = maxResultCount(input);
        String validationError = validatePagedInput(skipCount, maxResultCount);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        List<NameValueItem> users = store.findOrganizationUsers(inputId(input), safe(input == null ? null : input.get("filter")))
                .stream()
                .map(user -> nameValue("%s %s (%s)".formatted(safe(user.name), safe(user.surname), safe(user.emailAddress)).trim(),
                        String.valueOf(user.id)))
                .toList();
        return AbpResponse.ok(page(users, skipCount, maxResultCount));
    }

    @PostMapping("/FindRoles")
    public AbpResponse<PageResult<NameValueItem>> findRoles(@RequestBody(required = false) Map<String, Object> input) {
        int skipCount = intValue(input == null ? null : input.get("skipCount"));
        int maxResultCount = maxResultCount(input);
        String validationError = validatePagedInput(skipCount, maxResultCount);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        List<NameValueItem> roles = store.findOrganizationRoles(inputId(input), safe(input == null ? null : input.get("filter")))
                .stream()
                .map(role -> nameValue("%s (%s)".formatted(safe(role.displayName), safe(role.name)).trim(), String.valueOf(role.id)))
                .toList();
        return AbpResponse.ok(page(roles, skipCount, maxResultCount));
    }

    private Long inputId(Map<String, Object> input) {
        if (input == null) {
            return null;
        }
        Object value = input.containsKey("organizationUnitId") ? input.get("organizationUnitId") : input.get("id");
        return longValue(value);
    }

    private <T> PageResult<T> page(List<T> rows, int skipCount, int maxResultCount) {
        int skip = Math.max(skipCount, 0);
        int max = maxResultCount <= 0 ? rows.size() : maxResultCount;
        return new PageResult<>(rows.size(), rows.stream().skip(skip).limit(max).toList());
    }

    private String validateOrganizationUnitInput(OrganizationUnit input, boolean requireExistingId) {
        if (input == null || safe(input.displayName).isBlank() || safe(input.displayName).length() > 128) {
            // 原 ABP DTO 对 DisplayName 使用 Required 和 StringLength(128)。
            return "Validation failed";
        }
        if (requireExistingId && input.id <= 0) {
            // 原 UpdateOrganizationUnitInput 要求 Id 大于 0。
            return "Validation failed";
        }
        return null;
    }

    private String validateOrganizationUnitPagedInput(Long id, int skipCount, int maxResultCount) {
        if (id == null || id <= 0 || skipCount < 0 || maxResultCount < 1 || maxResultCount > 1000) {
            // 原 GetOrganizationUnitUsers/Roles 输入要求 Id>0、SkipCount>=0、MaxResultCount 为 1..1000。
            return "Validation failed";
        }
        return null;
    }

    private String validatePagedInput(int skipCount, int maxResultCount) {
        if (skipCount < 0 || maxResultCount < 1 || maxResultCount > 1000) {
            // 原 PagedAndFilteredInputDto 要求 MaxResultCount 为 1-1000，SkipCount 不能为负。
            return "Validation failed";
        }
        return null;
    }

    private String validateUserToOrganizationUnitInput(Long userId, Long organizationUnitId) {
        if (!isPositive(userId) || !isPositive(organizationUnitId)) {
            // 原 UserToOrganizationUnitInput 要求 UserId 和 OrganizationUnitId 都大于 0。
            return "Validation failed";
        }
        return null;
    }

    private String validateRoleToOrganizationUnitInput(Integer roleId, Long organizationUnitId) {
        if (!isPositive(roleId) || !isPositive(organizationUnitId)) {
            // 原 RoleToOrganizationUnitInput 要求 RoleId 和 OrganizationUnitId 都大于 0。
            return "Validation failed";
        }
        return null;
    }

    private OrganizationUnitUserDto organizationUser(UserItem user) {
        return new OrganizationUnitUserDto(user.id, user.name, user.surname, user.userName, user.emailAddress,
                user.profilePictureId == null ? null : user.profilePictureId.toString(),
                user.creationTime == null ? null : user.creationTime.toString());
    }

    private OrganizationUnitRoleDto organizationRole(RoleItem role) {
        return new OrganizationUnitRoleDto(role.id == null ? null : role.id.longValue(), role.displayName, role.name,
                role.creationTime == null ? null : role.creationTime.toString());
    }

    private NameValueItem nameValue(String name, String value) {
        NameValueItem item = new NameValueItem();
        item.name = name;
        item.value = value;
        return item;
    }

    private Comparator<OrganizationUnitUserDto> organizationUserComparator(String sorting) {
        String normalized = safe(sorting).trim();
        if (normalized.isBlank()) {
            // Original GetOrganizationUnitUsersInput defaults blank sorting to user.Name, user.Surname.
            normalized = "user.Name, user.Surname";
        }
        Comparator<OrganizationUnitUserDto> comparator = null;
        for (String part : normalized.split(",")) {
            String token = part.trim();
            if (token.isBlank()) {
                continue;
            }
            boolean desc = token.toLowerCase(Locale.ROOT).endsWith(" desc");
            String field = token.toLowerCase(Locale.ROOT).replace(" desc", "").replace(" asc", "").trim();
            Comparator<OrganizationUnitUserDto> current = organizationUserFieldComparator(field);
            comparator = comparator == null ? (desc ? current.reversed() : current)
                    : comparator.thenComparing(desc ? current.reversed() : current);
        }
        return comparator == null ? organizationUserFieldComparator("user.name") : comparator;
    }

    private Comparator<OrganizationUnitUserDto> organizationUserFieldComparator(String field) {
        return switch (field) {
            case "username", "user.username" -> Comparator.comparing(item -> safe(item.userName()));
            case "surname", "user.surname" -> Comparator.comparing(item -> safe(item.surname()));
            case "addedtime", "creationtime", "uou.creationtime" -> Comparator.comparing(item -> safe(item.addedTime()));
            case "id" -> Comparator.comparing(item -> item.id() == null ? 0L : item.id());
            default -> Comparator.comparing(item -> safe(item.name()));
        };
    }

    private Comparator<OrganizationUnitRoleDto> organizationRoleComparator(String sorting) {
        String normalized = safe(sorting).trim();
        if (normalized.isBlank()) {
            // Original GetOrganizationUnitRolesInput defaults blank sorting to role.DisplayName, role.Name.
            normalized = "role.DisplayName, role.Name";
        }
        Comparator<OrganizationUnitRoleDto> comparator = null;
        for (String part : normalized.split(",")) {
            String token = part.trim();
            if (token.isBlank()) {
                continue;
            }
            boolean desc = token.toLowerCase(Locale.ROOT).endsWith(" desc");
            String field = token.toLowerCase(Locale.ROOT).replace(" desc", "").replace(" asc", "").trim();
            Comparator<OrganizationUnitRoleDto> current = organizationRoleFieldComparator(field);
            comparator = comparator == null ? (desc ? current.reversed() : current)
                    : comparator.thenComparing(desc ? current.reversed() : current);
        }
        return comparator == null ? organizationRoleFieldComparator("role.displayname") : comparator;
    }

    private Comparator<OrganizationUnitRoleDto> organizationRoleFieldComparator(String field) {
        return switch (field) {
            case "name", "role.name" -> Comparator.comparing(item -> safe(item.name()));
            case "addedtime", "creationtime", "uou.creationtime" -> Comparator.comparing(item -> safe(item.addedTime()));
            case "id" -> Comparator.comparing(item -> item.id() == null ? 0L : item.id());
            default -> Comparator.comparing(item -> safe(item.displayName()));
        };
    }

    private Long longValue(Object value) {
        try {
            return value == null ? null : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer intValue(Object value) {
        try {
            return value == null ? 0 : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private boolean isPositive(Long value) {
        return value != null && value > 0;
    }

    private boolean isPositive(Integer value) {
        return value != null && value > 0;
    }

    private int maxResultCount(Map<String, Object> input) {
        if (input == null || !input.containsKey("maxResultCount")) {
            return 10;
        }
        return intValue(input.get("maxResultCount"));
    }

    private String sortingValue(Map<String, Object> input) {
        if (input == null) {
            return null;
        }
        Object value = input.containsKey("sorting") ? input.get("sorting") : input.get("Sorting");
        return value == null ? null : String.valueOf(value);
    }

    private List<Long> longList(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream().map(this::longValue).filter(item -> item != null).toList();
    }

    private List<Integer> intList(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream().map(this::intValue).filter(item -> item != null && item > 0).toList();
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public record OrganizationUnitUserDto(Long id, String name, String surname, String userName, String emailAddress,
                                          String profilePictureId, String addedTime) {
    }

    public record OrganizationUnitRoleDto(Long id, String displayName, String name, String addedTime) {
    }
}
