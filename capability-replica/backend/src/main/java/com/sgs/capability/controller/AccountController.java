package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.model.SystemSettingsItem;
import com.sgs.capability.model.TenantItem;
import com.sgs.capability.model.UserDelegation;
import com.sgs.capability.model.UserItem;
import com.sgs.capability.security.AbpSimpleStringCipher;
import com.sgs.capability.security.AuthContext;
import com.sgs.capability.security.AuthService;
import com.sgs.capability.security.AuthorizationInterceptor;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.CapabilityStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Mirrors AccountAppService registration, reset, activation, and account switching routes. */
@RestController
@RequestMapping("/api/services/app/Account")
public class AccountController {
    private final CapabilityStore store;
    private final AuthService auth;
    private final AbpSimpleStringCipher stringCipher = new AbpSimpleStringCipher();
    private final String serverRootAddressFormat;

    public AccountController(CapabilityStore store, AuthService auth,
                             @Value("${app.server-root-address:http://localhost:9901/}") String serverRootAddressFormat) {
        this.store = store;
        this.auth = auth;
        this.serverRootAddressFormat = serverRootAddressFormat;
    }

    @PostMapping("/IsTenantAvailable")
    public AbpResponse<IsTenantAvailableOutput> isTenantAvailable(@RequestBody(required = false) IsTenantAvailableInput input) {
        if (input == null || input.tenancyName == null || safe(input.tenancyName).length() > 64) {
            // 原 ABP DTO 对 TenancyName 使用 Required 和 MaxLength(64)。
            return AbpResponse.failed("Validation failed");
        }
        TenantItem tenant = store.tenantByTenancyName(input == null ? null : input.tenancyName).orElse(null);
        if (tenant == null) {
            return AbpResponse.ok(new IsTenantAvailableOutput(3, null, null));
        }
        if (!tenant.isActive) {
            return AbpResponse.ok(new IsTenantAvailableOutput(2, null, null));
        }
        return AbpResponse.ok(new IsTenantAvailableOutput(1, tenant.id,
                serverRootAddress(tenant.tenancyName)));
    }

    @PostMapping("/ResolveTenantId")
    public AbpResponse<Integer> resolveTenantId(@RequestBody(required = false) ResolveTenantIdInput input,
                                                HttpServletRequest request) {
        String value = input == null ? "" : input.c;
        if (value == null || value.isBlank()) {
            return AbpResponse.ok(currentTenantId(request));
        }
        String marker = "tenantId=";
        int index = value.indexOf(marker);
        if (index < 0) {
            return AbpResponse.ok(null);
        }
        try {
            return AbpResponse.ok(Integer.parseInt(value.substring(index + marker.length()).replaceAll("[^0-9].*$", "")));
        } catch (NumberFormatException ex) {
            return AbpResponse.ok(null);
        }
    }

    private Integer currentTenantId(HttpServletRequest request) {
        AuthContext context = current(request);
        return context == null ? null : context.tenantId();
    }

    @PostMapping("/Register")
    public AbpResponse<RegisterOutput> register(@RequestBody RegisterInput input, HttpServletRequest request) {
        String validationError = validateRegisterInput(input);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        SystemSettingsItem.TenantSettings settings = store.tenantSettings(currentTenantId(request));
        // 原系统先执行验证码校验，再进入用户注册管理器。
        if (settings.userManagement.useCaptchaOnRegistration
                && (input == null || input.captchaResponse == null || input.captchaResponse.isBlank())) {
            return AbpResponse.failed("CaptchaCanNotBeEmpty");
        }
        if (!settings.userManagement.allowSelfRegistration) {
            return AbpResponse.failed("SelfUserRegistrationIsDisabledMessage_Detail");
        }
        UserItem user = new UserItem();
        user.name = input == null ? null : input.name;
        user.surname = input == null ? null : input.surname;
        user.userName = input == null ? null : input.userName;
        user.emailAddress = input == null ? null : input.emailAddress;
        UserItem created = store.registerUser(user, input == null ? null : input.password,
                settings.userManagement.isNewRegisteredUserActiveByDefault, false);
        boolean canLogin = created.isActive
                && (created.isEmailConfirmed || !settings.userManagement.isEmailConfirmationRequiredForLogin);
        return AbpResponse.ok(new RegisterOutput(canLogin));
    }

