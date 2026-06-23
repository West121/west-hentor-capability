package com.sgs.capability.controller;

import com.sgs.capability.dto.*;
import com.sgs.capability.model.AbilityHistoryItem;
import com.sgs.capability.model.AuditLog;
import com.sgs.capability.model.EntityChangeItem;
import com.sgs.capability.model.EntityPropertyChangeItem;
import com.sgs.capability.model.NameValueItem;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.CapabilityStore;
import com.sgs.capability.service.ExcelTransferService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Provides mock audit and ability history rows for copied log pages. */
@RestController
public class LogController {
    private final CapabilityStore store;
    private final ExcelTransferService excel;

    public LogController(CapabilityStore store, ExcelTransferService excel) {
        this.store = store;
        this.excel = excel;
    }

    @PostMapping("/api/services/app/AbilityHistory/GetAbilityHistory")
    @RequirePermission("Pages.Log.AbilityHistory")
    public AbpResponse<PageResult<AbilityHistoryItem>> abilityHistory(@RequestBody(required = false) GetAbilityHistoryInput input) {
        GetAbilityHistoryInput safeInput = input == null ? new GetAbilityHistoryInput() : input;
        String validationError = validatePagedInput(safeInput.skipCount, safeInput.maxResultCount);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        return AbpResponse.ok(store.abilityHistoryPage(safeInput.sorting, safeInput.skipCount, safeInput.maxResultCount));
    }

    // Match the original Angular proxy contract: GET with PascalCase query names.
    @GetMapping("/api/services/app/AbilityHistory/GetAbilityHistory")
    @RequirePermission("Pages.Log.AbilityHistory")
    public AbpResponse<PageResult<AbilityHistoryItem>> abilityHistoryByQuery(@RequestParam(name = "Sorting", required = false) String sorting,
                                                                             @RequestParam(name = "SkipCount", defaultValue = "0") int skipCount,
                                                                             @RequestParam(name = "MaxResultCount", defaultValue = "10") int maxResultCount) {
        String validationError = validatePagedInput(skipCount, maxResultCount);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        return AbpResponse.ok(store.abilityHistoryPage(sorting, skipCount, maxResultCount));
    }

    @PostMapping("/api/services/app/AbilityHistory/GetHistoryDetail")
    @RequirePermission("Pages.Log.AbilityHistory")
    public AbpResponse<ListResult<AbilityHistoryDetailDto>> abilityHistoryDetail(@RequestBody(required = false) IdRequest input) {
        return AbpResponse.ok(new ListResult<>(store.abilityHistoryDetails(parseLong(input == null ? null : input.id))));
    }

    // Keep Id capitalized because the generated client sends this exact query key.
    @GetMapping("/api/services/app/AbilityHistory/GetHistoryDetail")
    @RequirePermission("Pages.Log.AbilityHistory")
    public AbpResponse<ListResult<AbilityHistoryDetailDto>> abilityHistoryDetailByQuery(@RequestParam(name = "Id", required = false) String id) {
        return AbpResponse.ok(new ListResult<>(store.abilityHistoryDetails(parseLong(id))));
    }

    @PostMapping("/api/services/app/AuditLog/GetAuditLogs")
    @RequirePermission("Pages.Administration.AuditLogs")
    public AbpResponse<PageResult<AuditLog>> auditLogs(@RequestBody(required = false) GetAuditLogsInput input) {
        GetAuditLogsInput safeInput = input == null ? new GetAuditLogsInput() : input;
        String validationError = validatePagedInput(safeInput.skipCount, safeInput.maxResultCount);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        return AbpResponse.ok(store.auditLogs(safeInput));
    }

    @GetMapping("/api/services/app/AuditLog/GetAuditLogs")
    @RequirePermission("Pages.Administration.AuditLogs")
    public AbpResponse<PageResult<AuditLog>> auditLogsByQuery(@RequestParam(name = "StartDate", required = false) String startDate,
                                                              @RequestParam(name = "EndDate", required = false) String endDate,
                                                              @RequestParam(name = "UserName", required = false) String userName,
                                                              @RequestParam(name = "ServiceName", required = false) String serviceName,
                                                              @RequestParam(name = "MethodName", required = false) String methodName,
                                                              @RequestParam(name = "BrowserInfo", required = false) String browserInfo,
                                                              @RequestParam(name = "HasException", required = false) Boolean hasException,
                                                              @RequestParam(name = "MinExecutionDuration", required = false) Integer minExecutionDuration,
                                                              @RequestParam(name = "MaxExecutionDuration", required = false) Integer maxExecutionDuration,
                                                              @RequestParam(name = "Sorting", required = false) String sorting,
                                                              @RequestParam(name = "SkipCount", defaultValue = "0") int skipCount,
                                                              @RequestParam(name = "MaxResultCount", defaultValue = "10") int maxResultCount) {
        return auditLogs(auditLogsInput(startDate, endDate, userName, serviceName, methodName, browserInfo,
                hasException, minExecutionDuration, maxExecutionDuration, sorting, skipCount, maxResultCount));
    }

