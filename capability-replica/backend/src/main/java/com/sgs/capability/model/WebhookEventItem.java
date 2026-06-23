package com.sgs.capability.model;

import java.util.UUID;

/** Webhook event payload stored before send attempts. */
public class WebhookEventItem {
    public UUID id;
    public String webhookName;
    public String data;
    public String creationTime;
}
