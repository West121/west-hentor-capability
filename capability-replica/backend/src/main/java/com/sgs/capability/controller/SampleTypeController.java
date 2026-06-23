package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.dto.IdRequest;
import com.sgs.capability.dto.ListResult;
import com.sgs.capability.model.OrganizationUnit;
import com.sgs.capability.model.SampleType;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.CapabilityStore;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Mirrors sample type routes used by the business UI. */
@RestController
@RequestMapping("/api/services/app/SampleType")
@RequirePermission("Pages.AbilityManagement.Sample")
public class SampleTypeController {
    private final CapabilityStore store;

    public SampleTypeController(CapabilityStore store) {
        this.store = store;
    }

    @GetMapping("/GetList")
    public AbpResponse<ListResult<SampleType>> getList() {
        return AbpResponse.ok(new ListResult<>(store.sampleTypes()));
    }

    @PostMapping("/GetList")
    public AbpResponse<ListResult<SampleType>> postGetList() {
        return getList();
    }

    @PostMapping("/GetListByOrg")
    public AbpResponse<ListResult<SampleType>> getListByOrg(@RequestBody(required = false) GetListByOrgInput input) {
        return AbpResponse.ok(new ListResult<>(store.sampleTypes(input == null ? null : input.orgId)));
    }

    @GetMapping("/GetListByOrg")
    public AbpResponse<ListResult<SampleType>> getListByOrgByQuery(@RequestParam(name = "OrgId", required = false) Long orgId) {
        GetListByOrgInput input = new GetListByOrgInput();
        input.orgId = orgId;
        return getListByOrg(input);
    }

    @PostMapping("/CreateOrUpdate")
    public AbpResponse<UUID> createOrUpdate(@RequestBody SampleType input) {
        try {
            return AbpResponse.ok(store.saveSampleType(input).id);
        } catch (IllegalArgumentException ex) {
            return AbpResponse.failed(ex.getMessage());
        }
    }

    @PostMapping("/GetForEdit")
    public AbpResponse<GetSampleTypeForEditOutput> getForEdit(@RequestBody(required = false) IdRequest input) {
        UUID id = parseUuid(input == null ? null : input.id);
        SampleType type = id == null ? new SampleType() : store.sampleType(id).orElseGet(SampleType::new);
        return AbpResponse.ok(new GetSampleTypeForEditOutput(type, store.orgUnits()));
    }

    @GetMapping("/GetForEdit")
    public AbpResponse<GetSampleTypeForEditOutput> getForEditByQuery(@RequestParam(name = "Id", required = false) String id) {
        IdRequest input = new IdRequest();
        input.id = id;
        return getForEdit(input);
    }

    @PostMapping("/DeleteSampleType")
    public AbpResponse<Void> deleteSampleType(@RequestBody IdRequest input) {
        store.deleteSampleType(input == null ? null : input.id);
        return AbpResponse.ok(null);
    }

    @DeleteMapping("/DeleteSampleType")
    public AbpResponse<Void> deleteSampleTypeByQuery(@RequestParam(name = "Id", required = false) String id) {
        store.deleteSampleType(id);
        return AbpResponse.ok(null);
    }

    private UUID parseUuid(String value) {
        try {
            return value == null || value.isBlank() ? null : UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static class GetListByOrgInput {
        public Long orgId;
    }

    public record GetSampleTypeForEditOutput(SampleType type, List<OrganizationUnit> orgList) {
    }
}