    @PostMapping("/api/services/app/AuditLog/GetAuditLogsToExcel")
    @RequirePermission("Pages.Administration.AuditLogs")
    public AbpResponse<FileDto> auditLogsToExcel(@RequestBody(required = false) GetAuditLogsInput input) {
        GetAuditLogsInput safeInput = input == null ? new GetAuditLogsInput() : input;
        String validationError = validatePagedInput(safeInput.skipCount, safeInput.maxResultCount);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        return AbpResponse.ok(excel.auditLogExport(store.filteredAuditLogs(safeInput)));
    }

    @GetMapping("/api/services/app/AuditLog/GetAuditLogsToExcel")
    @RequirePermission("Pages.Administration.AuditLogs")
    public AbpResponse<FileDto> auditLogsToExcelByQuery(@RequestParam(name = "StartDate", required = false) String startDate,
                                                        @RequestParam(name = "EndDate", required = false) String endDate,
                                                        @RequestParam(name = "UserName", required = false) String userName,
                                                        @RequestParam(name = "ServiceName", required = false) String serviceName,
                                                        @RequestParam(name = "MethodName", required = false) String methodName,
                                                        @RequestParam(name = "BrowserInfo", required = false) String browserInfo,
                                                        @RequestParam(name = "HasException", required = false) Boolean hasException,
                                                        @RequestParam(name = "MinExecutionDuration", required = false) Integer minExecutionDuration,
                                                        @RequestParam(name = "MaxExecutionDuration", required = false) Integer maxExecutionDuration,
                                                        @RequestParam(name = "Sorting", required = false) String sorting,
                                                        @RequestParam(name = "SkipCount", defaultValue = "0") int skipCount,
                                                        @RequestParam(name = "MaxResultCount", defaultValue = "10") int maxResultCount) {
        return auditLogsToExcel(auditLogsInput(startDate, endDate, userName, serviceName, methodName, browserInfo,
                hasException, minExecutionDuration, maxExecutionDuration, sorting, skipCount, maxResultCount));
    }

    @RequestMapping(value = "/api/services/app/AuditLog/GetEntityHistoryObjectTypes", method = {RequestMethod.GET, RequestMethod.POST})
    @RequirePermission("Pages.Administration.AuditLogs")
    public AbpResponse<List<NameValueItem>> entityHistoryObjectTypes() {
        return AbpResponse.ok(store.entityHistoryObjectTypes());
    }

    @PostMapping("/api/services/app/AuditLog/GetEntityChanges")
    @RequirePermission("Pages.Administration.AuditLogs")
    public AbpResponse<PageResult<EntityChangeItem>> entityChanges(@RequestBody(required = false) GetEntityChangeInput input) {
        GetEntityChangeInput safeInput = input == null ? new GetEntityChangeInput() : input;
        String validationError = validatePagedInput(safeInput.skipCount, safeInput.maxResultCount);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        return AbpResponse.ok(store.entityChanges(safeInput));
    }

    @GetMapping("/api/services/app/AuditLog/GetEntityChanges")
    @RequirePermission("Pages.Administration.AuditLogs")
    public AbpResponse<PageResult<EntityChangeItem>> entityChangesByQuery(@RequestParam(name = "StartDate", required = false) String startDate,
                                                                          @RequestParam(name = "EndDate", required = false) String endDate,
                                                                          @RequestParam(name = "UserName", required = false) String userName,
                                                                          @RequestParam(name = "EntityTypeFullName", required = false) String entityTypeFullName,
                                                                          @RequestParam(name = "Sorting", required = false) String sorting,
                                                                          @RequestParam(name = "SkipCount", defaultValue = "0") int skipCount,
                                                                          @RequestParam(name = "MaxResultCount", defaultValue = "10") int maxResultCount) {
        return entityChanges(entityChangeInput(startDate, endDate, userName, entityTypeFullName, sorting, skipCount, maxResultCount));
    }

    @PostMapping("/api/services/app/AuditLog/GetEntityTypeChanges")
    @RequirePermission("Pages.Administration.AuditLogs")
    public AbpResponse<PageResult<EntityChangeItem>> entityTypeChanges(@RequestBody(required = false) GetEntityTypeChangeInput input) {
        GetEntityTypeChangeInput safeInput = input == null ? new GetEntityTypeChangeInput() : input;
        String validationError = validatePagedInput(safeInput.skipCount, safeInput.maxResultCount);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        return AbpResponse.ok(store.entityTypeChanges(safeInput));
    }

    @GetMapping("/api/services/app/AuditLog/GetEntityTypeChanges")
    @RequirePermission("Pages.Administration.AuditLogs")
    public AbpResponse<PageResult<EntityChangeItem>> entityTypeChangesByQuery(@RequestParam(name = "EntityTypeFullName", required = false) String entityTypeFullName,
                                                                              @RequestParam(name = "EntityId", required = false) String entityId,
                                                                              @RequestParam(name = "Sorting", required = false) String sorting,
                                                                              @RequestParam(name = "SkipCount", defaultValue = "0") int skipCount,
                                                                              @RequestParam(name = "MaxResultCount", defaultValue = "10") int maxResultCount) {
        GetEntityTypeChangeInput input = new GetEntityTypeChangeInput();
        input.entityTypeFullName = entityTypeFullName;
        input.entityId = entityId;
        input.sorting = sorting == null ? input.sorting : sorting;
        input.skipCount = skipCount;
        input.maxResultCount = maxResultCount;
        return entityTypeChanges(input);
    }

