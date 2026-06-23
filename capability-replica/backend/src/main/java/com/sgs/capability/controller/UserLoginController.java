package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.dto.ListResult;
import com.sgs.capability.model.UserLoginAttemptItem;
import com.sgs.capability.security.AuthContext;
import com.sgs.capability.security.AuthService;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.CapabilityStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Mirrors UserLoginAppService recent login attempt route. */
@RestController
@RequestMapping("/api/services/app/UserLogin")
@RequirePermission
public class UserLoginController {
    private final AuthService auth;
    private final CapabilityStore store;

    public UserLoginController(AuthService auth, CapabilityStore store) {
        this.auth = auth;
        this.store = store;
    }

    @GetMapping("/GetRecentUserLoginAttempts")
    public AbpResponse<ListResult<UserLoginAttemptItem>> getRecentUserLoginAttempts(HttpServletRequest request) {
        AuthContext context = auth.currentUser(request.getHeader("Authorization")).orElse(null);
        if (context == null) {
            return AbpResponse.denied("未登录或登录已过期");
        }
        return AbpResponse.ok(new ListResult<>(store.userLoginAttempts(context.user().id)));
    }
}
