package com.sgs.capability.controller;

import com.sgs.capability.dto.*;
import com.sgs.capability.model.Ability;
import com.sgs.capability.model.NameValueItem;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.security.AuthService;
import com.sgs.capability.service.CapabilityStore;
import com.sgs.capability.service.ExcelTransferService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Mirrors the original AbilityAppService routes. */
@RestController
@RequestMapping("/api/services/app/Ability")
@RequirePermission("Pages.AbilityManagement")
public class AbilityController {
    private final CapabilityStore store;
    private final ExcelTransferService excel;
    private final AuthService auth;

    public AbilityController(CapabilityStore store, ExcelTransferService excel, AuthService auth) {
        this.store = store;
        this.excel = excel;
        this.auth = auth;
    }

    @PostMapping("/FindPageAblibities")
    public AbpResponse<PageResult<Ability>> findPage(@RequestBody(required = false) FindAbilityRequest input,
                                                     HttpServletRequest request) {
        FindAbilityRequest safeInput = input == null ? new FindAbilityRequest() : input;
        String validationError = safeInput.validateOriginalPaging();
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        return AbpResponse.ok(store.findAbilities(safeInput, currentUserId(request)));
    }

    @GetMapping("/FindAllAblibities")
    public AbpResponse<List<Ability>> findAll(HttpServletRequest request) {
        return AbpResponse.ok(store.findAbilities(new FindAbilityRequest(), currentUserId(request)).items);
    }

    @PostMapping("/FindAllAblibities")
    public AbpResponse<List<Ability>> postFindAll(HttpServletRequest request) {
        return findAll(request);
    }

    @PostMapping("/GetAbilityForEdit")
    public AbpResponse<GetForEditOutput> getForEdit(@RequestBody(required = false) IdRequest input,
                                                    HttpServletRequest request) {
        GetForEditOutput output = new GetForEditOutput();
        output.abilityDto = input == null ? null : store.getAbility(input.id, currentUserId(request)).orElse(null);
        output.labList = store.labs();
        output.orgList = store.orgUnits();
        output.sampleTypeList = store.sampleTypes();
        return AbpResponse.ok(output);
    }

    @GetMapping("/GetAbilityForEdit")
    public AbpResponse<GetForEditOutput> getForEditByQuery(@RequestParam(name = "Id", required = false) String id,
                                                           HttpServletRequest request) {
        IdRequest input = new IdRequest();
        input.id = id;
        return getForEdit(input, request);
    }

    @PostMapping("/CreateAbility")
    public AbpResponse<Void> create(@RequestBody Ability input, HttpServletRequest request) {
        try {
            if (input != null) {
                input.id = null;
            }
            store.saveAbility(input, currentUserId(request));
            return AbpResponse.ok(null);
        } catch (IllegalArgumentException ex) {
            return AbpResponse.failed(ex.getMessage());
        }
    }

    @PostMapping("/UpdateAbility")
    public AbpResponse<Void> update(@RequestBody Ability input, HttpServletRequest request) {
        try {
            store.saveAbility(input, currentUserId(request));
            return AbpResponse.ok(null);
        } catch (IllegalArgumentException ex) {
            return AbpResponse.failed(ex.getMessage());
        }
    }

    @PutMapping("/UpdateAbility")
    public AbpResponse<Void> putUpdate(@RequestBody Ability input, HttpServletRequest request) {
        return update(input, request);
    }

    @PostMapping("/DeleteAbility")
    public AbpResponse<Void> delete(@RequestBody IdRequest input, HttpServletRequest request) {
        store.deleteAbility(input.id, currentUserId(request));
        return AbpResponse.ok(null);
    }

    @DeleteMapping("/DeleteAbility")
    public AbpResponse<Void> deleteByQuery(@RequestParam(name = "Id", required = false) String id,
                                           HttpServletRequest request) {
        store.deleteAbility(id, currentUserId(request));
        return AbpResponse.ok(null);
    }

    @PostMapping("/DeleteAll")
    public AbpResponse<Void> deleteAll(@RequestBody(required = false) DeleteAllAbilityRequest input,
                                       HttpServletRequest request) {
        store.deleteAbilitiesByOrgName(input == null ? null : input.orgName(), currentUserId(request));
        return AbpResponse.ok(null);
    }

    @DeleteMapping("/DeleteAll")
    public AbpResponse<Void> deleteAllByQuery(@RequestParam(name = "OrgName", required = false) String orgName,
                                              HttpServletRequest request) {
        store.deleteAbilitiesByOrgName(orgName, currentUserId(request));
        return AbpResponse.ok(null);
    }

    @GetMapping("/GetAllUnits")
    public AbpResponse<ListResult<?>> getAllUnits() {
        return AbpResponse.ok(new ListResult<>(store.orgUnits()));
    }

    @PostMapping("/GetTemplateExcel")
    public AbpResponse<FileDto> getTemplateExcel(@RequestBody(required = false) GetTemplateExcelInput input) {
        return AbpResponse.ok(excel.abilityTemplate(input == null ? null : input.orgId()));
    }

    @GetMapping("/GetTemplateExcel")
    public AbpResponse<FileDto> getTemplateExcelByQuery(@RequestParam(name = "OrgId", required = false) Long orgId) {
        return getTemplateExcel(new GetTemplateExcelInput(orgId));
    }

    @PostMapping("/ExportData")
    public AbpResponse<FileDto> exportData(@RequestBody(required = false) FindAbilityRequest input) {
        return AbpResponse.ok(excel.abilityExport(input));
    }

    @PostMapping("/SaveExcelData")
    public AbpResponse<Void> saveExcelData(@RequestBody(required = false) SaveAbilityExcelInput input,
                                           HttpServletRequest request) {
        excel.saveAbilityRows(input == null ? new SaveAbilityExcelInput() : input, currentUserId(request));
        return AbpResponse.ok(null);
    }

    @PostMapping("/GetOrgTypeLit")
    public AbpResponse<ListResult<NameValueItem>> getOrgTypeLit(@RequestBody(required = false) IdRequest input) {
        return AbpResponse.ok(new ListResult<>(store.orgTypeList(parseLong(input == null ? null : input.id))));
    }

    @GetMapping("/GetOrgTypeLit")
    public AbpResponse<ListResult<NameValueItem>> getOrgTypeLitByQuery(@RequestParam(name = "Id", required = false) String id) {
        IdRequest input = new IdRequest();
        input.id = id;
        return getOrgTypeLit(input);
    }

    @RequestMapping(value = "/GetMyOrgSetting", method = {RequestMethod.GET, RequestMethod.POST})
    public AbpResponse<ListResult<MyOrgSettingDto>> getMyOrgSetting(HttpServletRequest request) {
        Long userId = auth.currentUser(request.getHeader("Authorization")).map(context -> context.user().id).orElse(null);
        return AbpResponse.ok(new ListResult<>(store.myOrgSettings(userId)));
    }

    private Long parseLong(String value) {
        try {
            return value == null || value.isBlank() ? null : Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Long currentUserId(HttpServletRequest request) {
        return auth.currentUser(request.getHeader("Authorization")).map(context -> context.user().id).orElse(1L);
    }

    public record GetTemplateExcelInput(Long orgId) {
    }
}
