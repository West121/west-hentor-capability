package com.sgs.capability.model;

import java.util.ArrayList;
import java.util.List;

/** Per-user notification settings copied from the AspNet Zero account platform. */
public class NotificationSettings {
    public Long userId;
    public boolean receiveNotifications = true;
    public boolean desktopNotifications = true;
    public boolean emailNotifications = true;
    public boolean smsNotifications;
    public List<NotificationSubscription> notifications = new ArrayList<>();
}
