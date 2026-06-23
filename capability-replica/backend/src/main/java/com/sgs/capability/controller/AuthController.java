package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.model.EditionItem;
import com.sgs.capability.model.FeatureItem;
import com.sgs.capability.model.SystemSettingsItem;
import com.sgs.capability.model.TenantItem;
import com.sgs.capability.model.ThemeSettingsItem;
import com.sgs.capability.model.UserDelegation;
import com.sgs.capability.model.UserItem;
import com.sgs.capability.security.AuthContext;
import com.sgs.capability.security.AuthService;
import com.sgs.capability.service.CapabilityStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Lightweight auth replica for local UI development. */
@RestController
public class AuthController {
    private final AuthService auth;
    private final CapabilityStore store;

    public AuthController(AuthService auth, CapabilityStore store) {
        this.auth = auth;
        this.store = store;
    }

    @PostMapping("/api/TokenAuth/Authenticate")
    public AbpResponse<AuthResult> authenticate(@RequestBody(required = false) AuthInput input, HttpServletRequest request) {
        AuthInput safeInput = input == null ? new AuthInput() : input;
        String validationError = validateAuthenticateInput(safeInput);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        String loginName = safeInput.loginName();
        String password = passwordForAuthentication(safeInput);
        if (!hasText(loginName) || !hasText(password)) {
            return AbpResponse.denied("用户名或密码错误");
        }
        UserItem passwordMatchedUser = passwordMatchedActiveUser(loginName, password);
        if (passwordMatchedUser != null && passwordMatchedUser.shouldChangePasswordOnNextLogin) {
            String resetCode = store.setNewPasswordResetCode(passwordMatchedUser.id);
            recordLoginAttempt(passwordMatchedUser.id, loginName, "ShouldResetPassword", request);
            return AbpResponse.ok(AuthResult.passwordReset(passwordMatchedUser.id, resetCode, safeInput.returnUrl));
        }
        AuthService.LoginToken token;
        try {
            token = auth.authenticate(loginName, password,
                    safeInput.twoFactorVerificationCode, safeInput.rememberClient).orElse(null);
        } catch (AuthService.InvalidTwoFactorCodeException ex) {
            return AbpResponse.failed("InvalidSecurityCode");
        }
        if (token == null) {
            store.userByUserNameOrEmail(loginName)
                    .ifPresent(user -> recordLoginAttempt(user.id, loginName, "Failed", request));
            return AbpResponse.denied("用户名或密码错误");
        }
        recordLoginAttempt(token.userId(), loginName,
                token.requiresTwoFactorVerification() ? "RequiresTwoFactor" : "Success", request);
        return AbpResponse.ok(AuthResult.from(token,
                singleSignInReturnUrl(safeInput.returnUrl, token.userId(), token.tenantId(), safeInput.singleSignIn)));
    }

    @PostMapping("/api/TokenAuth/RefreshToken")
    public AbpResponse<RefreshTokenResult> refreshToken(@RequestBody(required = false) RefreshTokenInput input) {
        return auth.refresh(input == null ? null : input.refreshToken)
                .map(token -> AbpResponse.ok(new RefreshTokenResult(token.token(), token.encryptedToken(), token.expireInSeconds())))
                .orElseGet(() -> AbpResponse.denied("Refresh token is not valid!"));
    }

    @GetMapping("/api/TokenAuth/RefreshToken")
    public AbpResponse<RefreshTokenResult> refreshTokenGet(@RequestParam String refreshToken) {
        RefreshTokenInput input = new RefreshTokenInput();
        input.refreshToken = refreshToken;
        return refreshToken(input);
    }

    @PostMapping("/api/TokenAuth/LogOut")
    public AbpResponse<Void> logout(HttpServletRequest request) {
        auth.logout(request.getHeader("Authorization"));
        return AbpResponse.ok(null);
    }

