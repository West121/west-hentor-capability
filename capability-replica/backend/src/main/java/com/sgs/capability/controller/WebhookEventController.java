package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.dto.IdRequest;
import com.sgs.capability.model.WebhookEventItem;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.CapabilityStore;
import org.springframework.web.bind.annotation.*;

/** Mirrors WebhookEventAppService event detail route. */
@RestController
@RequestMapping("/api/services/app/WebhookEvent")
@RequirePermission("Pages.Administration.WebhookSubscription")
public class WebhookEventController {
    private final CapabilityStore store;

    public WebhookEventController(CapabilityStore store) {
        this.store = store;
    }

    @GetMapping("/Get")
    public AbpResponse<WebhookEventItem> get(@RequestParam(required = false) String id) {
        return AbpResponse.ok(store.webhookEvent(id).orElse(null));
    }

    @PostMapping("/Get")
    public AbpResponse<WebhookEventItem> postGet(@RequestBody(required = false) IdRequest input) {
        return AbpResponse.ok(store.webhookEvent(input == null ? null : input.id).orElse(null));
    }
}
