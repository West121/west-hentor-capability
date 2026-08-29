package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.model.AbilityProperty;
import com.sgs.capability.model.OrgAbilitySetting;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.CapabilityStore;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Mirrors organization-specific ability field settings. */
@RestController
@RequestMapping("/api/services/app/AbilityProperty")
@RequirePermission("Pages.AbilityManagement.AbilitySetting")
public class AbilityPropertyController {
    private final CapabilityStore store;

    public AbilityPropertyController(CapabilityStore store) {
        this.store = store;
    }

    @GetMapping("/AbilityPropertyList")
    public AbpResponse<List<AbilityProperty>> list() {
        return AbpResponse.ok(store.abilityProperties(1L));
    }

    @PostMapping("/AbilityPropertyList")
    public AbpResponse<List<AbilityProperty>> postList() {
        return list();
    }

    @PostMapping("/OrgAbilityPropertyList")
    public AbpResponse<OrgAbilitySettingOutput> orgList(@RequestBody OrgAbilitySetting input) {
        long orgId = input == null ? 1L : input.orgId;
        OrgAbilitySetting setting = store.orgSetting(orgId);
        return AbpResponse.ok(new OrgAbilitySettingOutput(store.orgAbilityProperties(orgId), setting.isPublic, setting.description));
    }

    @PostMapping("/GetOrgAbilitySetting")
    public AbpResponse<List<String>> getSetting(@RequestBody OrgAbilitySetting input) {
        return orgAbilitySetting(input == null ? 1L : input.orgId);
    }

    @GetMapping("/GetOrgAbilitySetting")
    public AbpResponse<List<String>> getSettingByQuery(@RequestParam(name = "OrgId", required = false) Long orgId) {
        return orgAbilitySetting(orgId == null ? 1L : orgId);
    }

    @PostMapping("/SaveOrgSetting")
    public AbpResponse<Void> save(@RequestBody OrgAbilitySetting input) {
        store.saveOrgSetting(input);
        return AbpResponse.ok(null);
    }

    private AbpResponse<List<String>> orgAbilitySetting(long orgId) {
        return AbpResponse.ok(store.orgSettingPropertyNames(orgId));
    }

    /** Response payload used by the field setting page. */
    public record OrgAbilitySettingOutput(List<AbilityProperty> propertyList, boolean isPublic, String description) {
    }
}