    @GetMapping("/api/TokenAuth/LogOut")
    public AbpResponse<Void> logoutGet(HttpServletRequest request) {
        return logout(request);
    }

    @PostMapping("/api/TokenAuth/SendTwoFactorAuthCode")
    public AbpResponse<Void> sendTwoFactorAuthCode(@RequestBody(required = false) SendTwoFactorAuthCodeInput input) {
        String validationError = validateSendTwoFactorAuthCodeInput(input);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        return auth.sendTwoFactorCode(input == null ? null : input.userId, input == null ? "Email" : input.provider)
                .map(ignored -> AbpResponse.<Void>ok(null))
                .orElseGet(() -> AbpResponse.failed("SendSecurityCodeErrorMessage"));
    }

    @GetMapping("/api/TokenAuth/GetExternalAuthenticationProviders")
    public AbpResponse<List<ExternalLoginProviderInfoModel>> getExternalAuthenticationProviders() {
        SystemSettingsItem.ExternalLoginProviderSettings settings = store.hostSettings().externalLoginProviderSettings;
        List<ExternalLoginProviderInfoModel> providers = new ArrayList<>();
        if (hasText(settings.facebook.appId) && hasText(settings.facebook.appSecret)) {
            providers.add(new ExternalLoginProviderInfoModel("Facebook", settings.facebook.appId, Map.of()));
        }
        if (hasText(settings.google.clientId) && hasText(settings.google.clientSecret)) {
            providers.add(new ExternalLoginProviderInfoModel("Google", settings.google.clientId,
                    Map.of("UserInfoEndpoint", settings.google.userInfoEndpoint == null ? "" : settings.google.userInfoEndpoint)));
        }
        if (hasText(settings.microsoft.clientId) && hasText(settings.microsoft.clientSecret)) {
            providers.add(new ExternalLoginProviderInfoModel("Microsoft", settings.microsoft.clientId, Map.of()));
        }
        return AbpResponse.ok(providers);
    }

    @PostMapping("/api/TokenAuth/ExternalAuthenticate")
    public AbpResponse<ExternalAuthenticateResultModel> externalAuthenticate(@RequestBody(required = false) ExternalAuthenticateModel input) {
        String validationError = validateExternalAuthenticateInput(input);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        if (getExternalAuthenticationProviders().result.stream().noneMatch(provider -> provider.name().equals(input.authProvider))) {
            return AbpResponse.failed("External authentication provider is not configured");
        }

        UserItem user = store.userByUserNameOrEmail(input.providerKey).orElseGet(() -> registerExternalUser(input));
        return auth.issueTokenForUser(user.id)
                .map(token -> AbpResponse.ok(ExternalAuthenticateResultModel.from(token, externalReturnUrl(input, user.id))))
                .orElseGet(() -> AbpResponse.denied("External user is inactive"));
    }

    @GetMapping("/api/TokenAuth/TestNotification")
    public AbpResponse<Void> testNotification(@RequestParam(required = false) String message,
                                              @RequestParam(required = false) String severity,
                                              HttpServletRequest request) {
        AuthContext context = auth.currentUser(request.getHeader("Authorization")).orElse(null);
        store.notifySimpleMessage(context == null ? 1L : context.user().id, message, severity);
        return AbpResponse.ok(null);
    }

    @PostMapping("/api/TokenAuth/DelegatedImpersonatedAuthenticate")
    public AbpResponse<AuthResult> delegatedImpersonatedAuthenticate(@RequestParam Long userDelegationId,
                                                                     @RequestParam String impersonationToken) {
        UserDelegation delegation = store.userDelegations().stream()
                .filter(item -> item.id != null && item.id.equals(userDelegationId))
                .findFirst()
                .orElse(null);
        if (delegation == null) {
            return AbpResponse.denied("User delegation is not valid");
        }
        return auth.impersonatedAuthenticate(impersonationToken)
                .filter(token -> Objects.equals(token.userId(), delegation.sourceUserId))
                .map(token -> AbpResponse.ok(AuthResult.from(token)))
                .orElseGet(() -> AbpResponse.denied("Impersonation token is not valid"));
    }

