package com.sgs.capability.dto;

import com.sgs.capability.model.Ability;
import com.sgs.capability.model.Laboratory;
import com.sgs.capability.model.OrganizationUnit;
import com.sgs.capability.model.SampleType;
import java.util.List;

/** Edit screen payload containing lookups and the selected ability. */
public class GetForEditOutput {
    public Ability abilityDto;
    public List<Laboratory> labList;
    public List<OrganizationUnit> orgList;
    public List<SampleType> sampleTypeList;
}
