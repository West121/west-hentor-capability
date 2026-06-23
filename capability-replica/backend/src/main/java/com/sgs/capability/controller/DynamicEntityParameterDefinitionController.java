package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.CapabilityStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Mirrors DynamicEntityParameterDefinitionAppService lookup endpoints. */
@RestController
@RequestMapping("/api/services/app/DynamicEntityParameterDefinition")
@RequirePermission("Pages.Administration.DynamicParameters")
public class DynamicEntityParameterDefinitionController {
    private final CapabilityStore store;

    public DynamicEntityParameterDefinitionController(CapabilityStore store) {
        this.store = store;
    }

    @GetMapping("/GetAllAllowedInputTypeNames")
    public AbpResponse<List<String>> getAllAllowedInputTypeNames() {
        return AbpResponse.ok(store.allowedDynamicInputTypeNames());
    }

    @PostMapping("/GetAllAllowedInputTypeNames")
    public AbpResponse<List<String>> postGetAllAllowedInputTypeNames() {
        return getAllAllowedInputTypeNames();
    }

    @GetMapping("/GetAllEntities")
    public AbpResponse<List<String>> getAllEntities() {
        return AbpResponse.ok(store.dynamicEntityNames());
    }

    @PostMapping("/GetAllEntities")
    public AbpResponse<List<String>> postGetAllEntities() {
        return getAllEntities();
    }
}
