package com.sgs.capability.security;

import com.sgs.capability.model.UserItem;

import java.util.List;

/** Request-scoped user, tenant, and impersonator details resolved from the bearer token. */
public record AuthContext(UserItem user, Integer tenantId, List<String> permissions,
                          Long impersonatorUserId, Integer impersonatorTenantId) {
}
