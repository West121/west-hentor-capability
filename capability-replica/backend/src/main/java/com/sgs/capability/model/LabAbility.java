package com.sgs.capability.model;

import java.util.UUID;

/** Per-lab capability flags for CNAS, CMA, and available ability. */
public class LabAbility {
    public UUID labId;
    public String code;
    public boolean hasCnas;
    public boolean hasCma;
    public boolean isAbility;
}
