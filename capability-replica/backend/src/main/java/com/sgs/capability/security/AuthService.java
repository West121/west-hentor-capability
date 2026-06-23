package com.sgs.capability.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.model.UserItem;
import com.sgs.capability.service.CapabilityStore;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;

/** Signed JWT and refresh-token service for the replica's ABP-style authentication flow. */
@Service
public class AuthService {
    private static final int ACCESS_TOKEN_SECONDS = 86400;
    private static final int REFRESH_TOKEN_SECONDS = 604800;
    private static final String JWT_SECRET = "capability-replica-local-jwt-secret-change-before-production";
    private static final String USER_DELEGATION_ERROR = "ThereIsNoActiveUserDelegationBetweenYourUserAndCurrentUser";

    private final CapabilityStore store;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, TokenSession> refreshTokens = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, TokenSession> impersonationTokens = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, TokenSession> switchAccountTokens = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, String> twoFactorCodes = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, Boolean> twoFactorChallenges = new ConcurrentHashMap<>();

    public AuthService(CapabilityStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
    }

    public Optional<LoginToken> authenticate(String userName, String password) {
        return authenticate(userName, password, null, false);
    }

    public Optional<LoginToken> authenticate(String userName, String password, String twoFactorCode, boolean rememberClient) {
        if (userName == null || userName.isBlank() || password == null || password.isBlank()) {
            return Optional.empty();
        }
        return store.userByUserNameOrEmail(userName)
                .filter(user -> user.isActive)
                .filter(user -> store.passwordMatches(user.id, password))
                .map(user -> {
                    if (user.isTwoFactorEnabled && (twoFactorCode == null || twoFactorCode.isBlank())) {
                        twoFactorChallenges.put(user.id, true);
                        twoFactorCodes.remove(user.id);
                        return LoginToken.twoFactor(user.id, defaultTenantId(), null);
                    }
                    if (user.isTwoFactorEnabled && !validTwoFactorCode(user.id, twoFactorCode)) {
                        throw new InvalidTwoFactorCodeException();
                    }
                    String twoFactorRememberToken = user.isTwoFactorEnabled && rememberClient ? rememberToken(user.id) : null;
                    twoFactorChallenges.remove(user.id);
                    twoFactorCodes.remove(user.id);
                    store.markLogin(user.id);
                    return issueLoginToken(user.id, defaultTenantId(), twoFactorRememberToken);
                });
    }

    public Optional<LoginToken> issueTokenForUser(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return store.user(userId)
                .filter(user -> user.isActive)
                .map(user -> {
                    store.markLogin(user.id);
                    return issueLoginToken(user.id, defaultTenantId());
                });
    }

    public Optional<AuthContext> currentUser(String authorizationHeader) {
        String token = bearerToken(authorizationHeader);
        if (token == null) {
            return Optional.empty();
        }
        TokenClaims claims = parseAccessToken(token).orElse(null);
        if (claims == null) {
            return Optional.empty();
        }
        if (delegationValidationError(claims).isPresent()) {
            return Optional.empty();
        }
        return store.user(claims.userId)
                .map(user -> new AuthContext(user, claims.tenantId, store.permissionsForUser(user.id),
                        claims.impersonatorUserId, claims.impersonatorTenantId));
    }

    public Optional<String> delegationValidationError(String authorizationHeader) {
        String token = bearerToken(authorizationHeader);
        if (token == null) {
            return Optional.empty();
        }
        TokenClaims claims = parseAccessToken(token).orElse(null);
        return claims == null ? Optional.empty() : delegationValidationError(claims);
    }

    public boolean hasPermission(AuthContext context, String permission) {
        return permission == null || permission.isBlank() || context.permissions().contains(permission);
    }

    public Optional<LoginToken> refresh(String refreshToken) {
        TokenSession session = validSession(refreshTokens.get(refreshToken));
        if (session == null) {
            return Optional.empty();
        }
        return store.user(session.userId).filter(user -> user.isActive).map(user -> {
            String accessToken = createAccessToken(user.id, session.tenantId, session.impersonatorUserId,
                    session.impersonatorTenantId);
            return new LoginToken(accessToken, encryptedAccessToken(accessToken), refreshToken, user.id, session.tenantId, false,
                    List.of(), null, ACCESS_TOKEN_SECONDS, REFRESH_TOKEN_SECONDS);
        });
    }

