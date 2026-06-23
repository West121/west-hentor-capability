package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.dto.IdRequest;
import com.sgs.capability.model.Laboratory;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.CapabilityStore;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Mirrors laboratory management routes. */
@RestController
@RequestMapping("/api/services/app/Laboratory")
@RequirePermission("Pages.Administration.Laboratory")
public class LaboratoryController {
    private final CapabilityStore store;

    public LaboratoryController(CapabilityStore store) {
        this.store = store;
    }

    @GetMapping("/List")
    public AbpResponse<LaboratoryList> list() {
        return AbpResponse.ok(new LaboratoryList(store.labs()));
    }

    @PostMapping("/List")
    public AbpResponse<LaboratoryList> postList() {
        return list();
    }

    @PostMapping("/CreateOrUpdate")
    public AbpResponse<UUID> save(@RequestBody Laboratory input) {
        String validationError = validateLaboratoryInput(input);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        try {
            return AbpResponse.ok(store.saveLab(input).id);
        } catch (IllegalArgumentException ex) {
            return AbpResponse.failed(ex.getMessage());
        }
    }

    @PostMapping("/GetLabForEdit")
    public AbpResponse<Laboratory> getLabForEdit(@RequestBody(required = false) IdRequest input) {
        UUID id = parseUuid(input == null ? null : input.id);
        return AbpResponse.ok(id == null ? new Laboratory() : store.lab(id).orElseGet(Laboratory::new));
    }

    @GetMapping("/GetLabForEdit")
    public AbpResponse<Laboratory> getLabForEditByQuery(@RequestParam(name = "Id", required = false) String id) {
        IdRequest input = new IdRequest();
        input.id = id;
        return getLabForEdit(input);
    }

    @PostMapping("/DeleteLab")
    public AbpResponse<Void> delete(@RequestBody IdRequest input) {
        store.deleteLab(input.id);
        return AbpResponse.ok(null);
    }

    @DeleteMapping("/DeleteLab")
    public AbpResponse<Void> deleteByQuery(@RequestParam(name = "Id", required = false) String id) {
        store.deleteLab(id);
        return AbpResponse.ok(null);
    }

    /** Original endpoint returns a wrapper with a list property. */
    public record LaboratoryList(List<Laboratory> list) {
    }

    private String validateLaboratoryInput(Laboratory input) {
        if (input == null || isBlank(input.code) || isBlank(input.name)) {
            // 原 LaboratoryDto 要求 Code 和 Name 必填。
            return "Validation failed";
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private UUID parseUuid(String value) {
        try {
            return value == null || value.isBlank() ? null : UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
