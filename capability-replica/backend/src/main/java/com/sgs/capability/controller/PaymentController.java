package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.dto.IdRequest;
import com.sgs.capability.dto.PageResult;
import com.sgs.capability.model.EditionItem;
import com.sgs.capability.model.PaymentGatewayItem;
import com.sgs.capability.model.SubscriptionPaymentItem;
import com.sgs.capability.model.TenantItem;
import com.sgs.capability.security.AuthContext;
import com.sgs.capability.security.AuthService;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.CapabilityStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/** Mirrors PaymentAppService with local payment state. */
@RestController
@RequestMapping("/api/services/app/Payment")
@RequirePermission("Pages.Administration.SubscriptionManagement")
public class PaymentController {
    private final CapabilityStore store;
    private final AuthService auth;
    private final boolean stripeActive;
    private final boolean paypalActive;

    public PaymentController(CapabilityStore store, AuthService auth,
                             @Value("${payment.stripe.is-active:false}") boolean stripeActive,
                             @Value("${payment.paypal.is-active:false}") boolean paypalActive) {
        this.store = store;
        this.auth = auth;
        this.stripeActive = stripeActive;
        this.paypalActive = paypalActive;
    }

    @PostMapping("/GetPaymentInfo")
    public AbpResponse<PaymentInfoOutput> getPaymentInfo(@RequestBody(required = false) PaymentInfoInput input,
                                                         HttpServletRequest request) {
        PaymentInfoInput safeInput = input == null ? new PaymentInfoInput() : input;
        return paymentInfo(currentTenantId(request), safeInput.upgradeEditionId, safeInput.paymentPeriodType);
    }

    @GetMapping("/GetPaymentInfo")
    public AbpResponse<PaymentInfoOutput> getPaymentInfoByQuery(
            @RequestParam(name = "UpgradeEditionId", required = false) Integer upgradeEditionId,
            HttpServletRequest request) {
        return paymentInfo(currentTenantId(request), upgradeEditionId, null);
    }

    @PostMapping("/CreatePayment")
    public AbpResponse<Long> createPayment(@RequestBody CreatePaymentInput input, HttpServletRequest request) {
        CreatePaymentInput safeInput = input == null ? new CreatePaymentInput() : input;
        try {
            SubscriptionPaymentItem payment = store.createPayment(currentTenantId(request), safeInput.editionId,
                    safeInput.editionPaymentType,
                    safeInput.paymentPeriodType, safeInput.subscriptionPaymentGatewayType,
                    safeInput.recurringPaymentEnabled, safeInput.successUrl, safeInput.errorUrl);
            return AbpResponse.ok(payment.id);
        } catch (IllegalArgumentException ex) {
            return AbpResponse.failed(ex.getMessage());
        }
    }

    @PostMapping("/CancelPayment")
    public AbpResponse<Void> cancelPayment(@RequestBody CancelPaymentInput input) {
        store.cancelPayment(input == null ? null : input.paymentId, input == null ? 0 : input.gateway);
        return AbpResponse.ok(null);
    }

    @PostMapping("/GetPaymentHistory")
    public AbpResponse<PageResult<SubscriptionPaymentItem>> getPaymentHistory(@RequestBody(required = false) PageInput input,
                                                                              HttpServletRequest request) {
        PageInput safeInput = input == null ? new PageInput() : input;
        return paymentHistory(currentTenantId(request), safeInput.skipCount, safeInput.maxResultCount, safeInput.sorting);
    }

    @GetMapping("/GetPaymentHistory")
    public AbpResponse<PageResult<SubscriptionPaymentItem>> getPaymentHistoryByQuery(
            @RequestParam(name = "Sorting", required = false) String sorting,
            @RequestParam(name = "MaxResultCount", required = false) Integer maxResultCount,
            @RequestParam(name = "SkipCount", required = false) Integer skipCount,
            HttpServletRequest request) {
        return paymentHistory(currentTenantId(request), skipCount == null ? 0 : skipCount,
                maxResultCount == null ? 10 : maxResultCount, sorting);
    }

