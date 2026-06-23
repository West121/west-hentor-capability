package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.CapabilityStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

/** Mirrors PayPalPaymentAppService with local order ids. */
@RestController
@RequestMapping("/api/services/app/PayPalPayment")
@RequirePermission("Pages.Administration.SubscriptionManagement")
public class PayPalPaymentController {
    private final CapabilityStore store;
    private final String clientId;
    private final String demoUsername;
    private final String demoPassword;

    public PayPalPaymentController(CapabilityStore store,
                                   @Value("${payment.paypal.client-id:}") String clientId,
                                   @Value("${payment.paypal.demo-username:}") String demoUsername,
                                   @Value("${payment.paypal.demo-password:}") String demoPassword) {
        this.store = store;
        this.clientId = clientId;
        this.demoUsername = demoUsername;
        this.demoPassword = demoPassword;
    }

    @PostMapping("/ConfirmPayment")
    public AbpResponse<Void> confirmPayment(@RequestParam(required = false) Long paymentId,
                                            @RequestParam(required = false) String paypalOrderId,
                                            @RequestBody(required = false) PayPalConfirmInput input) {
        // Original generated proxy sends PayPal ids as POST query parameters.
        store.confirmPayPalPayment(paymentId == null && input != null ? input.paymentId : paymentId,
                paypalOrderId == null && input != null ? input.paypalOrderId : paypalOrderId);
        return AbpResponse.ok(null);
    }

    @GetMapping("/GetConfiguration")
    public AbpResponse<PayPalConfigurationOutput> getConfiguration() {
        return AbpResponse.ok(new PayPalConfigurationOutput(clientId, demoUsername, demoPassword));
    }

    public static class PayPalConfirmInput {
        public Long paymentId;
        public String paypalOrderId;
    }

    public record PayPalConfigurationOutput(String clientId, String demoUsername, String demoPassword) {
    }
}
