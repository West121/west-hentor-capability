package com.sgs.capability.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Copies the public Stripe MVC webhook entry point from Web.Host. */
@RestController
public class StripeWebhookController {

    @PostMapping("/Stripe/WebHooks")
    public ResponseEntity<Void> webHooks(@RequestBody(required = false) String payload,
                                         @RequestHeader(name = "Stripe-Signature", required = false) String stripeSignature) {
        if (isBlank(payload) || isBlank(stripeSignature)) {
            // 原控制器用 Stripe SDK 校验签名，缺签名会返回 BadRequest。
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok().build();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
