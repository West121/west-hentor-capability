package com.sgs.capability.dto;

import java.util.ArrayList;
import java.util.List;

/** One row from the copied UsersController ImportFromExcel file. */
public class ImportUserDto {
    public String userName;
    public String name;
    public String surname;
    public String emailAddress;
    public String phoneNumber;
    public String password;
    public List<String> assignedRoleNames = new ArrayList<>();
    public String exception;
}