    private String validateRegisterInput(RegisterInput input) {
        if (input == null || safe(input.name).isBlank() || safe(input.surname).isBlank()
                || safe(input.userName).isBlank() || safe(input.emailAddress).isBlank()
                || safe(input.password).isBlank()) {
            return "Validation failed";
        }
        if (safe(input.name).length() > 64 || safe(input.surname).length() > 64
                || safe(input.userName).length() > 256 || safe(input.emailAddress).length() > 256
                || safe(input.password).length() > 32) {
            return "Validation failed";
        }
        if (!input.emailAddress.contains("@")) {
            return "Validation failed";
        }
        if (input.userName.contains("@") && !input.userName.equalsIgnoreCase(input.emailAddress)) {
            return "Username cannot be an email address unless it's same with your email address !";
        }
        return null;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    @PostMapping("/SendPasswordResetCode")
    public AbpResponse<Void> sendPasswordResetCode(@RequestBody SendPasswordResetCodeInput input) {
        if (input == null || safe(input.emailAddress).isBlank() || safe(input.emailAddress).length() > 256) {
            return AbpResponse.failed("Validation failed");
        }
        return store.issuePasswordResetCode(input == null ? null : input.emailAddress)
                .map(user -> AbpResponse.<Void>ok(null))
                .orElseGet(() -> AbpResponse.failed("InvalidEmailAddress"));
    }

    @PostMapping("/ResetPassword")
    public AbpResponse<ResetPasswordOutput> resetPassword(@RequestBody ResetPasswordInput input) {
        ResetPasswordInput normalized = normalizeResetPasswordInput(input);
        return store.resetPassword(normalized == null ? null : normalized.userId, normalized == null ? null : normalized.resetCode,
                        normalized == null ? null : normalized.password)
                .map(user -> AbpResponse.ok(new ResetPasswordOutput(user.isActive, user.userName)))
                .orElseGet(() -> AbpResponse.failed("InvalidPasswordResetCode"));
    }

    @PostMapping("/SendEmailActivationLink")
    public AbpResponse<Void> sendEmailActivationLink(@RequestBody SendEmailActivationLinkInput input) {
        if (input == null || safe(input.emailAddress).isBlank()) {
            // 原 SendEmailActivationLinkInput 只要求 EmailAddress 必填。
            return AbpResponse.failed("Validation failed");
        }
        return store.issueEmailActivationCode(input == null ? null : input.emailAddress)
                .map(user -> AbpResponse.<Void>ok(null))
                .orElseGet(() -> AbpResponse.failed("InvalidEmailAddress"));
    }

    @PostMapping("/ActivateEmail")
    public AbpResponse<Void> activateEmail(@RequestBody ActivateEmailInput input) {
        ActivateEmailInput normalized = normalizeActivateEmailInput(input);
        boolean ok = store.activateEmail(normalized == null ? null : normalized.userId,
                normalized == null ? null : normalized.confirmationCode);
        return ok ? AbpResponse.ok(null) : AbpResponse.failed("InvalidEmailConfirmationCode");
    }

    private ResetPasswordInput normalizeResetPasswordInput(ResetPasswordInput input) {
        if (input == null || safe(input.c).isBlank()) {
            return input;
        }
        Map<String, String> parameters = parseQueryString(stringCipher.decrypt(input.c));
        if (parameters.containsKey("userId")) {
            input.userId = parseLong(parameters.get("userId"));
        }
        if (parameters.containsKey("resetCode")) {
            input.resetCode = parameters.get("resetCode");
        }
        return input;
    }

    private ActivateEmailInput normalizeActivateEmailInput(ActivateEmailInput input) {
        if (input == null || safe(input.c).isBlank()) {
            return input;
        }
        Map<String, String> parameters = parseQueryString(stringCipher.decrypt(input.c));
        if (parameters.containsKey("userId")) {
            input.userId = parseLong(parameters.get("userId"));
        }
        if (parameters.containsKey("confirmationCode")) {
            input.confirmationCode = parameters.get("confirmationCode");
        }
        return input;
    }

    private Map<String, String> parseQueryString(String query) {
        Map<String, String> values = new LinkedHashMap<>();
        if (query == null || query.isBlank()) {
            return values;
        }
        for (String part : query.split("&")) {
            int separator = part.indexOf('=');
            String key = separator < 0 ? part : part.substring(0, separator);
            String value = separator < 0 ? "" : part.substring(separator + 1);
            values.put(urlDecode(key), urlDecode(value));
        }
        return values;
    }

    private Long parseLong(String value) {
        try {
            return value == null || value.isBlank() ? null : Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String urlDecode(String value) {
        return URLDecoder.decode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    @PostMapping("/Impersonate")
    @RequirePermission("Pages.Administration.Users.Impersonation")
    public AbpResponse<ImpersonateOutput> impersonate(@RequestBody ImpersonateInput input, HttpServletRequest request) {
        if (!isPositive(input == null ? null : input.userId)) {
            // 原 ImpersonateInput.UserId 要求大于 0。
            return AbpResponse.failed("Validation failed");
        }
        AuthContext context = current(request);
        String token = auth.createImpersonationToken(input.userId, input.tenantId,
                context == null ? null : context.user().id, context == null ? null : context.tenantId());
        return AbpResponse.ok(new ImpersonateOutput(token, tenancyName(input.tenantId)));
    }

    @PostMapping("/DelegatedImpersonate")
    public AbpResponse<ImpersonateOutput> delegatedImpersonate(@RequestBody DelegatedImpersonateInput input, HttpServletRequest request) {
        AuthContext context = current(request);
        UserDelegation delegation = store.userDelegations().stream()
                .filter(item -> item.id != null && item.id.equals(input == null ? null : input.userDelegationId))
                .findFirst()
                .orElse(null);
        if (context == null || delegation == null || !context.user().id.equals(delegation.targetUserId)) {
            return AbpResponse.denied("User delegation error.");
        }
        String token = auth.createImpersonationToken(delegation.sourceUserId, delegation.tenantId,
                context.user().id, context.tenantId());
        return AbpResponse.ok(new ImpersonateOutput(token, tenancyName(delegation.tenantId)));
    }

    @PostMapping("/BackToImpersonator")
    public AbpResponse<ImpersonateOutput> backToImpersonator(HttpServletRequest request) {
        java.util.Optional<String> delegationError = auth.delegationValidationError(request.getHeader("Authorization"));
        if (delegationError.isPresent()) {
            return AbpResponse.denied(delegationError.get());
        }
        AuthContext context = current(request);
        Long impersonatorUserId = context == null || context.impersonatorUserId() == null ? 1L : context.impersonatorUserId();
        Integer impersonatorTenantId = context == null ? null : context.impersonatorTenantId();
        String token = auth.createImpersonationToken(impersonatorUserId, impersonatorTenantId);
        return AbpResponse.ok(new ImpersonateOutput(token, tenancyName(impersonatorTenantId)));
    }

    @PostMapping("/SwitchToLinkedAccount")
    public AbpResponse<SwitchToLinkedAccountOutput> switchToLinkedAccount(@RequestBody SwitchToLinkedAccountInput input,
                                                                          HttpServletRequest request) {
        if (!isPositive(input == null ? null : input.targetUserId)) {
            // 原 SwitchToLinkedAccountInput.TargetUserId 要求大于 0。
            return AbpResponse.failed("Validation failed");
        }
        AuthContext context = current(request);
        if (context == null || !context.user().linkedUserIds.contains(input.targetUserId)) {
            return AbpResponse.denied("This account is not linked to your account");
        }
        String token = auth.createSwitchAccountToken(input.targetUserId, input.targetTenantId,
                context.impersonatorUserId(), context.impersonatorTenantId());
        return AbpResponse.ok(new SwitchToLinkedAccountOutput(token, tenancyName(input.targetTenantId)));
    }

    @PostMapping("/LinkUserForLocalReplica")
    public AbpResponse<Void> linkUserForLocalReplica(@RequestBody SwitchToLinkedAccountInput input, HttpServletRequest request) {
        AuthContext context = current(request);
        if (context == null || input == null) {
            return AbpResponse.denied("未登录或登录已过期");
        }
        store.linkUsers(context.user().id, input.targetUserId);
        return AbpResponse.ok(null);
    }

    private AuthContext current(HttpServletRequest request) {
        Object value = request.getAttribute(AuthorizationInterceptor.AUTH_CONTEXT);
        if (value instanceof AuthContext context) {
            return context;
        }
        return auth.currentUser(request.getHeader("Authorization")).orElse(null);
    }

    private boolean isPositive(Long value) {
        return value != null && value > 0;
    }

    private String tenancyName(Integer tenantId) {
        return store.tenant(tenantId).map(tenant -> tenant.tenancyName).orElse(null);
    }

    private String serverRootAddress(String tenancyName) {
        String format = serverRootAddressFormat == null || serverRootAddressFormat.isBlank()
                ? "http://localhost:9901/"
                : serverRootAddressFormat;
        if (!format.contains("{TENANCY_NAME}")) {
            return format;
        }
        if (format.contains("{TENANCY_NAME}.")) {
            format = format.replace("{TENANCY_NAME}.", "{TENANCY_NAME}");
        }
        if (tenancyName == null || tenancyName.isBlank()) {
            return format.replace("{TENANCY_NAME}", "");
        }
        return format.replace("{TENANCY_NAME}", tenancyName + ".");
    }

    public static class IsTenantAvailableInput {
        public String tenancyName;
    }

    public record IsTenantAvailableOutput(int state, Integer tenantId, String serverRootAddress) {
    }

    public static class ResolveTenantIdInput {
        public String c;
    }

    public static class RegisterInput {
        public String name;
        public String surname;
        public String userName;
        public String emailAddress;
        public String password;
        public String captchaResponse;
    }

    public record RegisterOutput(boolean canLogin) {
    }

    public static class SendPasswordResetCodeInput {
        public String emailAddress;
    }

    public static class ResetPasswordInput {
        public Long userId;
        public String resetCode;
        public String password;
        public String returnUrl;
        public String singleSignIn;
        public String c;
    }

    public record ResetPasswordOutput(boolean canLogin, String userName) {
    }

    public static class SendEmailActivationLinkInput {
        public String emailAddress;
    }

    public static class ActivateEmailInput {
        public Long userId;
        public String confirmationCode;
        public String c;
    }

    public static class ImpersonateInput {
        public Integer tenantId;
        public Long userId;
    }

    public record ImpersonateOutput(String impersonationToken, String tenancyName) {
    }

    public static class DelegatedImpersonateInput {
        public Long userDelegationId;
    }

    public static class SwitchToLinkedAccountInput {
        public Integer targetTenantId;
        public Long targetUserId;
    }

    public record SwitchToLinkedAccountOutput(String switchAccountToken, String tenancyName) {
    }
}