    public void logout(String authorizationHeader) {
        parseAccessToken(bearerToken(authorizationHeader)).ifPresent(claims ->
                refreshTokens.entrySet().removeIf(entry -> entry.getValue().userId.equals(claims.userId)));
    }

    public Optional<String> sendTwoFactorCode(Long userId, String provider) {
        if (userId == null || !Boolean.TRUE.equals(twoFactorChallenges.get(userId))) {
            return Optional.empty();
        }
        String code = String.valueOf(ThreadLocalRandom.current().nextInt(100000, 1000000));
        twoFactorCodes.put(userId, code);
        return Optional.of((provider == null || provider.isBlank() ? "Email" : provider) + ":" + code);
    }

    public String createImpersonationToken(Long userId, Integer tenantId) {
        return createImpersonationToken(userId, tenantId, null, null);
    }

    public String createImpersonationToken(Long userId, Integer tenantId, Long impersonatorUserId, Integer impersonatorTenantId) {
        String token = "impersonate-" + UUID.randomUUID();
        impersonationTokens.put(token, new TokenSession(userId, tenantId, Instant.now().plusSeconds(300),
                impersonatorUserId, impersonatorTenantId));
        return token;
    }

    public Optional<LoginToken> impersonatedAuthenticate(String token) {
        TokenSession session = validSession(impersonationTokens.remove(token));
        if (session == null) {
            return Optional.empty();
        }
        return store.user(session.userId).map(user -> issueLoginToken(user.id, session.tenantId,
                session.impersonatorUserId, session.impersonatorTenantId));
    }

    public String createSwitchAccountToken(Long userId, Integer tenantId) {
        return createSwitchAccountToken(userId, tenantId, null, null);
    }

    public String createSwitchAccountToken(Long userId, Integer tenantId, Long impersonatorUserId, Integer impersonatorTenantId) {
        String token = "switch-" + UUID.randomUUID();
        switchAccountTokens.put(token, new TokenSession(userId, tenantId, Instant.now().plusSeconds(300),
                impersonatorUserId, impersonatorTenantId));
        return token;
    }

    public Optional<LoginToken> linkedAccountAuthenticate(String token) {
        TokenSession session = validSession(switchAccountTokens.remove(token));
        if (session == null) {
            return Optional.empty();
        }
        return store.user(session.userId).map(user -> issueLoginToken(user.id, session.tenantId,
                session.impersonatorUserId, session.impersonatorTenantId));
    }

