package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.model.FriendItem;
import com.sgs.capability.model.UserItem;
import com.sgs.capability.security.AuthContext;
import com.sgs.capability.security.AuthService;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.CapabilityStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

/** Mirrors FriendshipAppService request/block/accept routes. */
@RestController
@RequestMapping("/api/services/app/Friendship")
@RequirePermission
public class FriendshipController {
    private static final String DUPLICATE_FRIENDSHIP_ERROR = "You already added this user.";
    private static final String SELF_FRIENDSHIP_ERROR = "You can not be a friend with yourself.";
    private static final String MISSING_FRIENDSHIP_ERROR_PREFIX = "Friendship does not exist between";

    private final AuthService auth;
    private final CapabilityStore store;

    public FriendshipController(AuthService auth, CapabilityStore store) {
        this.auth = auth;
        this.store = store;
    }

    @PostMapping("/CreateFriendshipRequest")
    public AbpResponse<FriendItem> createFriendshipRequest(@RequestBody UserIdentifierInput input,
                                                           HttpServletRequest request) {
        AuthContext context = current(request);
        if (context == null) {
            return AbpResponse.denied("未登录或登录已过期");
        }
        Long friendUserId = input == null ? null : input.userId;
        Integer friendTenantId = input == null ? null : input.tenantId;
        String validationError = validateUserIdentifier(friendUserId);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        if (isSelfFriendship(context, friendUserId, friendTenantId)) {
            return AbpResponse.failed(SELF_FRIENDSHIP_ERROR);
        }
        // Original FriendshipAppService rejects duplicate friendship requests.
        if (store.friendshipExists(context.user().id, friendUserId, friendTenantId)) {
            return AbpResponse.failed(DUPLICATE_FRIENDSHIP_ERROR);
        }
        return store.createFriendshipRequest(context.user().id, friendUserId, friendTenantId)
                .map(AbpResponse::ok)
                .orElseGet(() -> AbpResponse.failed("请选择有效用户"));
    }

    @PostMapping("/CreateFriendshipRequestByUserName")
    public AbpResponse<FriendItem> createFriendshipRequestByUserName(@RequestBody UserNameInput input,
                                                                     HttpServletRequest request) {
        AuthContext context = current(request);
        if (context == null) {
            return AbpResponse.denied("未登录或登录已过期");
        }
        Integer friendTenantId = resolveFriendTenantId(input == null ? null : input.tenancyName);
        if (friendTenantId == null && input != null && input.tenancyName != null && !input.tenancyName.equals(".")) {
            return AbpResponse.failed(tenantNotDefinedError(input.tenancyName));
        }
        UserItem targetUser = store.userByUserName(input == null ? null : input.userName).orElse(null);
        if (targetUser == null) {
            return AbpResponse.failed(tenantNotDefinedError(input == null ? null : input.tenancyName));
        }
        if (targetUser != null && isSelfFriendship(context, targetUser.id, friendTenantId)) {
            return AbpResponse.failed(SELF_FRIENDSHIP_ERROR);
        }
        if (targetUser != null && store.friendshipExists(context.user().id, targetUser.id, friendTenantId)) {
            return AbpResponse.failed(DUPLICATE_FRIENDSHIP_ERROR);
        }
        return store.createFriendshipRequest(context.user().id, targetUser == null ? null : targetUser.id, friendTenantId)
                .map(AbpResponse::ok)
                .orElseGet(() -> AbpResponse.failed("请选择有效用户名"));
    }

    @PostMapping("/BlockUser")
    public AbpResponse<Void> blockUser(@RequestBody UserIdentifierInput input, HttpServletRequest request) {
        AuthContext context = current(request);
        if (context == null) {
            return AbpResponse.denied("未登录或登录已过期");
        }
        Long friendUserId = input == null ? null : input.userId;
        Integer friendTenantId = input == null ? null : input.tenantId;
        String validationError = validateUserIdentifier(friendUserId);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        if (!store.blockFriend(context.user().id, friendUserId, friendTenantId)) {
            return AbpResponse.failed(missingFriendshipError(context, friendUserId, friendTenantId));
        }
        return AbpResponse.ok(null);
    }

    @PostMapping("/UnblockUser")
    public AbpResponse<Void> unblockUser(@RequestBody UserIdentifierInput input, HttpServletRequest request) {
        AuthContext context = current(request);
        if (context == null) {
            return AbpResponse.denied("未登录或登录已过期");
        }
        Long friendUserId = input == null ? null : input.userId;
        Integer friendTenantId = input == null ? null : input.tenantId;
        String validationError = validateUserIdentifier(friendUserId);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        if (!store.unblockFriend(context.user().id, friendUserId, friendTenantId)) {
            return AbpResponse.failed(missingFriendshipError(context, friendUserId, friendTenantId));
        }
        return AbpResponse.ok(null);
    }

    @PostMapping("/AcceptFriendshipRequest")
    public AbpResponse<Void> acceptFriendshipRequest(@RequestBody UserIdentifierInput input, HttpServletRequest request) {
        AuthContext context = current(request);
        if (context == null) {
            return AbpResponse.denied("未登录或登录已过期");
        }
        Long friendUserId = input == null ? null : input.userId;
        Integer friendTenantId = input == null ? null : input.tenantId;
        String validationError = validateUserIdentifier(friendUserId);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        if (!store.acceptFriendship(context.user().id, friendUserId, friendTenantId)) {
            return AbpResponse.failed(missingFriendshipError(context, friendUserId, friendTenantId));
        }
        return AbpResponse.ok(null);
    }

    private AuthContext current(HttpServletRequest request) {
        return auth.currentUser(request.getHeader("Authorization")).orElse(null);
    }

    private String validateUserIdentifier(Long userId) {
        if (userId == null || userId <= 0) {
            // 原好友 UserId 输入 DTO 都要求大于 0。
            return "Validation failed";
        }
        return null;
    }

    private boolean isSelfFriendship(AuthContext context, Long friendUserId, Integer friendTenantId) {
        return Objects.equals(context.user().id, friendUserId)
                && Objects.equals(context.tenantId(), friendTenantId == null ? context.tenantId() : friendTenantId);
    }

    private Integer resolveFriendTenantId(String tenancyName) {
        if (tenancyName == null || tenancyName.equals(".")) {
            return null;
        }
        // Original GetUserIdentifier validates tenancy before looking up the username.
        return store.tenantByTenancyName(tenancyName).map(tenant -> tenant.id).orElse(null);
    }

    private String tenantNotDefinedError(String tenancyName) {
        return "There is no tenant defined with name " + (tenancyName == null ? "." : tenancyName);
    }

    private String missingFriendshipError(AuthContext context, Long friendUserId, Integer friendTenantId) {
        return MISSING_FRIENDSHIP_ERROR_PREFIX
                + " " + context.tenantId() + ":" + context.user().id
                + " and " + friendTenantId + ":" + friendUserId;
    }

    public static class UserIdentifierInput {
        public Integer tenantId;
        public Long userId;
    }

    public static class UserNameInput {
        public String tenancyName;
        public String userName;
    }
}
