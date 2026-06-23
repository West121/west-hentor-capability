package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.dto.ListResult;
import com.sgs.capability.dto.PageResult;
import com.sgs.capability.model.WebhookSendAttemptItem;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.CapabilityStore;
import org.springframework.web.bind.annotation.*;

/** Mirrors WebhookSendAttemptAppService list and resend routes. */
@RestController
@RequestMapping("/api/services/app/WebhookSendAttempt")
@RequirePermission("Pages.Administration.Webhook.ListSendAttempts")
public class WebhookSendAttemptController {
    private final CapabilityStore store;

    public WebhookSendAttemptController(CapabilityStore store) {
        this.store = store;
    }

    @PostMapping("/GetAllSendAttempts")
    public AbpResponse<PageResult<WebhookSendAttemptItem>> getAllSendAttempts(@RequestBody(required = false) AttemptsInput input) {
        AttemptsInput safeInput = input == null ? new AttemptsInput() : input;
        String validationError = validatePagedInput(safeInput.skipCount, safeInput.maxResultCount);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        return AbpResponse.ok(store.webhookSendAttempts(safeInput.subscriptionId, safeInput.skipCount, safeInput.maxResultCount));
    }

    @GetMapping("/GetAllSendAttempts")
    public AbpResponse<PageResult<WebhookSendAttemptItem>> getAllSendAttemptsByQuery(
            @RequestParam(name = "SubscriptionId", required = false) String subscriptionId,
            @RequestParam(name = "MaxResultCount", required = false) Integer maxResultCount,
            @RequestParam(name = "SkipCount", required = false) Integer skipCount) {
        int safeSkipCount = skipCount == null ? 0 : skipCount;
        int safeMaxResultCount = maxResultCount == null ? 10 : maxResultCount;
        String validationError = validatePagedInput(safeSkipCount, safeMaxResultCount);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        return AbpResponse.ok(store.webhookSendAttempts(subscriptionId,
                safeSkipCount,
                safeMaxResultCount));
    }

    @PostMapping("/GetAllSendAttemptsOfWebhookEvent")
    public AbpResponse<ListResult<WebhookSendAttemptItem>> getAllSendAttemptsOfWebhookEvent(
            @RequestBody(required = false) EventInput input) {
        return AbpResponse.ok(new ListResult<>(store.webhookSendAttemptsOfEvent(input == null ? null : input.id)));
    }

    @GetMapping("/GetAllSendAttemptsOfWebhookEvent")
    public AbpResponse<ListResult<WebhookSendAttemptItem>> getAllSendAttemptsOfWebhookEventByQuery(
            @RequestParam(name = "Id", required = false) String id) {
        return AbpResponse.ok(new ListResult<>(store.webhookSendAttemptsOfEvent(id)));
    }

    @PostMapping("/Resend")
    @RequirePermission("Pages.Administration.Webhook.ResendWebhook")
    public AbpResponse<Void> resend(@RequestParam(required = false) String sendAttemptId,
                                    @RequestBody(required = false) ResendInput input) {
        // Original proxy posts sendAttemptId as a query parameter.
        String safeSendAttemptId = sendAttemptId == null || sendAttemptId.isBlank()
                ? input == null ? null : input.sendAttemptId()
                : sendAttemptId;
        store.resendWebhookAttempt(safeSendAttemptId);
        return AbpResponse.ok(null);
    }

    public static class AttemptsInput {
        public String subscriptionId;
        public int skipCount;
        public int maxResultCount = 10;
    }

    private String validatePagedInput(int skipCount, int maxResultCount) {
        if (skipCount < 0 || maxResultCount < 1 || maxResultCount > 1000) {
            // 原 PagedInputDto 要求 MaxResultCount 为 1-1000，SkipCount 不能为负。
            return "Validation failed";
        }
        return null;
    }

    public static class EventInput {
        public String id;
    }

    public static class ResendInput extends EventInput {
        public String sendAttemptId;

        public String sendAttemptId() {
            return sendAttemptId == null || sendAttemptId.isBlank() ? id : sendAttemptId;
        }
    }
}