    @GetMapping("/api/TokenAuth/ImpersonatedAuthenticate")
    public AbpResponse<AuthResult> impersonatedAuthenticate(@RequestParam String impersonationToken) {
        return auth.impersonatedAuthenticate(impersonationToken)
                .map(token -> AbpResponse.ok(AuthResult.from(token)))
                .orElseGet(() -> AbpResponse.denied("Impersonation token is not valid"));
    }

    @PostMapping("/api/TokenAuth/ImpersonatedAuthenticate")
    public AbpResponse<AuthResult> impersonatedAuthenticatePost(@RequestParam String impersonationToken) {
        return impersonatedAuthenticate(impersonationToken);
    }

    @GetMapping("/api/TokenAuth/LinkedAccountAuthenticate")
    public AbpResponse<SwitchedAccountAuthenticateResult> linkedAccountAuthenticate(@RequestParam String switchAccountToken) {
        return auth.linkedAccountAuthenticate(switchAccountToken)
                .map(token -> AbpResponse.ok(new SwitchedAccountAuthenticateResult(token.token(), token.encryptedToken(),
                        token.expireInSeconds())))
                .orElseGet(() -> AbpResponse.denied("Switch account token is not valid"));
    }

    @PostMapping("/api/TokenAuth/LinkedAccountAuthenticate")
    public AbpResponse<SwitchedAccountAuthenticateResult> linkedAccountAuthenticatePost(@RequestParam String switchAccountToken) {
        return linkedAccountAuthenticate(switchAccountToken);
    }

    @GetMapping("/api/services/app/Session/GetCurrentLoginInformations")
    public AbpResponse<LoginInfo> loginInfo(HttpServletRequest request) {
        AuthContext context = auth.currentUser(request.getHeader("Authorization")).orElse(null);
        if (context == null) {
            return AbpResponse.denied(authorizationError(request));
        }
        UserItem current = context.user();
        LoginInfo info = new LoginInfo();
        info.user = userInfo(current);
        info.tenant = tenantInfo(context.tenantId());
        info.application = applicationInfo();
        info.theme = store.uiManagementSettings().stream().filter(item -> item.isActive).findFirst()
                .orElseGet(() -> store.uiManagementSettings().stream().findFirst().orElse(null));
        info.permissions = context.permissions();
        return AbpResponse.ok(info);
    }

    @PostMapping("/api/services/app/Session/UpdateUserSignInToken")
    public AbpResponse<UpdateUserSignInTokenOutput> updateUserSignInToken(HttpServletRequest request) {
        AuthContext context = auth.currentUser(request.getHeader("Authorization")).orElse(null);
        if (context == null) {
            return AbpResponse.denied(authorizationError(request));
        }
        String token = store.updateUserSignInToken(context.user().id);
        return AbpResponse.ok(new UpdateUserSignInTokenOutput(token, encode(context.user().id), encode(context.tenantId())));
    }

    @PutMapping("/api/services/app/Session/UpdateUserSignInToken")
    public AbpResponse<UpdateUserSignInTokenOutput> putUpdateUserSignInToken(HttpServletRequest request) {
        return updateUserSignInToken(request);
    }

    private String authorizationError(HttpServletRequest request) {
        return auth.delegationValidationError(request.getHeader("Authorization"))
                .orElse("未登录或登录已过期");
    }

    private String validateAuthenticateInput(AuthInput input) {
        // 原 AuthenticateModel 要求登录名必填且最长 256，密码载荷必填且最长 32。
        String loginName = input == null ? null : input.loginName();
        String password = input == null ? null : input.password;
        if (!hasText(loginName) || !hasText(password)) {
            return "Validation failed";
        }
        if (loginName.length() > 256 || password.length() > 32) {
            return "Validation failed";
        }
        return null;
    }

