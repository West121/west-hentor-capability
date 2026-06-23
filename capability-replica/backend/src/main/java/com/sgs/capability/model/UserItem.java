package com.sgs.capability.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** User row matching UserListDto/UserEditDto fields. */
public class UserItem {
    public Long id;
    public String name;
    public String surname;
    public String userName;
    public String emailAddress;
    public String phoneNumber;
    public String password;
    public UUID profilePictureId;
    public boolean isEmailConfirmed;
    public boolean isPhoneNumberConfirmed;
    public boolean isActive;
    public boolean shouldChangePasswordOnNextLogin;
    public boolean isTwoFactorEnabled;
    public boolean isLockoutEnabled;
    public boolean isLockedOut;
    public String engName;
    public String preferredLanguageName;
    public String googleAuthenticatorKey;
    public String signInToken;
    public String lastSmsVerificationCode;
    public String collectedDataPreparedTime;
    public LocalDateTime creationTime;
    public String passwordResetCode;
    public String emailConfirmationCode;
    public String lastLoginTime;
    public List<Long> linkedUserIds = new ArrayList<>();
    public List<String> assignedRoleNames = new ArrayList<>();
    public List<Long> organizationUnits = new ArrayList<>();
    public List<UUID> labs = new ArrayList<>();
}