    @PostMapping("/GetActiveGateways")
    public AbpResponse<List<PaymentGatewayItem>> getActiveGateways(@RequestBody(required = false) GetActiveGatewaysInput input) {
        return AbpResponse.ok(store.activePaymentGateways(input == null ? null : input.recurringPaymentsEnabled,
                paypalActive, stripeActive));
    }

    @GetMapping("/GetActiveGateways")
    public AbpResponse<List<PaymentGatewayItem>> getActiveGatewaysByQuery(
            @RequestParam(name = "RecurringPaymentsEnabled", required = false) Boolean recurringPaymentsEnabled) {
        return AbpResponse.ok(store.activePaymentGateways(recurringPaymentsEnabled, paypalActive, stripeActive));
    }

    @PostMapping("/GetPaymentAsync")
    public AbpResponse<SubscriptionPaymentItem> getPaymentAsync(@RequestBody IdRequest input) {
        return payment(input == null ? null : input.id);
    }

    @GetMapping("/GetPayment")
    public AbpResponse<SubscriptionPaymentItem> getPaymentByQuery(
            @RequestParam(name = "paymentId", required = false) String paymentId) {
        return payment(paymentId);
    }

    private AbpResponse<SubscriptionPaymentItem> payment(String paymentId) {
        return AbpResponse.ok(store.payment(parseLong(paymentId)).orElse(null));
    }

    @PostMapping("/GetLastCompletedPayment")
    public AbpResponse<SubscriptionPaymentItem> getLastCompletedPayment(HttpServletRequest request) {
        return AbpResponse.ok(store.lastCompletedPayment(currentTenantId(request)).orElse(null));
    }

    @GetMapping("/GetLastCompletedPayment")
    public AbpResponse<SubscriptionPaymentItem> getLastCompletedPaymentByQuery(HttpServletRequest request) {
        return getLastCompletedPayment(request);
    }

    @PostMapping("/BuyNowSucceed")
    public AbpResponse<Void> buyNowSucceed(@RequestParam(name = "paymentId", required = false) String paymentId,
                                           @RequestBody(required = false) IdRequest input) {
        return markPayment(paymentId, input, 5);
    }

    @PostMapping("/NewRegistrationSucceed")
    public AbpResponse<Void> newRegistrationSucceed(@RequestParam(name = "paymentId", required = false) String paymentId,
                                                    @RequestBody(required = false) IdRequest input) {
        return markPayment(paymentId, input, 5);
    }

    @PostMapping("/UpgradeSucceed")
    public AbpResponse<Void> upgradeSucceed(@RequestParam(name = "paymentId", required = false) String paymentId,
                                            @RequestBody(required = false) IdRequest input) {
        return markPayment(paymentId, input, 5);
    }

    @PostMapping("/ExtendSucceed")
    public AbpResponse<Void> extendSucceed(@RequestParam(name = "paymentId", required = false) String paymentId,
                                           @RequestBody(required = false) IdRequest input) {
        return markPayment(paymentId, input, 5);
    }

    @PostMapping("/PaymentFailed")
    public AbpResponse<Void> paymentFailed(@RequestParam(name = "paymentId", required = false) String paymentId,
                                           @RequestBody(required = false) IdRequest input) {
        return markPayment(paymentId, input, 3);
    }

    @PostMapping("/SwitchBetweenFreeEditions")
    public AbpResponse<Void> switchBetweenFreeEditions(
            @RequestParam(name = "upgradeEditionId", required = false) Integer upgradeEditionId,
            @RequestBody(required = false) SwitchEditionInput input,
            HttpServletRequest request) {
        try {
            store.switchBetweenFreeEditions(currentTenantId(request),
                    upgradeEditionId == null && input != null ? input.upgradeEditionId : upgradeEditionId);
            return AbpResponse.ok(null);
        } catch (IllegalArgumentException ex) {
            return AbpResponse.failed(ex.getMessage());
        }
    }

