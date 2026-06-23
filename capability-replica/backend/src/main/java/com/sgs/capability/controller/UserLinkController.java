package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.dto.PageResult;
import com.sgs.capability.model.LinkedUserItem;
import com.sgs.capability.model.UserItem;
import com.sgs.capability.security.AuthContext;
import com.sgs.capability.security.AuthService;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.CapabilityStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

/** Mirrors UserLinkAppService linked account routes. */
@RestController
@RequestMapping("/api/services/app/UserLink")
@RequirePermission
public class UserLinkController {
    private final AuthService auth;
    private final CapabilityStore store;

    public UserLinkController(AuthService auth, CapabilityStore store) {
        this.auth = auth;
        this.store = store;
    }

    @PostMapping("/LinkToUser")
    public AbpResponse<Void> linkToUser(@RequestBody LinkToUserInput input, HttpServletRequest request) {
        AuthContext context = current(request);
        if (context == null) {
            return AbpResponse.denied("未登录或登录已过期");
        }
        String usernameOrEmail = input == null ? null : input.usernameOrEmailAddress;
        String validationError = validateLinkToUserInput(input);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        UserItem targetUser = store.userByUserNameOrEmail(usernameOrEmail).orElse(null);
        // Original UserLinkAppService rejects linking the signed-in account to itself.
        if (targetUser != null && Objects.equals(targetUser.id, context.user().id)) {
            return AbpResponse.failed("You can not link to same account!");
        }
        // Original UserLinkAppService also blocks accounts that must change password first.
        if (targetUser != null
                && targetUser.shouldChangePasswordOnNextLogin
                && store.passwordMatches(targetUser.id, input == null ? null : input.password)) {
            return AbpResponse.failed("You must change your password before linking this account!");
        }
        boolean linked = store.linkToUser(context.user().id, usernameOrEmail,
                input == null ? null : input.password);
        return linked ? AbpResponse.ok(null) : AbpResponse.failed("关联账号失败，请检查账号和密码");
    }

    @PostMapping("/GetLinkedUsers")
    public AbpResponse<PageResult<LinkedUserItem>> getLinkedUsers(@RequestBody(required = false) GetLinkedUsersInput input,
                                                                  HttpServletRequest request) {
        AuthContext context = current(request);
        if (context == null) {
            return AbpResponse.denied("未登录或登录已过期");
        }
        GetLinkedUsersInput safeInput = input == null ? new GetLinkedUsersInput() : input;
        String validationError = validatePagedInput(safeInput.skipCount, safeInput.maxResultCount);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        return AbpResponse.ok(store.linkedUsers(context.user().id, safeInput.skipCount, safeInput.maxResultCount,
                safeInput.sorting));
    }

    // Match the generated Angular client: GET with PascalCase paging keys.
    @GetMapping("/GetLinkedUsers")
    public AbpResponse<PageResult<LinkedUserItem>> getLinkedUsersByQuery(
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
        return AbpResponse.ok(store.linkedUsers(context.user().id, skipCount, maxResultCount, sorting));
    }

    @GetMapping("/GetRecentlyUsedLinkedUsers")
    public AbpResponse<ListResult<LinkedUserItem>> getRecentlyUsedLinkedUsers(HttpServletRequest request) {
        AuthContext context = current(request);
        if (context == null) {
            return AbpResponse.denied("未登录或登录已过期");
        }
        return AbpResponse.ok(new ListResult<>(store.recentlyUsedLinkedUsers(context.user().id)));
    }

    @PostMapping("/UnlinkUser")
    public AbpResponse<Void> unlinkUser(@RequestBody UnlinkUserInput input, HttpServletRequest request) {
        AuthContext context = current(request);
        if (context == null) {
            return AbpResponse.denied("未登录或登录已过期");
        }
        // Match the original guard: no linked account means unlink fails.
        if (!store.hasLinkedUsers(context.user().id)) {
            return AbpResponse.failed("You are not linked to any account");
        }
        store.unlinkUser(context.user().id, input == null ? null : input.userId);
        return AbpResponse.ok(null);
    }

    private AuthContext current(HttpServletRequest request) {
        return auth.currentUser(request.getHeader("Authorization")).orElse(null);
    }

    private String validateLinkToUserInput(LinkToUserInput input) {
        if (input == null || isBlank(input.usernameOrEmailAddress) || isBlank(input.password)) {
            // 原 LinkToUserInput 要求账号和密码必填。
            return "Validation failed";
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String validatePagedInput(int skipCount, int maxResultCount) {
        if (skipCount < 0 || maxResultCount < 1 || maxResultCount > 1000) {
            // 原 PagedAndSortedInputDto 要求 MaxResultCount 为 1-1000，SkipCount 不能为负。
            return "Validation failed";
        }
        return null;
    }

    public static class LinkToUserInput {
        public String tenancyName;
        public String usernameOrEmailAddress;
        public String password;
    }

    public static class GetLinkedUsersInput {
        public int maxResultCount = 10;
        public int skipCount;
        public String sorting;
    }

    public static class UnlinkUserInput {
        public Integer tenantId;
        public Long userId;
    }

    public record ListResult<T>(List<T> items) {
    }
}
