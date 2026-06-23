package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.dto.IdRequest;
import com.sgs.capability.model.InvoiceItem;
import com.sgs.capability.model.SystemSettingsItem;
import com.sgs.capability.security.AuthContext;
import com.sgs.capability.security.AuthService;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.CapabilityStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

/** Mirrors InvoiceAppService invoice routes. */
@RestController
@RequestMapping("/api/services/app/Invoice")
@RequirePermission("Pages.Administration.SubscriptionManagement")
public class InvoiceController {
    private final AuthService auth;
    private final CapabilityStore store;

    public InvoiceController(AuthService auth, CapabilityStore store) {
        this.auth = auth;
        this.store = store;
    }

    @PostMapping("/GetInvoiceInfo")
    public AbpResponse<InvoiceItem> getInvoiceInfo(@RequestBody IdRequest input, HttpServletRequest request) {
        return invoiceInfo(input == null ? null : input.id, request);
    }

    @GetMapping("/GetInvoiceInfo")
    public AbpResponse<InvoiceItem> getInvoiceInfoByQuery(@RequestParam(name = "Id", required = false) String id,
                                                          HttpServletRequest request) {
        return invoiceInfo(id, request);
    }

    private AbpResponse<InvoiceItem> invoiceInfo(String id, HttpServletRequest request) {
        AuthContext context = auth.currentUser(request.getHeader("Authorization")).orElse(null);
        if (context == null) {
            return AbpResponse.denied("未登录或登录已过期");
        }
        Long paymentId = parseLong(id);
        var payment = store.payment(paymentId);
        if (payment.isPresent() && isBlank(payment.get().invoiceNo)) {
            return AbpResponse.failed("There is no invoice for this payment !");
        }
        if (payment.isPresent() && payment.get().tenantId != context.tenantId()) {
            return AbpResponse.failed("This invoice is not yours!");
        }
        return AbpResponse.ok(store.invoiceInfo(paymentId).orElse(null));
    }

    @PostMapping("/CreateInvoice")
    public AbpResponse<Void> createInvoice(@RequestBody CreateInvoiceInput input, HttpServletRequest request) {
        AuthContext context = auth.currentUser(request.getHeader("Authorization")).orElse(null);
        if (context == null) {
            return AbpResponse.denied("未登录或登录已过期");
        }
        Long paymentId = input == null ? null : input.subscriptionPaymentId;
        boolean invoiceAlreadyExists = store.payment(paymentId)
                .filter(payment -> payment.invoiceNo != null && !payment.invoiceNo.isBlank())
                .isPresent();
        if (invoiceAlreadyExists) {
            return AbpResponse.failed("Invoice is already generated for this payment.");
        }
        SystemSettingsItem.TenantSettings settings = store.tenantSettings(context.tenantId());
        if (isBlank(settings.billing.legalName) || isBlank(settings.billing.address) || isBlank(settings.billing.taxVatNo)) {
            return AbpResponse.failed("Invoice info is missing or not completed");
        }
        store.createInvoice(paymentId);
        return AbpResponse.ok(null);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private Long parseLong(String value) {
        try {
            return value == null || value.isBlank() ? null : Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static class CreateInvoiceInput {
        public Long subscriptionPaymentId;
    }
}
