package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.model.SubscriptionPaymentItem;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.CapabilityStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

/** Mirrors StripePaymentAppService with local session ids. */
@RestController
@RequestMapping("/api/services/app/StripePayment")
@RequirePermission("Pages.Administration.SubscriptionManagement")
public class StripePaymentController {
    private final CapabilityStore store;
    private final String publishableKey;

    public StripePaymentController(CapabilityStore store,
                                   @Value("${payment.stripe.publishable-key:}") String publishableKey) {
        this.store = store;
        this.publishableKey = publishableKey;
    }

    @PostMapping("/ConfirmPayment")
    public AbpResponse<Void> confirmPayment(@RequestBody StripeSessionInput input) {
        store.confirmStripePayment(input == null ? null : input.stripeSessionId);
        return AbpResponse.ok(null);
    }

    @GetMapping("/GetConfiguration")
    public AbpResponse<StripeConfigurationOutput> getConfiguration() {
        return AbpResponse.ok(new StripeConfigurationOutput(publishableKey));
    }

    @PostMapping("/GetPaymentAsync")
    public AbpResponse<SubscriptionPaymentItem> getPaymentAsync(@RequestBody StripeSessionInput input) {
        return payment(input == null ? null : input.stripeSessionId);
    }

    @GetMapping("/GetPayment")
    public AbpResponse<SubscriptionPaymentItem> getPaymentByQuery(
            @RequestParam(name = "StripeSessionId", required = false) String stripeSessionId) {
        return payment(stripeSessionId);
    }

    @GetMapping("/GetPaymentResult")
    public AbpResponse<StripePaymentResultOutput> getPaymentResult(
            @RequestParam(name = "PaymentId", required = false) Long paymentId) {
        try {
            return AbpResponse.ok(new StripePaymentResultOutput(store.stripePaymentDone(paymentId)));
        } catch (IllegalArgumentException ex) {
            return AbpResponse.failed(ex.getMessage());
        }
    }

    private AbpResponse<SubscriptionPaymentItem> payment(String stripeSessionId) {
        return store.paymentByExternalId(stripeSessionId)
                .map(AbpResponse::ok)
                .orElseGet(() -> AbpResponse.failed("Cannot find any payment with sessionId " + stripeSessionId));
    }

    @PostMapping("/CreatePaymentSession")
    public AbpResponse<String> createPaymentSession(@RequestBody StripeCreateSessionInput input) {
        return AbpResponse.ok(store.createStripePaymentSession(input == null ? null : input.paymentId));
    }

    public static class StripeSessionInput {
        public String stripeSessionId;
    }

    public static class StripeCreateSessionInput {
        public Long paymentId;
        public String successUrl;
        public String cancelUrl;
    }

    public record StripeConfigurationOutput(String publishableKey) {
    }

    public record StripePaymentResultOutput(boolean paymentDone) {
    }
}
