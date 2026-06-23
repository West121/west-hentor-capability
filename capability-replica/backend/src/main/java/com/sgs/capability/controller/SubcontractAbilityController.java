package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.dto.FileDto;
import com.sgs.capability.dto.FindAbilityRequest;
import com.sgs.capability.dto.PageResult;
import com.sgs.capability.dto.SaveSubcontractAbilityExcelInput;
import com.sgs.capability.model.SubcontractAbility;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.CapabilityStore;
import com.sgs.capability.service.ExcelTransferService;
import org.springframework.web.bind.annotation.*;

/** Mirrors SubcontractAbilityAppService for outsourcing ability rows. */
@RestController
@RequestMapping("/api/services/app/SubcontractAbility")
@RequirePermission("Pages.AbilityManagement")
public class SubcontractAbilityController {
    private final CapabilityStore store;
    private final ExcelTransferService excel;

    public SubcontractAbilityController(CapabilityStore store, ExcelTransferService excel) {
        this.store = store;
        this.excel = excel;
    }

    @PostMapping("/FindList")
    public AbpResponse<PageResult<SubcontractAbility>> findList(@RequestBody(required = false) FindAbilityRequest input) {
        FindAbilityRequest safeInput = input == null ? new FindAbilityRequest() : input;
        String validationError = safeInput.validateOriginalPaging();
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        return AbpResponse.ok(store.findSubcontractAbilities(safeInput));
    }

    @PostMapping("/GetTemplateExcel")
    public AbpResponse<FileDto> getTemplateExcel() {
        return AbpResponse.ok(excel.subcontractTemplate());
    }

    @PostMapping("/SaveExcelData")
    public AbpResponse<Void> saveExcelData(@RequestBody(required = false) SaveSubcontractAbilityExcelInput input) {
        SaveSubcontractAbilityExcelInput safeInput = input == null ? new SaveSubcontractAbilityExcelInput() : input;
        String validationError = validateFileDto(safeInput.file);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        excel.saveSubcontractRows(safeInput);
        return AbpResponse.ok(null);
    }

    private String validateFileDto(FileDto file) {
        if (file != null && (!hasText(file.fileName) || !hasText(file.fileToken))) {
            // 原 FileDto 只要求 FileName 和 FileToken 必填，FileType 可为空。
            return "Validation failed";
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
