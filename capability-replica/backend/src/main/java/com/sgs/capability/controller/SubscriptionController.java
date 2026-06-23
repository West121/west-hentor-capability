package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.security.AuthContext;
import com.sgs.capability.security.AuthService;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.CapabilityStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

/** Mirrors SubscriptionAppService recurring payment routes. */
@RestController
@RequestMapping("/api/services/app/Subscription")
@RequirePermission("Pages.Administration.SubscriptionManagement")
public class SubscriptionController {
    private final CapabilityStore store;
    private final AuthService auth;

    public SubscriptionController(CapabilityStore store, AuthService auth) {
        this.store = store;
        this.auth = auth;
    }

    @PostMapping("/DisableRecurringPayments")
    public AbpResponse<Void> disableRecurringPayments(HttpServletRequest request) {
        store.disableRecurringPayments(currentTenantId(request));
        return AbpResponse.ok(null);
    }

    @PostMapping("/EnableRecurringPayments")
    public AbpResponse<Void> enableRecurringPayments(HttpServletRequest request) {
        store.enableRecurringPayments(currentTenantId(request));
        return AbpResponse.ok(null);
    }

    private Integer currentTenantId(HttpServletRequest request) {
        return auth.currentUser(request.getHeader("Authorization"))
                .map(AuthContext::tenantId)
                .orElse(null);
    }
}
