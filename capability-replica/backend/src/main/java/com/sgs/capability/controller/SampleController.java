package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.dto.IdRequest;
import com.sgs.capability.dto.ListResult;
import com.sgs.capability.model.Sample;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.CapabilityStore;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Mirrors sample routes used by sample management. */
@RestController
@RequestMapping("/api/services/app/Sample")
@RequirePermission("Pages.AbilityManagement.Sample")
public class SampleController {
    private final CapabilityStore store;

    public SampleController(CapabilityStore store) {
        this.store = store;
    }

    @PostMapping("/GetList")
    public AbpResponse<ListResult<Sample>> getList(@RequestBody(required = false) GetSampleListInput input) {
        return AbpResponse.ok(new ListResult<>(store.samples(parseUuid(input == null ? null : input.typeId))));
    }

    @GetMapping("/GetList")
    public AbpResponse<ListResult<Sample>> getListByQuery(@RequestParam(name = "TypeId", required = false) String typeId) {
        GetSampleListInput input = new GetSampleListInput();
        input.typeId = typeId;
        return getList(input);
    }

    @PostMapping("/CreateOrUpdate")
    public AbpResponse<UUID> createOrUpdate(@RequestBody Sample input) {
        try {
            return AbpResponse.ok(store.saveSample(input).id);
        } catch (IllegalArgumentException ex) {
            return AbpResponse.failed(ex.getMessage());
        }
    }

    @PostMapping("/GetForEdit")
    public AbpResponse<Sample> getForEdit(@RequestBody(required = false) IdRequest input) {
        UUID id = parseUuid(input == null ? null : input.id);
        return AbpResponse.ok(id == null ? new Sample() : store.sample(id).orElseGet(Sample::new));
    }

    @GetMapping("/GetForEdit")
    public AbpResponse<Sample> getForEditByQuery(@RequestParam(name = "Id", required = false) String id) {
        IdRequest input = new IdRequest();
        input.id = id;
        return getForEdit(input);
    }

    @PostMapping("/DeleteSample")
    public AbpResponse<Void> deleteSample(@RequestBody IdRequest input) {
        store.deleteSample(input == null ? null : input.id);
        return AbpResponse.ok(null);
    }

    @DeleteMapping("/DeleteSample")
    public AbpResponse<Void> deleteSampleByQuery(@RequestParam(name = "Id", required = false) String id) {
        store.deleteSample(id);
        return AbpResponse.ok(null);
    }

    private UUID parseUuid(String value) {
        try {
            return value == null || value.isBlank() ? null : UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static class GetSampleListInput {
        public String typeId;
    }
}
