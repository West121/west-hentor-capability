package com.sgs.capability.model;

import java.util.UUID;

/** Friend row copied from FriendDto with owner fields for local persistence. */
public class FriendItem {
    public Long userId;
    public Integer tenantId;
    public Long friendUserId;
    public Integer friendTenantId;
    public String friendUserName;
    public String friendTenancyName;
    public UUID friendProfilePictureId;
    public int unreadMessageCount;
    public boolean isOnline;
    public int state = 1;
    public String creationTime;
}