    @PostMapping("/UpgradeSubscriptionCostsLessThenMinAmount")
    public AbpResponse<Void> upgradeSubscriptionCostsLessThenMinAmount(
            @RequestParam(name = "editionId", required = false) Integer editionId,
            @RequestBody(required = false) SwitchEditionInput input,
            HttpServletRequest request) {
        store.upgradeSubscriptionCostsLessThanMinAmount(currentTenantId(request),
                editionId == null && input != null ? input.editionId() : editionId);
        return AbpResponse.ok(null);
    }

    @PostMapping("/HasAnyPayment")
    public AbpResponse<Boolean> hasAnyPayment(HttpServletRequest request) {
        return AbpResponse.ok(store.hasAnyPayment(currentTenantId(request)));
    }

    private Long parseLong(String value) {
        try {
            return value == null || value.isBlank() ? null : Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private AbpResponse<PaymentInfoOutput> paymentInfo(Integer tenantId, Integer upgradeEditionId, Integer paymentPeriodType) {
        TenantItem tenant = store.tenant(tenantId).orElse(null);
        if (tenant == null || tenant.editionId == null) {
            return AbpResponse.failed("Tenant edition is not assigned");
        }
        Integer editionId = upgradeEditionId == null ? tenant.editionId : upgradeEditionId;
        EditionItem edition = store.edition(editionId).orElse(null);
        BigDecimal amount = store.editionPaymentAmount(editionId, paymentPeriodType);
        return AbpResponse.ok(new PaymentInfoOutput(edition, amount));
    }

    private AbpResponse<PageResult<SubscriptionPaymentItem>> paymentHistory(Integer tenantId, int skipCount, int maxResultCount,
                                                                            String sorting) {
        String validationError = validatePagedInput(skipCount, maxResultCount);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        return AbpResponse.ok(store.paymentHistory(tenantId, skipCount, maxResultCount, sorting));
    }

    private String validatePagedInput(int skipCount, int maxResultCount) {
        if (skipCount < 0 || maxResultCount < 1 || maxResultCount > 1000) {
            // 原 PagedInputDto 要求 MaxResultCount 为 1-1000，SkipCount 不能为负。
            return "Validation failed";
        }
        return null;
    }

    private AbpResponse<Void> markPayment(String paymentId, IdRequest input, int status) {
        // Original generated proxy posts the payment id in the query string.
        Long id = parseLong(paymentId == null && input != null ? input.id : paymentId);
        try {
            if (status == 5) {
                store.completePaidPayment(id);
            } else {
                store.markPaymentStatus(id, status);
            }
            return AbpResponse.ok(null);
        } catch (IllegalArgumentException ex) {
            return AbpResponse.failed(ex.getMessage());
        }
    }

    private Integer currentTenantId(HttpServletRequest request) {
        return auth.currentUser(request.getHeader("Authorization"))
                .map(AuthContext::tenantId)
                .orElse(1);
    }

    public static class PaymentInfoInput {
        public Integer upgradeEditionId;
        public int editionPaymentType;
        public Integer paymentPeriodType;
        public boolean recurringPaymentEnabled;
    }

    public static class CreatePaymentInput {
        public int editionId;
        public int editionPaymentType;
        public Integer paymentPeriodType;
        public int subscriptionPaymentGatewayType;
        public boolean recurringPaymentEnabled;
        public String successUrl;
        public String errorUrl;
    }

    public static class CancelPaymentInput {
        public String paymentId;
        public int gateway;
    }

    public static class PageInput {
        public int skipCount;
        public int maxResultCount = 10;
        public String sorting;
    }

    public static class GetActiveGatewaysInput {
        public Boolean recurringPaymentsEnabled;
    }

    public static class SwitchEditionInput {
        public Integer upgradeEditionId;
        public Integer editionId;

        public Integer editionId() {
            return editionId == null ? upgradeEditionId : editionId;
        }
    }

    public record PaymentInfoOutput(EditionItem edition, BigDecimal additionalPrice) {
    }
}
