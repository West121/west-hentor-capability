package com.sgs.capability.model;

/** Recent login attempt row copied from UserLoginAttemptDto. */
public class UserLoginAttemptItem {
    public Long id;
    public Long userId;
    public String tenancyName;
    public String userNameOrEmail;
    public String clientIpAddress;
    public String clientName;
    public String browserInfo;
    public String result;
    public String creationTime;
}
