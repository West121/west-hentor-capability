package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.model.SystemSettingsItem;
import com.sgs.capability.model.UserItem;
import com.sgs.capability.security.AllowAnonymous;
import com.sgs.capability.security.AuthContext;
import com.sgs.capability.security.AuthService;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.CapabilityStore;
import com.sgs.capability.service.GdprCollectedDataService;
import com.sgs.capability.service.TempFileService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/** Mirrors ProfileAppService routes for the local account center. */
@RestController
@RequestMapping("/api/services/app/Profile")
@RequirePermission
public class ProfileController {
    private final AuthService auth;
    private final CapabilityStore store;
    private final GdprCollectedDataService collectedData;
    private final TempFileService tempFiles;

    public ProfileController(AuthService auth, CapabilityStore store, GdprCollectedDataService collectedData,
                             TempFileService tempFiles) {
        this.auth = auth;
        this.store = store;
        this.collectedData = collectedData;
        this.tempFiles = tempFiles;
    }

    @GetMapping("/GetCurrentUserProfileForEdit")
    public AbpResponse<CurrentUserProfileEditDto> currentProfile(HttpServletRequest request) {
        AuthContext context = auth.currentUser(request.getHeader("Authorization")).orElse(null);
        if (context == null) {
            return AbpResponse.denied("未登录或登录已过期");
        }
        return AbpResponse.ok(CurrentUserProfileEditDto.from(context.user()));
    }