    private String validateSendTwoFactorAuthCodeInput(SendTwoFactorAuthCodeInput input) {
        // 原 SendTwoFactorAuthCodeModel 要求 userId >= 1，并且 provider 必填。
        if (input == null || input.userId == null || input.userId < 1 || !hasText(input.provider)) {
            return "Validation failed";
        }
        return null;
    }

    private String validateExternalAuthenticateInput(ExternalAuthenticateModel input) {
        // 原 ExternalAuthenticateModel 要求三个外部登录字段必填，并限制 provider/key 长度。
        if (input == null || !hasText(input.authProvider) || !hasText(input.providerKey)
                || !hasText(input.providerAccessCode)) {
            return "Validation failed";
        }
        if (input.authProvider.length() > 128 || input.providerKey.length() > 256) {
            return "Validation failed";
        }
        return null;
    }

    /** Login input matching the old token endpoint shape. */
    public static class AuthInput {
        public String userName;
        public String userNameOrEmailAddress;
        public String password;
        public String twoFactorVerificationCode;
        public boolean rememberClient;
        public String returnUrl;
        public Boolean singleSignIn;

        public String loginName() {
            return userNameOrEmailAddress == null || userNameOrEmailAddress.isBlank() ? userName : userNameOrEmailAddress;
        }
    }

    /** Token payload matching the old login response. */
    public static class AuthResult {
        public String accessToken;
        public String encryptedAccessToken;
        public int expireInSeconds;
        public long userId;
        public boolean requiresTwoFactorVerification;
        public List<String> twoFactorAuthProviders;
        public String twoFactorRememberClientToken;
        public boolean shouldResetPassword;
        public String passwordResetCode;
        public String returnUrl;
        public String refreshToken;
        public int refreshTokenExpireInSeconds;

        public static AuthResult from(AuthService.LoginToken token) {
            return from(token, null);
        }

        public static AuthResult from(AuthService.LoginToken token, String returnUrl) {
            AuthResult result = new AuthResult();
            result.accessToken = token.token();
            result.encryptedAccessToken = token.encryptedToken();
            result.expireInSeconds = token.expireInSeconds();
            result.userId = token.userId();
            result.requiresTwoFactorVerification = token.requiresTwoFactorVerification();
            result.twoFactorAuthProviders = token.twoFactorAuthProviders();
            result.twoFactorRememberClientToken = token.twoFactorRememberClientToken();
            result.refreshToken = token.refreshToken();
            result.refreshTokenExpireInSeconds = token.refreshTokenExpireInSeconds();
            result.returnUrl = returnUrl;
            return result;
        }

        public static AuthResult passwordReset(long userId, String passwordResetCode, String returnUrl) {
            AuthResult result = new AuthResult();
            result.shouldResetPassword = true;
            result.passwordResetCode = passwordResetCode;
            result.userId = userId;
            result.returnUrl = returnUrl;
            return result;
        }
    }

    /** Current login information used by the React shell. */
    public static class LoginInfo {
        public UserInfo user;
        public TenantLoginInfo tenant;
        public ApplicationInfo application;
        public ThemeSettingsItem theme;
        public List<String> permissions;
    }

    /** User profile returned by SessionAppService. */
    public record UserInfo(long id, String name, String surname, String userName, String emailAddress,
                           String profilePictureId) {
    }

    /** Tenant details returned during app bootstrap. */
    public record TenantLoginInfo(Integer id, String tenancyName, String name, String logoId, String logoFileType,
                                  String customCssId, String subscriptionEndDateUtc, boolean isInTrialPeriod,
                                  int subscriptionPaymentType, EditionInfo edition, String creationTime,
                                  int paymentPeriodType, String subscriptionDateString,
                                  String creationTimeString) {
    }