    public String encryptedAccessToken(String accessToken) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(accessToken.getBytes(StandardCharsets.UTF_8));
    }

    private LoginToken issueLoginToken(long userId, Integer tenantId) {
        return issueLoginToken(userId, tenantId, null);
    }

    private LoginToken issueLoginToken(long userId, Integer tenantId, String twoFactorRememberClientToken) {
        return issueLoginToken(userId, tenantId, null, null, twoFactorRememberClientToken);
    }

    private LoginToken issueLoginToken(long userId, Integer tenantId, Long impersonatorUserId, Integer impersonatorTenantId) {
        return issueLoginToken(userId, tenantId, impersonatorUserId, impersonatorTenantId, null);
    }

    private LoginToken issueLoginToken(long userId, Integer tenantId, Long impersonatorUserId, Integer impersonatorTenantId,
                                       String twoFactorRememberClientToken) {
        Integer effectiveTenantId = tenantId == null ? defaultTenantId() : tenantId;
        String accessToken = createAccessToken(userId, effectiveTenantId, impersonatorUserId, impersonatorTenantId);
        String refreshToken = "refresh-" + UUID.randomUUID();
        refreshTokens.put(refreshToken, new TokenSession(userId, effectiveTenantId,
                Instant.now().plusSeconds(REFRESH_TOKEN_SECONDS), impersonatorUserId, impersonatorTenantId));
        return new LoginToken(accessToken, encryptedAccessToken(accessToken), refreshToken, userId, effectiveTenantId, false,
                List.of(), twoFactorRememberClientToken, ACCESS_TOKEN_SECONDS, REFRESH_TOKEN_SECONDS);
    }

    private String createAccessToken(long userId, Integer tenantId, Long impersonatorUserId, Integer impersonatorTenantId) {
        try {
            String header = base64Json(Map.of("alg", "HS256", "typ", "JWT"));
            long now = Instant.now().getEpochSecond();
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("sub", String.valueOf(userId));
            claims.put("userId", userId);
            claims.put("tenantId", tenantId);
            claims.put("iat", now);
            claims.put("exp", now + ACCESS_TOKEN_SECONDS);
            claims.put("iss", "capability-replica");
            if (impersonatorUserId != null) {
                claims.put("impersonatorUserId", impersonatorUserId);
            }
            if (impersonatorTenantId != null) {
                claims.put("impersonatorTenantId", impersonatorTenantId);
            }
            String payload = base64Json(claims);
            String unsigned = header + "." + payload;
            return unsigned + "." + sign(unsigned);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to create local JWT", ex);
        }
    }

    private Optional<TokenClaims> parseAccessToken(String token) {
        if (token == null || token.isBlank() || token.split("\\.").length != 3) {
            return Optional.empty();
        }
        try {
            String[] parts = token.split("\\.");
            String unsigned = parts[0] + "." + parts[1];
            if (!sign(unsigned).equals(parts[2])) {
                return Optional.empty();
            }
            Map<String, Object> payload = objectMapper.readValue(Base64.getUrlDecoder().decode(parts[1]),
                    new TypeReference<>() {
                    });
            long exp = ((Number) payload.getOrDefault("exp", 0)).longValue();
            if (Instant.now().getEpochSecond() >= exp) {
                return Optional.empty();
            }
            Object userId = payload.get("userId");
            if (!(userId instanceof Number number)) {
                return Optional.empty();
            }
            Object tenantId = payload.get("tenantId");
            Integer parsedTenantId = tenantId instanceof Number tenantNumber ? tenantNumber.intValue() : defaultTenantId();
            Object impersonatorUserId = payload.get("impersonatorUserId");
            Long parsedImpersonatorUserId = impersonatorUserId instanceof Number impersonatorUserNumber
                    ? impersonatorUserNumber.longValue() : null;
            Object impersonatorTenantId = payload.get("impersonatorTenantId");
            Integer parsedImpersonatorTenantId = impersonatorTenantId instanceof Number impersonatorTenantNumber
                    ? impersonatorTenantNumber.intValue() : null;
            return Optional.of(new TokenClaims(number.longValue(), parsedTenantId, parsedImpersonatorUserId,
                    parsedImpersonatorTenantId));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private String base64Json(Map<String, Object> value) throws Exception {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(objectMapper.writeValueAsBytes(value));
    }

    private String sign(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(JWT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private boolean validTwoFactorCode(Long userId, String code) {
        return code != null && code.equals(twoFactorCodes.get(userId));
    }

    private String rememberToken(long userId) {
        return "remember-" + userId + "-" + UUID.randomUUID();
    }

    private Integer defaultTenantId() {
        // 当前复制版的种子用户属于默认租户。
        return 1;
    }

    private Optional<String> delegationValidationError(TokenClaims claims) {
        if (claims.impersonatorUserId == null) {
            return Optional.empty();
        }
        if (store.permissionsForUser(claims.impersonatorUserId).contains("Pages.Administration.Users.Impersonation")) {
            return Optional.empty();
        }
        if (store.hasActiveDelegation(claims.userId, claims.impersonatorUserId)) {
            return Optional.empty();
        }
        return Optional.of(USER_DELEGATION_ERROR);
    }

    private TokenSession validSession(TokenSession session) {
        if (session == null || session.expiresAt.isBefore(Instant.now())) {
            return null;
        }
        return session;
    }

    private String bearerToken(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        return header.substring("Bearer ".length()).trim();
    }

    private record TokenClaims(Long userId, Integer tenantId, Long impersonatorUserId, Integer impersonatorTenantId) {
    }

    private record TokenSession(Long userId, Integer tenantId, Instant expiresAt,
                                Long impersonatorUserId, Integer impersonatorTenantId) {
    }

    public record LoginToken(String token, String encryptedToken, String refreshToken, long userId, Integer tenantId,
                             boolean requiresTwoFactorVerification, List<String> twoFactorAuthProviders,
                             String twoFactorRememberClientToken, int expireInSeconds,
                             int refreshTokenExpireInSeconds) {
        public static LoginToken twoFactor(long userId, Integer tenantId, String rememberToken) {
            return new LoginToken(null, null, null, userId, tenantId, true, List.of("Email", "Sms"),
                    rememberToken, ACCESS_TOKEN_SECONDS, REFRESH_TOKEN_SECONDS);
        }
    }

    public static class InvalidTwoFactorCodeException extends RuntimeException {
    }
}