    @PostMapping("/UpdateCurrentUserProfile")
    public AbpResponse<Void> updateProfile(@RequestBody CurrentUserProfileEditDto input,
                                           HttpServletRequest request) {
        AuthContext context = auth.currentUser(request.getHeader("Authorization")).orElse(null);
        if (context == null) {
            return AbpResponse.denied("未登录或登录已过期");
        }
        if (input == null
                || !hasText(input.name)
                || !hasText(input.surname)
                || !hasText(input.userName)
                || !hasText(input.emailAddress)
                || isTooLong(input.name, 64)
                || isTooLong(input.surname, 64)
                || isTooLong(input.userName, 256)
                || isTooLong(input.emailAddress, 256)
                || isTooLong(input.phoneNumber, 24)) {
            return AbpResponse.failed("Validation failed");
        }
        UserItem update = new UserItem();
        update.name = input.name;
        update.surname = input.surname;
        update.emailAddress = input.emailAddress;
        update.phoneNumber = input.phoneNumber;
        update.engName = input.engName;
        store.updateCurrentUserProfile(context.user().id, update);
        return AbpResponse.ok(null);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isTooLong(String value, int maxLength) {
        return value != null && value.length() > maxLength;
    }

    @PutMapping("/UpdateCurrentUserProfile")
    public AbpResponse<Void> putUpdateProfile(@RequestBody CurrentUserProfileEditDto input,
                                              HttpServletRequest request) {
        return updateProfile(input, request);
    }

    @PostMapping("/ChangePassword")
    public AbpResponse<Void> changePassword(@RequestBody ChangePasswordInput input, HttpServletRequest request) {
        AuthContext context = auth.currentUser(request.getHeader("Authorization")).orElse(null);
        if (context == null) {
            return AbpResponse.denied("未登录或登录已过期");
        }
        ChangePasswordInput safeInput = input == null ? new ChangePasswordInput() : input;
        if (!hasText(safeInput.currentPassword) || !hasText(safeInput.newPassword)) {
            // 原 ChangePasswordInput 要求 CurrentPassword 和 NewPassword 必填。
            return AbpResponse.failed("Validation failed");
        }
        // Original ProfileAppService checks the current password before changing it.
        if (!store.passwordMatches(context.user().id, safeInput.currentPassword)) {
            return AbpResponse.failed("Incorrect password.");
        }
        if (!matchesPasswordComplexity(safeInput.newPassword, passwordComplexityForTenant(context.tenantId()))) {
            return AbpResponse.failed("当前密码错误，或新密码少于6位");
        }
        if (!store.changePassword(context.user().id, safeInput.currentPassword, safeInput.newPassword)) {
            return AbpResponse.failed("当前密码错误，或新密码少于6位");
        }
        return AbpResponse.ok(null);
    }

    @GetMapping("/GetPasswordComplexitySetting")
    @AllowAnonymous
    public AbpResponse<PasswordComplexitySettingOutput> passwordComplexity(HttpServletRequest request) {
        Integer tenantId = auth.currentUser(request.getHeader("Authorization"))
                .map(AuthContext::tenantId)
                .orElse(1);
        SystemSettingsItem.PasswordComplexitySetting setting = passwordComplexityForTenant(tenantId);
        // Original ProfileAppService reads the current password complexity settings.
        PasswordComplexitySetting output = new PasswordComplexitySetting(
                setting.requireDigit,
                setting.requireLowercase,
                setting.requireNonAlphanumeric,
                setting.requireUppercase,
                setting.requiredLength
        );
        return AbpResponse.ok(new PasswordComplexitySettingOutput(output));
    }

    private SystemSettingsItem.PasswordComplexitySetting passwordComplexityForTenant(Integer tenantId) {
        SystemSettingsItem.SecuritySettings security = store.tenantSettings(tenantId).security;
        return security.useDefaultPasswordComplexitySettings
                ? security.defaultPasswordComplexity
                : security.passwordComplexity;
    }

    private boolean matchesPasswordComplexity(String password, SystemSettingsItem.PasswordComplexitySetting setting) {
        String value = password == null ? "" : password;
        if (value.length() < setting.requiredLength) {
            return false;
        }
        if (setting.requireDigit && value.chars().noneMatch(Character::isDigit)) {
            return false;
        }
        if (setting.requireLowercase && value.chars().noneMatch(Character::isLowerCase)) {
            return false;
        }
        if (setting.requireUppercase && value.chars().noneMatch(Character::isUpperCase)) {
            return false;
        }
        return !setting.requireNonAlphanumeric || value.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch));
    }

    @PostMapping("/DisableGoogleAuthenticator")
    public AbpResponse<Void> disableGoogleAuthenticator(HttpServletRequest request) {
        AuthContext context = auth.currentUser(request.getHeader("Authorization")).orElse(null);
        if (context == null) {
            return AbpResponse.denied("未登录或登录已过期");
        }
        store.disableGoogleAuthenticator(context.user().id);
        return AbpResponse.ok(null);
    }

    @PostMapping("/UpdateGoogleAuthenticatorKey")
    public AbpResponse<GoogleAuthenticatorOutput> updateGoogleAuthenticatorKey(HttpServletRequest request) {
        AuthContext context = auth.currentUser(request.getHeader("Authorization")).orElse(null);
        if (context == null) {
            return AbpResponse.denied("未登录或登录已过期");
        }
        String key = store.updateGoogleAuthenticatorKey(context.user().id).orElse("");
        context.user().googleAuthenticatorKey = key;
        return AbpResponse.ok(new GoogleAuthenticatorOutput(googleAuthenticatorImage(context.user())));
    }

    @PutMapping("/UpdateGoogleAuthenticatorKey")
    public AbpResponse<GoogleAuthenticatorOutput> putUpdateGoogleAuthenticatorKey(HttpServletRequest request) {
        return updateGoogleAuthenticatorKey(request);
    }

    @PostMapping("/SendVerificationSms")
    public AbpResponse<Void> sendVerificationSms(@RequestBody(required = false) SendVerificationSmsInput input,
                                                 HttpServletRequest request) {
        AuthContext context = auth.currentUser(request.getHeader("Authorization")).orElse(null);
        if (context == null) {
            return AbpResponse.denied("未登录或登录已过期");
        }
        store.sendVerificationSms(context.user().id, input == null ? context.user().phoneNumber : input.phoneNumber);
        return AbpResponse.ok(null);
    }

    @PostMapping("/VerifySmsCode")
    public AbpResponse<Void> verifySmsCode(@RequestBody(required = false) VerifySmsCodeInput input,
                                           HttpServletRequest request) {
        AuthContext context = auth.currentUser(request.getHeader("Authorization")).orElse(null);
        if (context == null) {
            return AbpResponse.denied("未登录或登录已过期");
        }
        VerifySmsCodeInput safeInput = input == null ? new VerifySmsCodeInput() : input;
        if (!store.verifySmsCode(context.user().id, safeInput.phoneNumber, safeInput.code)) {
            return AbpResponse.failed("Wrong verification code!");
        }
        return AbpResponse.ok(null);
    }

    @PostMapping("/PrepareCollectedData")
    public AbpResponse<Void> prepareCollectedData(HttpServletRequest request) {
        AuthContext context = auth.currentUser(request.getHeader("Authorization")).orElse(null);
        if (context == null) {
            return AbpResponse.denied("未登录或登录已过期");
        }
        collectedData.prepare(context.user().id);
        return AbpResponse.ok(null);
    }

    @PostMapping("/UpdateProfilePicture")
    public AbpResponse<Void> updateProfilePicture(@RequestBody(required = false) UpdateProfilePictureInput input,
                                                  HttpServletRequest request) {
        AuthContext context = auth.currentUser(request.getHeader("Authorization")).orElse(null);
        if (context == null) {
            return AbpResponse.denied("未登录或登录已过期");
        }
        if (input == null || !hasText(input.fileToken) || input.fileToken.length() > 400) {
            // 原 UpdateProfilePictureInput 要求 FileToken 必填且最多 400。
            return AbpResponse.failed("Validation failed");
        }
        try {
            store.updateProfilePicture(context.user().id, profilePicturePayload(input));
            return AbpResponse.ok(null);
        } catch (IllegalArgumentException ex) {
            return AbpResponse.failed(ex.getMessage());
        }
    }

    @PutMapping("/UpdateProfilePicture")
    public AbpResponse<Void> putUpdateProfilePicture(@RequestBody(required = false) UpdateProfilePictureInput input,
                                                     HttpServletRequest request) {
        return updateProfilePicture(input, request);
    }

    @GetMapping("/GetProfilePicture")
    public AbpResponse<GetProfilePictureOutput> getProfilePicture(HttpServletRequest request) {
        AuthContext context = auth.currentUser(request.getHeader("Authorization")).orElse(null);
        if (context == null) {
            return AbpResponse.denied("未登录或登录已过期");
        }
        return AbpResponse.ok(new GetProfilePictureOutput(store.profilePicture(context.user().id)));
    }

    @GetMapping("/GetProfilePictureById")
    @AllowAnonymous
    public AbpResponse<GetProfilePictureOutput> getProfilePictureById(@RequestParam(required = false) UUID profilePictureId) {
        return AbpResponse.ok(new GetProfilePictureOutput(store.profilePictureById(profilePictureId)));
    }

    @PostMapping("/GetProfilePictureById")
    @AllowAnonymous
    public AbpResponse<GetProfilePictureOutput> postProfilePictureById(@RequestBody(required = false) ProfilePictureIdInput input) {
        return getProfilePictureById(input == null ? null : input.profilePictureId);
    }

    @PostMapping("/GetFriendProfilePictureById")
    public AbpResponse<GetProfilePictureOutput> getFriendProfilePictureById(
            @RequestBody(required = false) GetFriendProfilePictureByIdInput input) {
        GetFriendProfilePictureByIdInput safeInput = input == null ? new GetFriendProfilePictureByIdInput() : input;
        return friendProfilePicture(safeInput.profilePictureId, safeInput.userId);
    }

    // Match the generated Angular client: GET with ProfilePictureId/UserId/TenantId query keys.
    @GetMapping("/GetFriendProfilePictureById")
    public AbpResponse<GetProfilePictureOutput> getFriendProfilePictureByQuery(
            @RequestParam(name = "ProfilePictureId", required = false) UUID profilePictureId,
            @RequestParam(name = "UserId", required = false) Long userId,
            @RequestParam(name = "TenantId", required = false) Integer tenantId) {
        return friendProfilePicture(profilePictureId, userId);
    }

    @PostMapping("/ChangeLanguage")
    public AbpResponse<Void> changeLanguage(@RequestBody(required = false) ChangeUserLanguageInput input,
                                            HttpServletRequest request) {
        AuthContext context = auth.currentUser(request.getHeader("Authorization")).orElse(null);
        if (context == null) {
            return AbpResponse.denied("未登录或登录已过期");
        }
        if (input == null || !hasText(input.languageName)) {
            // 原 ChangeUserLanguageDto 要求 LanguageName 必填。
            return AbpResponse.failed("Validation failed");
        }
        store.changeUserLanguage(context.user().id, input == null ? null : input.languageName);
        return AbpResponse.ok(null);
    }

    /** Editable profile fields from CurrentUserProfileEditDto. */
    public static class CurrentUserProfileEditDto {
        public Long id;
        public String name;
        public String surname;
        public String userName;
        public String emailAddress;
        public String phoneNumber;
        public String engName;
        public boolean isPhoneNumberConfirmed;
        public String timezone;
        public String qrCodeSetupImageUrl;
        public boolean isGoogleAuthenticatorEnabled;
        public String preferredLanguageName;

        public static CurrentUserProfileEditDto from(UserItem user) {
            CurrentUserProfileEditDto dto = new CurrentUserProfileEditDto();
            dto.id = user.id;
            dto.name = user.name;
            dto.surname = user.surname;
            dto.userName = user.userName;
            dto.emailAddress = user.emailAddress;
            dto.phoneNumber = user.phoneNumber;
            dto.engName = user.engName;
            dto.isPhoneNumberConfirmed = user.isPhoneNumberConfirmed;
            dto.timezone = "";
            dto.isGoogleAuthenticatorEnabled = user.googleAuthenticatorKey != null && !user.googleAuthenticatorKey.isBlank();
            dto.qrCodeSetupImageUrl = dto.isGoogleAuthenticatorEnabled ? googleAuthenticatorImage(user) : "";
            dto.preferredLanguageName = user.preferredLanguageName == null ? "zh-Hans" : user.preferredLanguageName;
            return dto;
        }
    }

    public static class ChangePasswordInput {
        public String currentPassword;
        public String newPassword;
    }

    public record PasswordComplexitySettingOutput(PasswordComplexitySetting setting) {
    }

    public record PasswordComplexitySetting(boolean requireDigit, boolean requireLowercase,
                                            boolean requireNonAlphanumeric, boolean requireUppercase,
                                            int requiredLength) {
    }

    public record GoogleAuthenticatorOutput(String qrCodeSetupImageUrl) {
    }

    public record GetProfilePictureOutput(String profilePicture) {
    }

    public static class UpdateProfilePictureInput {
        public String fileToken;
        public int x;
        public int y;
        public int width;
        public int height;
    }

    public static class ProfilePictureIdInput {
        public UUID profilePictureId;
    }

    public static class GetFriendProfilePictureByIdInput {
        public UUID profilePictureId;
        public Long userId;
        public Integer tenantId;
    }

    public static class ChangeUserLanguageInput {
        public String languageName;
    }

    public static class SendVerificationSmsInput {
        public String phoneNumber;
    }

    public static class VerifySmsCodeInput {
        public String code;
        public String phoneNumber;
    }

    private static String googleAuthenticatorImage(UserItem user) {
        String label = user.emailAddress == null ? user.userName : user.emailAddress;
        String key = user.googleAuthenticatorKey == null ? "" : user.googleAuthenticatorKey;
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"300\" height=\"300\">"
                + "<rect width=\"300\" height=\"300\" fill=\"white\"/>"
                + "<rect x=\"32\" y=\"32\" width=\"236\" height=\"236\" fill=\"#111827\"/>"
                + "<rect x=\"56\" y=\"56\" width=\"188\" height=\"188\" fill=\"white\"/>"
                + "<text x=\"150\" y=\"135\" text-anchor=\"middle\" font-family=\"Arial\" font-size=\"18\" fill=\"#111827\">"
                + escapeSvg(label)
                + "</text><text x=\"150\" y=\"168\" text-anchor=\"middle\" font-family=\"Arial\" font-size=\"22\" fill=\"#1f6feb\">"
                + escapeSvg(key)
                + "</text></svg>";
        return "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
    }

    private String profilePicturePayload(UpdateProfilePictureInput input) {
        if (input == null || input.fileToken == null || input.fileToken.isBlank()) {
            return "";
        }
        if (input.fileToken.startsWith("data:image")) {
            return input.fileToken;
        }
        return tempFiles.get(input.fileToken)
                .filter(file -> file.fileType() != null && file.fileType().startsWith("image/"))
                .map(file -> "data:" + file.fileType() + ";base64,"
                        + Base64.getEncoder().encodeToString(file.content()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "There is no such image file with the token: " + input.fileToken));
    }

    private AbpResponse<GetProfilePictureOutput> friendProfilePicture(UUID profilePictureId, Long userId) {
        String picture = profilePictureId == null
                ? store.user(userId).map(user -> store.profilePictureById(user.profilePictureId)).orElse("")
                : store.profilePictureById(profilePictureId);
        return AbpResponse.ok(new GetProfilePictureOutput(picture));
    }

    private static String escapeSvg(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
