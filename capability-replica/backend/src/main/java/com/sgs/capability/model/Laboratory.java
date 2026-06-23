package com.sgs.capability.model;

import java.util.UUID;

/** Laboratory master data used in ability rows and admin screens. */
public class Laboratory {
    public UUID id;
    public String code;
    public String name;
    public String engName;
    public String describe;
    public String leader;
    public String contactInfo;
    public String address;
    public boolean hasCnas;
    public boolean hasCms;
}
