package com.sgs.capability.model;

import java.util.UUID;

/** Webhook send attempt result copied from ABP webhook attempt rows. */
public class WebhookSendAttemptItem {
    public UUID id;
    public UUID webhookEventId;
    public UUID webhookSubscriptionId;
    public String webhookUri;
    public String webhookName;
    public String data;
    public String response;
    public Integer responseStatusCode;
    public String creationTime;
    public String lastModificationTime;
    public int retryCount;
}
