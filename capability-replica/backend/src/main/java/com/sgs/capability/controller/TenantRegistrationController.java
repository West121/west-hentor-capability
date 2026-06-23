package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.model.EditionItem;
import com.sgs.capability.model.FeatureItem;
import com.sgs.capability.model.NameValueItem;
import com.sgs.capability.model.SubscriptionPaymentItem;
import com.sgs.capability.model.SystemSettingsItem;
import com.sgs.capability.model.TenantItem;
import com.sgs.capability.security.AuthService;
import com.sgs.capability.service.CapabilityStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Mirrors TenantRegistrationAppService public registration and pricing endpoints. */
@RestController
@RequestMapping("/api/services/app/TenantRegistration")
public class TenantRegistrationController {
    private final CapabilityStore store;
    private final AuthService auth;

    public TenantRegistrationController(CapabilityStore store, AuthService auth) {
        this.store = store;
        this.auth = auth;
    }

    @PostMapping("/RegisterTenant")
    public AbpResponse<RegisterTenantOutput> registerTenant(@RequestBody(required = false) RegisterTenantInput input) {
        RegisterTenantInput safeInput = input == null ? new RegisterTenantInput() : input;
        String validationError = validateRegisterTenantInput(safeInput);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        if (store.tenantByTenancyName(safeInput.tenancyName).isPresent()) {
            return AbpResponse.failed("Tenancy name is already taken.");
        }

        EditionItem edition = store.edition(safeInput.editionId).orElse(null);
        String subscriptionStartError = validateSubscriptionStart(edition, safeInput);
        if (subscriptionStartError != null) {
            return AbpResponse.failed(subscriptionStartError);
        }
        SystemSettingsItem.HostSettings settings = store.hostSettings();
        if (!settings.tenantManagement.allowSelfRegistration) {
            return AbpResponse.failed("SelfTenantRegistrationIsDisabledMessage_Detail");
        }
        // 原系统在确认允许租户注册后校验验证码。
        if (settings.tenantManagement.useCaptchaOnRegistration
                && (safeInput.captchaResponse == null || safeInput.captchaResponse.isBlank())) {
            return AbpResponse.failed("CaptchaCanNotBeEmpty");
        }
        boolean paidStart = safeInput.subscriptionStartType == 3;
        boolean trialStart = safeInput.subscriptionStartType == 2;
        boolean isActive = paidStart ? false : settings.tenantManagement.isNewRegisteredTenantActiveByDefault;

        TenantItem tenant = new TenantItem();
        tenant.tenancyName = safeInput.tenancyName;
        tenant.name = safeInput.name;
        tenant.adminEmailAddress = safeInput.adminEmailAddress;
        tenant.adminPassword = safe(safeInput.adminPassword).isBlank() ? "123qwe" : safeInput.adminPassword;
        tenant.editionId = edition == null ? null : edition.id;
        tenant.isActive = isActive;
        tenant.isInTrialPeriod = trialStart;
        tenant.shouldChangePasswordOnNextLogin = false;
        tenant.sendActivationEmail = true;
        if (trialStart && edition != null && edition.trialDayCount != null) {
            tenant.subscriptionEndDateUtc = LocalDateTime.now().plusDays(edition.trialDayCount).toString();
        }

        TenantItem created = store.createTenant(tenant);
        return AbpResponse.ok(new RegisterTenantOutput(created.id, created.tenancyName, created.name, "admin",
                created.adminEmailAddress, created.isActive, created.isActive,
                settings.userManagement.isEmailConfirmationRequiredForLogin));
    }

    private String validateRegisterTenantInput(RegisterTenantInput input) {
        if (safe(input.tenancyName).isBlank() || safe(input.name).isBlank() || safe(input.adminEmailAddress).isBlank()) {
            return "Validation failed";
        }
        if (input.tenancyName.length() > 64
                || input.name.length() > 64
                || input.adminEmailAddress.length() > 256
                || (input.adminPassword != null && input.adminPassword.length() > 32)) {
            return "Validation failed";
        }
        if (!input.adminEmailAddress.contains("@")) {
            return "Validation failed";
        }
        return null;
    }

    @GetMapping("/GetEditionsForSelect")
    public AbpResponse<EditionsSelectOutput> getEditionsForSelect(HttpServletRequest request) {
        Integer tenantId = currentTenantId(request);
        TenantItem tenant = tenantId == null ? null : store.tenant(tenantId).orElse(null);
        EditionItem currentEdition = tenant == null ? null : store.edition(tenant.editionId).orElse(null);
        SubscriptionPaymentItem lastPayment = tenantId == null ? null : store.lastCompletedPayment(tenantId).orElse(null);

        return AbpResponse.ok(new EditionsSelectOutput(
                store.features().stream()
                        .sorted(Comparator.comparing(feature -> safe(feature.displayName)))
                        .map(this::flatFeature)
                        .toList(),
                store.editions().stream()
                        .filter(edition -> currentEdition == null || !Objects.equals(edition.id, currentEdition.id))
                        .filter(edition -> isUpgradeForLastPayment(edition, currentEdition, lastPayment))
                        .sorted(Comparator.comparing(this::monthlyAmount))
                        .map(this::editionWithFeatures)
                        .toList()
        ));
    }

