package com.sgs.capability.model;

/** Chat message row copied from ChatMessageDto. */
public class ChatMessageItem {
    public Long id;
    public Long userId;
    public Integer tenantId;
    public Long targetUserId;
    public Integer targetTenantId;
    public int side = 1;
    public int readState = 1;
    public int receiverReadState = 1;
    public String message;
    public String creationTime;
    public String sharedMessageId;
}
