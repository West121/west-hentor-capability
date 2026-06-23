package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.dto.IdRequest;
import com.sgs.capability.dto.ListResult;
import com.sgs.capability.model.WebhookDefinitionItem;
import com.sgs.capability.model.WebhookSubscriptionItem;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.CapabilityStore;
import org.springframework.web.bind.annotation.*;

/** Mirrors WebhookSubscriptionAppService subscription routes. */
@RestController
@RequestMapping("/api/services/app/WebhookSubscription")
@RequirePermission("Pages.Administration.WebhookSubscription")
public class WebhookSubscriptionController {
    private final CapabilityStore store;

    public WebhookSubscriptionController(CapabilityStore store) {
        this.store = store;
    }

    @PostMapping("/PublishTestWebhook")
    public AbpResponse<String> publishTestWebhook() {
        return AbpResponse.ok(store.publishTestWebhook());
    }

    @GetMapping("/GetAllSubscriptions")
    public AbpResponse<ListResult<WebhookSubscriptionItem>> getAllSubscriptions() {
        return AbpResponse.ok(new ListResult<>(store.webhookSubscriptions()));
    }

    @PostMapping("/GetAllSubscriptions")
    public AbpResponse<ListResult<WebhookSubscriptionItem>> postGetAllSubscriptions() {
        return getAllSubscriptions();
    }

    @GetMapping("/GetSubscription")
    @RequirePermission("Pages.Administration.WebhookSubscription.Detail")
    public AbpResponse<WebhookSubscriptionItem> getSubscription(@RequestParam(required = false) String subscriptionId) {
        return AbpResponse.ok(store.webhookSubscription(subscriptionId).orElse(null));
    }

    @PostMapping("/GetSubscription")
    @RequirePermission("Pages.Administration.WebhookSubscription.Detail")
    public AbpResponse<WebhookSubscriptionItem> postGetSubscription(@RequestBody(required = false) SubscriptionInput input) {
        return AbpResponse.ok(store.webhookSubscription(input == null ? null : input.subscriptionId()).orElse(null));
    }

    @PostMapping("/AddSubscription")
    @RequirePermission("Pages.Administration.WebhookSubscription.Create")
    public AbpResponse<Void> addSubscription(@RequestBody WebhookSubscriptionItem input) {
        input.id = null;
        store.saveWebhookSubscription(input);
        return AbpResponse.ok(null);
    }

    @PostMapping("/UpdateSubscription")
    @RequirePermission("Pages.Administration.WebhookSubscription.Edit")
    public AbpResponse<Void> updateSubscription(@RequestBody WebhookSubscriptionItem input) {
        store.saveWebhookSubscription(input);
        return AbpResponse.ok(null);
    }

    @PutMapping("/UpdateSubscription")
    @RequirePermission("Pages.Administration.WebhookSubscription.Edit")
    public AbpResponse<Void> putUpdateSubscription(@RequestBody WebhookSubscriptionItem input) {
        return updateSubscription(input);
    }

    @PostMapping("/ActivateWebhookSubscription")
    @RequirePermission("Pages.Administration.WebhookSubscription.ChangeActivity")
    public AbpResponse<Void> activateWebhookSubscription(@RequestBody ActivateInput input) {
        store.activateWebhookSubscription(input == null ? null : input.subscriptionId(), input != null && input.isActive);
        return AbpResponse.ok(null);
    }

    @GetMapping("/IsSubscribed")
    public AbpResponse<Boolean> isSubscribed(@RequestParam(required = false) String webhookName) {
        return AbpResponse.ok(store.isSubscribed(webhookName));
    }

    @PostMapping("/IsSubscribed")
    public AbpResponse<Boolean> postIsSubscribed(@RequestParam(required = false) String webhookName,
                                                 @RequestBody(required = false) WebhookNameInput input) {
        // Original proxy posts this value in the query string.
        String safeWebhookName = webhookName == null || webhookName.isBlank()
                ? input == null ? null : input.webhookName
                : webhookName;
        return AbpResponse.ok(store.isSubscribed(safeWebhookName));
    }

    @GetMapping("/GetAllSubscriptionsIfFeaturesGranted")
    public AbpResponse<ListResult<WebhookSubscriptionItem>> getAllSubscriptionsIfFeaturesGranted(
            @RequestParam(required = false) String webhookName) {
        return AbpResponse.ok(new ListResult<>(store.webhookSubscriptionsForWebhook(webhookName)));
    }

    @PostMapping("/GetAllSubscriptionsIfFeaturesGranted")
    public AbpResponse<ListResult<WebhookSubscriptionItem>> postGetAllSubscriptionsIfFeaturesGranted(
            @RequestBody(required = false) WebhookNameInput input) {
        return AbpResponse.ok(new ListResult<>(store.webhookSubscriptionsForWebhook(input == null ? null : input.webhookName)));
    }

    @GetMapping("/GetAllAvailableWebhooks")
    public AbpResponse<ListResult<WebhookDefinitionItem>> getAllAvailableWebhooks() {
        return AbpResponse.ok(new ListResult<>(store.webhookDefinitions()));
    }

    @PostMapping("/GetAllAvailableWebhooks")
    public AbpResponse<ListResult<WebhookDefinitionItem>> postGetAllAvailableWebhooks() {
        return getAllAvailableWebhooks();
    }

    public static class SubscriptionInput extends IdRequest {
        public String subscriptionId;

        public String subscriptionId() {
            return subscriptionId == null || subscriptionId.isBlank() ? id : subscriptionId;
        }
    }

    public static class ActivateInput extends SubscriptionInput {
        public boolean isActive;
    }

    public static class WebhookNameInput {
        public String webhookName;
    }
}
