package com.sgs.capability.model;

import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.Map;

/** User notification row matching AspNet Zero notification list fields. */
public class NotificationItem {
    public UUID id;
    public Long userId;
    public String notificationName;
    public String message;
    public String severity;
    public Map<String, Object> data = new LinkedHashMap<>();
    public String creationTime;
    public int readState;
    public String readTime;
}