    /** Edition summary copied into the session tenant block. */
    public record EditionInfo(Integer id, String displayName, Integer trialDayCount, java.math.BigDecimal monthlyPrice,
                              java.math.BigDecimal annualPrice, boolean isHighestEdition, boolean isFree) {
    }

    /** Application metadata returned by SessionAppService. */
    public record ApplicationInfo(String version, String releaseDate, String currency, String currencySign,
                                  boolean allowTenantsToChangeEmailSettings, boolean userDelegationIsEnabled,
                                  Map<String, Boolean> features, Map<String, String> settings) {
    }

    public record UpdateUserSignInTokenOutput(String signInToken, String encodedUserId, String encodedTenantId) {
    }

    public static class RefreshTokenInput {
        public String refreshToken;
    }

    public record RefreshTokenResult(String accessToken, String encryptedAccessToken, int expireInSeconds) {
    }

    public static class SendTwoFactorAuthCodeInput {
        public Long userId;
        public String provider;
    }

    public record SwitchedAccountAuthenticateResult(String accessToken, String encryptedAccessToken, int expireInSeconds) {
    }

    public record ExternalLoginProviderInfoModel(String name, String clientId, Map<String, String> additionalParams) {
    }

    /** Body copied from TokenAuthController.ExternalAuthenticate. */
    public static class ExternalAuthenticateModel {
        public String authProvider;
        public String providerKey;
        public String providerAccessCode;
        public String returnUrl;
        public boolean singleSignIn;
    }

    /** External login response shape used by the original SPA. */
    public static class ExternalAuthenticateResultModel {
        public String accessToken;
        public String encryptedAccessToken;
        public int expireInSeconds;
        public boolean waitingForActivation;
        public String returnUrl;
        public String refreshToken;
        public int refreshTokenExpireInSeconds;

        public static ExternalAuthenticateResultModel from(AuthService.LoginToken token, String returnUrl) {
            ExternalAuthenticateResultModel result = new ExternalAuthenticateResultModel();
            result.accessToken = token.token();
            result.encryptedAccessToken = token.encryptedToken();
            result.expireInSeconds = token.expireInSeconds();
            result.waitingForActivation = false;
            result.returnUrl = returnUrl;
            result.refreshToken = token.refreshToken();
            result.refreshTokenExpireInSeconds = token.refreshTokenExpireInSeconds();
            return result;
        }
    }

    private UserInfo userInfo(UserItem user) {
        return new UserInfo(user.id, user.name, user.surname, user.userName, user.emailAddress,
                user.profilePictureId == null ? null : user.profilePictureId.toString());
    }

    private TenantLoginInfo tenantInfo(Integer tenantId) {
        TenantItem tenant = store.tenant(tenantId).orElse(null);
        if (tenant == null) {
            return null;
        }
        EditionInfo edition = store.edition(tenant.editionId).map(this::editionInfo).orElse(null);
        String subscriptionDate = tenant.subscriptionEndDateUtc == null || tenant.subscriptionEndDateUtc.isBlank()
                ? "Unlimited"
                : tenant.subscriptionEndDateUtc;
        return new TenantLoginInfo(tenant.id, tenant.tenancyName, tenant.name, tenant.logoId, tenant.logoFileType, tenant.customCssId,
                tenant.subscriptionEndDateUtc, tenant.isInTrialPeriod,
                tenant.subscriptionPaymentType == null ? 0 : tenant.subscriptionPaymentType, edition, tenant.creationTime, 30,
                subscriptionDate, dateOnly(tenant.creationTime));
    }

    private EditionInfo editionInfo(EditionItem edition) {
        boolean highest = store.editions().stream()
                .map(item -> item.monthlyPrice == null ? java.math.BigDecimal.ZERO : item.monthlyPrice)
                .max(java.math.BigDecimal::compareTo)
                .map(max -> max.compareTo(edition.monthlyPrice == null ? java.math.BigDecimal.ZERO : edition.monthlyPrice) == 0)
                .orElse(false);
        return new EditionInfo(edition.id, edition.displayName, edition.trialDayCount, edition.monthlyPrice,
                edition.annualPrice, highest, edition.isFree);
    }

