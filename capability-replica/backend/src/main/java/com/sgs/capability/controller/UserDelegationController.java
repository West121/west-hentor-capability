package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.dto.FindAbilityRequest;
import com.sgs.capability.dto.IdRequest;
import com.sgs.capability.dto.PageResult;
import com.sgs.capability.model.UserDelegation;
import com.sgs.capability.security.AuthContext;
import com.sgs.capability.security.AuthService;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.CapabilityStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

/** Mirrors UserDelegationAppService for local account delegation. */
@RestController
@RequestMapping("/api/services/app/UserDelegation")
@RequirePermission
public class UserDelegationController {
    private static final String REMOVE_OWNERSHIP_ERROR = "Only source user can delete a user delegation !";
    private static final String START_AFTER_END_ERROR = "StartTime of a user delegation operation can't be bigger than EndTime!";

    private final AuthService auth;
    private final CapabilityStore store;

    public UserDelegationController(AuthService auth, CapabilityStore store) {
        this.auth = auth;
        this.store = store;
    }

    @PostMapping("/GetDelegatedUsers")
    public AbpResponse<PageResult<UserDelegation>> delegatedUsers(@RequestBody(required = false) FindAbilityRequest input,
                                                                  HttpServletRequest request) {
        AuthContext context = current(request);
        if (context == null) {
            return AbpResponse.denied("未登录或登录已过期");
        }
        FindAbilityRequest safeInput = input == null ? new FindAbilityRequest() : input;
        String validationError = safeInput.validateOriginalPaging();
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        return AbpResponse.ok(store.delegatedUsers(context.user().id, safeInput.filter, safeInput.skipCount,
                safeInput.maxResultCount, safeInput.sorting));
    }

    // Match the generated Angular client: GET with PascalCase paging keys.
    @GetMapping("/GetDelegatedUsers")
    public AbpResponse<PageResult<UserDelegation>> delegatedUsersByQuery(
            @RequestParam(name = "MaxResultCount", defaultValue = "10") int maxResultCount,
            @RequestParam(name = "SkipCount", defaultValue = "0") int skipCount,
            @RequestParam(name = "Sorting", required = false) String sorting,
            HttpServletRequest request) {
        AuthContext context = current(request);
        if (context == null) {
            return AbpResponse.denied("未登录或登录已过期");
        }
        String validationError = validatePagedInput(skipCount, maxResultCount);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        return AbpResponse.ok(store.delegatedUsers(context.user().id, null, skipCount, maxResultCount, sorting));
    }

    @PostMapping("/DelegateNewUser")
    public AbpResponse<Void> delegateNewUser(@RequestBody DelegateNewUserInput input,
                                             HttpServletRequest request) {
        AuthContext context = current(request);
        if (context == null) {
            return AbpResponse.denied("未登录或登录已过期");
        }
        String validationError = validateDelegateNewUserInput(input);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        Long targetId = targetUserId(input);
        // Original UserDelegationAppService reports a dedicated error for self delegation.
        if (Objects.equals(targetId, context.user().id)) {
            return AbpResponse.failed("You can't delegate authorization to yourself !");
        }
        if (store.delegateNewUser(context.user().id, context.tenantId(), targetId, input.startTime, input.endTime).isEmpty()) {
            return AbpResponse.failed("请选择有效的被委托用户");
        }
        return AbpResponse.ok(null);
    }

    @PostMapping("/RemoveDelegation")
    public AbpResponse<Void> removeDelegation(@RequestBody IdRequest input, HttpServletRequest request) {
        AuthContext context = current(request);
        if (context == null) {
            return AbpResponse.denied("未登录或登录已过期");
        }
        if (!store.removeDelegation(context.user().id, parseLong(input == null ? null : input.id))) {
            return AbpResponse.failed(REMOVE_OWNERSHIP_ERROR);
        }
        return AbpResponse.ok(null);
    }

    @DeleteMapping("/RemoveDelegation")
    public AbpResponse<Void> removeDelegationByQuery(@RequestParam(name = "Id", required = false) String id,
                                                     HttpServletRequest request) {
        AuthContext context = current(request);
        if (context == null) {
            return AbpResponse.denied("未登录或登录已过期");
        }
        if (!store.removeDelegation(context.user().id, parseLong(id))) {
            return AbpResponse.failed(REMOVE_OWNERSHIP_ERROR);
        }
        return AbpResponse.ok(null);
    }

    @GetMapping("/GetActiveUserDelegations")
    public AbpResponse<List<UserDelegation>> activeDelegations(HttpServletRequest request) {
        AuthContext context = current(request);
        if (context == null) {
            return AbpResponse.denied("未登录或登录已过期");
        }
        return AbpResponse.ok(store.activeUserDelegations(context.user().id));
    }

    private AuthContext current(HttpServletRequest request) {
        return auth.currentUser(request.getHeader("Authorization")).orElse(null);
    }

    private Long parseLong(String value) {
        try {
            return value == null || value.isBlank() ? null : Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String validateDelegateNewUserInput(DelegateNewUserInput input) {
        Long targetId = targetUserId(input);
        if (targetId == null || targetId < 1 || isBlank(input.startTime) || isBlank(input.endTime)) {
            // 原 CreateUserDelegationDto 要求 TargetUserId、StartTime、EndTime 必填，TargetUserId 为正数。
            return "Validation failed";
        }
        LocalDateTime start = parseDateTime(input.startTime);
        LocalDateTime end = parseDateTime(input.endTime);
        if (start == null || end == null) {
            return "Validation failed";
        }
        if (start.isAfter(end)) {
            return START_AFTER_END_ERROR;
        }
        return null;
    }

    private Long targetUserId(DelegateNewUserInput input) {
        return input == null ? null : input.targetUserId == null ? input.userId : input.targetUserId;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private LocalDateTime parseDateTime(String value) {
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String validatePagedInput(int skipCount, int maxResultCount) {
        if (skipCount < 0 || maxResultCount < 1 || maxResultCount > 1000) {
            // 原 PagedAndSortedInputDto 要求 MaxResultCount 为 1-1000，SkipCount 不能为负。
            return "Validation failed";
        }
        return null;
    }

    public static class DelegateNewUserInput {
        public Long userId;
        public Long targetUserId;
        public String startTime;
        public String endTime;
    }
}