    @PostMapping("/GetEditionsForSelect")
    public AbpResponse<EditionsSelectOutput> postEditionsForSelect(HttpServletRequest request) {
        return getEditionsForSelect(request);
    }

    @GetMapping("/GetEdition")
    public AbpResponse<EditionSelectDto> getEdition(@RequestParam Integer editionId) {
        return AbpResponse.ok(store.edition(editionId).map(this::editionSelect).orElse(null));
    }

    @PostMapping("/GetEdition")
    public AbpResponse<EditionSelectDto> postGetEdition(@RequestBody(required = false) GetEditionInput input) {
        return getEdition(input == null ? null : input.editionId);
    }

    private EditionWithFeaturesDto editionWithFeatures(EditionItem edition) {
        return new EditionWithFeaturesDto(editionSelect(edition), nameValues(edition.featureValues));
    }

    private EditionSelectDto editionSelect(EditionItem edition) {
        return new EditionSelectDto(edition.id, edition.name, edition.displayName, edition.expiringEditionId,
                edition.dailyPrice, edition.weeklyPrice, edition.monthlyPrice, edition.annualPrice,
                edition.trialDayCount, edition.waitingDayAfterExpire, edition.isFree, additionalData());
    }

    private FlatFeatureSelectDto flatFeature(FeatureItem feature) {
        return new FlatFeatureSelectDto(feature.parentName, feature.name, feature.displayName, feature.description,
                feature.defaultValue, feature.inputType, null);
    }

    private List<NameValueItem> nameValues(Map<String, String> values) {
        return values == null ? List.of() : values.entrySet().stream()
                .map(entry -> {
                    NameValueItem item = new NameValueItem();
                    item.name = entry.getKey();
                    item.value = entry.getValue();
                    return item;
                })
                .toList();
    }

    private Integer currentTenantId(HttpServletRequest request) {
        return auth.currentUser(request.getHeader("Authorization"))
                .map(context -> context.tenantId())
                .orElse(null);
    }

    private boolean isUpgradeForLastPayment(EditionItem edition, EditionItem currentEdition, SubscriptionPaymentItem lastPayment) {
        if (currentEdition == null || lastPayment == null) {
            return true;
        }
        return paymentAmount(edition, lastPayment.paymentPeriodType)
                .compareTo(paymentAmount(currentEdition, lastPayment.paymentPeriodType)) > 0;
    }

    private BigDecimal monthlyAmount(EditionItem edition) {
        return amount(edition.monthlyPrice);
    }

    private BigDecimal paymentAmount(EditionItem edition, Integer paymentPeriodType) {
        return switch (paymentPeriodType == null ? 30 : paymentPeriodType) {
            case 1 -> amount(edition.dailyPrice);
            case 7 -> amount(edition.weeklyPrice);
            case 365 -> amount(edition.annualPrice);
            default -> amount(edition.monthlyPrice);
        };
    }

    private BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String validateSubscriptionStart(EditionItem edition, RegisterTenantInput input) {
        if (input.editionId == null) {
            return store.editions().isEmpty()
                    ? null
                    : "Tenant registration is not allowed without edition because there are editions defined !";
        }
        return switch (input.subscriptionStartType) {
            case 1 -> edition != null && edition.isFree ? null : "This is not a free edition !";
            case 2 -> edition != null && hasTrial(edition) ? null : "Trial is not available for this edition !";
            case 3 -> edition != null && !edition.isFree
                    ? null
                    : "This is a free edition and cannot be subscribed as paid !";
            default -> null;
        };
    }

    private boolean hasTrial(EditionItem edition) {
        // 原系统免费版本不能试用；付费版本必须显式配置正数试用天数。
        return !edition.isFree && edition.trialDayCount != null && edition.trialDayCount > 0;
    }

    private Map<String, Map<String, String>> additionalData() {
        return Map.of();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public static class RegisterTenantInput {
        public String tenancyName;
        public String name;
        public String adminEmailAddress;
        public String adminPassword;
        public String captchaResponse;
        public int subscriptionStartType = 1;
        public Integer editionId;
    }

    public record RegisterTenantOutput(int tenantId, String tenancyName, String name, String userName,
                                       String emailAddress, boolean isTenantActive, boolean isActive,
                                       boolean isEmailConfirmationRequired) {
    }

    public record EditionsSelectOutput(List<FlatFeatureSelectDto> allFeatures,
                                       List<EditionWithFeaturesDto> editionsWithFeatures) {
    }

    public record EditionWithFeaturesDto(EditionSelectDto edition, List<NameValueItem> featureValues) {
    }

    public record EditionSelectDto(Integer id, String name, String displayName, Integer expiringEditionId,
                                   BigDecimal dailyPrice, BigDecimal weeklyPrice, BigDecimal monthlyPrice,
                                   BigDecimal annualPrice, Integer trialDayCount, Integer waitingDayAfterExpire,
                                   boolean isFree, Map<String, Map<String, String>> additionalData) {
    }

    public record FlatFeatureSelectDto(String parentName, String name, String displayName, String description,
                                       String defaultValue, Map<String, Object> inputType, String textHtmlColor) {
    }

    public static class GetEditionInput {
        public Integer editionId;
    }
}