    private ApplicationInfo applicationInfo() {
        Map<String, Boolean> features = new LinkedHashMap<>();
        for (FeatureItem feature : store.features()) {
            features.put(feature.name, Boolean.parseBoolean(String.valueOf(feature.defaultValue)));
        }
        Map<String, String> settings = new LinkedHashMap<>();
        settings.put("Ability.Description", store.abilitySettings().description);
        return new ApplicationInfo("8.8.0.0", "2024-01-01T00:00:00", "USD", "$", false, true, features, settings);
    }

    private String encode(Object value) {
        return Base64.getEncoder().encodeToString(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
    }

    private String externalReturnUrl(ExternalAuthenticateModel input, long userId) {
        String returnUrl = input.returnUrl == null || input.returnUrl.isBlank() ? "/" : input.returnUrl;
        if (!input.singleSignIn) {
            return returnUrl;
        }
        return addSingleSignInParametersToReturnUrl(returnUrl, userId, 1);
    }

    private String singleSignInReturnUrl(String returnUrl, long userId, Integer tenantId, Boolean singleSignIn) {
        if (!Boolean.TRUE.equals(singleSignIn)) {
            return returnUrl;
        }
        String safeReturnUrl = returnUrl == null || returnUrl.isBlank() ? "/" : returnUrl;
        return addSingleSignInParametersToReturnUrl(safeReturnUrl, userId, tenantId);
    }

    private String addSingleSignInParametersToReturnUrl(String returnUrl, long userId, Integer tenantId) {
        String signInToken = store.updateUserSignInToken(userId);
        String delimiter = returnUrl.contains("?") ? "&" : "?";
        String result = returnUrl + delimiter + "accessToken=" + signInToken + "&userId=" + encode(userId);
        if (tenantId != null) {
            result += "&tenantId=" + encode(tenantId);
        }
        return result;
    }

    private UserItem registerExternalUser(ExternalAuthenticateModel input) {
        UserItem user = new UserItem();
        String provider = input.authProvider == null ? "external" : input.authProvider.toLowerCase();
        String key = input.providerKey == null ? "user" : input.providerKey;
        user.name = input.authProvider == null || input.authProvider.isBlank() ? "External" : input.authProvider;
        user.surname = "User";
        user.userName = provider + "_" + key.replaceAll("[^A-Za-z0-9._-]", "_");
        user.emailAddress = key.contains("@") ? key : user.userName + "@external.local";
        user.isEmailConfirmed = true;
        return store.registerUser(user, "123qwe");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String passwordForAuthentication(AuthInput input) {
        String password = input.password;
        if (password == null || password.isBlank()) {
            return password;
        }
        String decoded = decodeOriginalPassword(password);
        return decoded == null ? "" : decoded;
    }

    private String decodeOriginalPassword(String password) {
        try {
            return new String(Base64.getDecoder().decode(password), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private UserItem passwordMatchedActiveUser(String loginName, String password) {
        if (!hasText(loginName) || !hasText(password)) {
            return null;
        }
        return store.userByUserNameOrEmail(loginName)
                .filter(user -> user.isActive)
                .filter(user -> store.passwordMatches(user.id, password))
                .orElse(null);
    }

    private String dateOnly(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            return LocalDateTime.parse(value).toLocalDate().toString();
        } catch (Exception ex) {
            return value;
        }
    }

    private void recordLoginAttempt(Long userId, String userNameOrEmail, String result, HttpServletRequest request) {
        store.recordLoginAttempt(userId, userNameOrEmail, result, request.getRemoteAddr(), "Local Browser",
                request.getHeader("User-Agent"));
    }
}
