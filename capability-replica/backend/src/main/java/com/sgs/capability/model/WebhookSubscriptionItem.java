package com.sgs.capability.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Webhook subscription row copied from ABP webhook subscriptions. */
public class WebhookSubscriptionItem {
    public UUID id;
    public String webhookUri;
    public boolean isActive;
    public List<String> webhooks = new ArrayList<>();
    public Map<String, String> headers = new LinkedHashMap<>();
    public String secret;
    public String creationTime;
}