    @PostMapping("/api/services/app/AuditLog/GetEntityChangesToExcel")
    @RequirePermission("Pages.Administration.AuditLogs")
    public AbpResponse<FileDto> entityChangesToExcel(@RequestBody(required = false) GetEntityChangeInput input) {
        GetEntityChangeInput safeInput = input == null ? new GetEntityChangeInput() : input;
        String validationError = validatePagedInput(safeInput.skipCount, safeInput.maxResultCount);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        return AbpResponse.ok(excel.entityChangesExport(store.filteredEntityChanges(safeInput)));
    }

    @GetMapping("/api/services/app/AuditLog/GetEntityChangesToExcel")
    @RequirePermission("Pages.Administration.AuditLogs")
    public AbpResponse<FileDto> entityChangesToExcelByQuery(@RequestParam(name = "StartDate", required = false) String startDate,
                                                            @RequestParam(name = "EndDate", required = false) String endDate,
                                                            @RequestParam(name = "UserName", required = false) String userName,
                                                            @RequestParam(name = "EntityTypeFullName", required = false) String entityTypeFullName,
                                                            @RequestParam(name = "Sorting", required = false) String sorting,
                                                            @RequestParam(name = "SkipCount", defaultValue = "0") int skipCount,
                                                            @RequestParam(name = "MaxResultCount", defaultValue = "10") int maxResultCount) {
        return entityChangesToExcel(entityChangeInput(startDate, endDate, userName, entityTypeFullName, sorting, skipCount, maxResultCount));
    }

    @RequestMapping(value = "/api/services/app/AuditLog/GetEntityPropertyChanges", method = {RequestMethod.GET, RequestMethod.POST})
    @RequirePermission("Pages.Administration.AuditLogs")
    public AbpResponse<List<EntityPropertyChangeItem>> entityPropertyChanges(
            @RequestParam(required = false) Long entityChangeId,
            @RequestBody(required = false) Map<String, Object> input) {
        Long resolvedId = entityChangeId == null ? idFromBody(input) : entityChangeId;
        return AbpResponse.ok(store.entityPropertyChanges(resolvedId));
    }

    private Long idFromBody(Map<String, Object> input) {
        if (input == null || !input.containsKey("entityChangeId")) {
            return null;
        }
        Object value = input.get("entityChangeId");
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private GetAuditLogsInput auditLogsInput(String startDate, String endDate, String userName, String serviceName,
                                             String methodName, String browserInfo, Boolean hasException,
                                             Integer minExecutionDuration, Integer maxExecutionDuration,
                                             String sorting, int skipCount, int maxResultCount) {
        GetAuditLogsInput input = new GetAuditLogsInput();
        input.startDate = startDate;
        input.endDate = endDate;
        input.userName = userName;
        input.serviceName = serviceName;
        input.methodName = methodName;
        input.browserInfo = browserInfo;
        input.hasException = hasException;
        input.minExecutionDuration = minExecutionDuration;
        input.maxExecutionDuration = maxExecutionDuration;
        input.sorting = sorting == null ? input.sorting : sorting;
        input.skipCount = skipCount;
        input.maxResultCount = maxResultCount;
        return input;
    }

    private GetEntityChangeInput entityChangeInput(String startDate, String endDate, String userName,
                                                   String entityTypeFullName, String sorting, int skipCount,
                                                   int maxResultCount) {
        GetEntityChangeInput input = new GetEntityChangeInput();
        input.startDate = startDate;
        input.endDate = endDate;
        input.userName = userName;
        input.entityTypeFullName = entityTypeFullName;
        input.sorting = sorting == null ? input.sorting : sorting;
        input.skipCount = skipCount;
        input.maxResultCount = maxResultCount;
        return input;
    }

    private <T> List<T> page(List<T> rows, int skipCount, int maxResultCount) {
        int skip = Math.max(skipCount, 0);
        int take = maxResultCount <= 0 ? 10 : maxResultCount;
        return rows.stream().skip(skip).limit(take).toList();
    }

    private String validatePagedInput(int skipCount, int maxResultCount) {
        if (skipCount < 0 || maxResultCount < 1 || maxResultCount > 1000) {
            // 原 PagedAndSortedInputDto 要求 MaxResultCount 为 1-1000，SkipCount 不能为负。
            return "Validation failed";
        }
        return null;
    }

    private Long parseLong(String value) {
        try {
            return value == null || value.isBlank() ? null : Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static class GetAbilityHistoryInput {
        public int skipCount = 0;
        public int maxResultCount = 10;
        public String sorting = "changeTime DESC";
    }
}
