package com.sgs.capability.dto;

import com.sgs.capability.model.UserItem;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Request body for UserAppService.CreateOrUpdateUser. */
public class UserEditInput {
    public UserItem user;
    public List<String> assignedRoleNames;
    public boolean sendActivationEmail;
    public boolean setRandomPassword;
    public List<Long> organizationUnits = new ArrayList<>();
    public List<UUID> labs = new ArrayList<>();
}
