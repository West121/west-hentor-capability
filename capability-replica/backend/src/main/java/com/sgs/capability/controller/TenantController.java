package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.dto.IdRequest;
import com.sgs.capability.dto.PageResult;
import com.sgs.capability.model.FeatureItem;
import com.sgs.capability.model.NameValueItem;
import com.sgs.capability.model.TenantItem;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.CapabilityStore;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Mirrors TenantAppService management routes. */
@RestController
@RequestMapping("/api/services/app/Tenant")
@RequirePermission("Pages.Tenants")
public class TenantController {
    private final CapabilityStore store;

    public TenantController(CapabilityStore store) {
        this.store = store;
    }

    @PostMapping("/GetTenants")
    public AbpResponse<PageResult<TenantItem>> getTenants(@RequestBody(required = false) GetTenantsInput input) {
        GetTenantsInput safeInput = input == null ? new GetTenantsInput() : input;
        String validationError = validatePagedInput(safeInput.skipCount, safeInput.maxResultCount);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        return AbpResponse.ok(store.tenants(safeInput.filter, safeInput.editionId, safeInput.editionIdSpecified,
                safeInput.skipCount, safeInput.maxResultCount, safeInput.sorting));
    }

    @GetMapping("/GetTenants")
    public AbpResponse<PageResult<TenantItem>> getTenantsByQuery(@RequestParam(name = "Filter", required = false) String filter,
                                                                 @RequestParam(name = "EditionId", required = false) Integer editionId,
                                                                 @RequestParam(name = "EditionIdSpecified", defaultValue = "false") boolean editionIdSpecified,
                                                                 @RequestParam(name = "Sorting", required = false) String sorting,
                                                                 @RequestParam(name = "SkipCount", defaultValue = "0") int skipCount,
                                                                 @RequestParam(name = "MaxResultCount", defaultValue = "10") int maxResultCount) {
        GetTenantsInput input = new GetTenantsInput();
        input.filter = filter;
        input.editionId = editionId;
        input.editionIdSpecified = editionIdSpecified;
        input.sorting = sorting;
        input.skipCount = skipCount;
        input.maxResultCount = maxResultCount;
        return getTenants(input);
    }

    @PostMapping("/CreateTenant")
    public AbpResponse<Void> createTenant(@RequestBody TenantItem input) {
        TenantItem safeInput = input == null ? new TenantItem() : input;
        String validationError = validateCreateTenantInput(safeInput);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        store.createTenant(safeInput);
        return AbpResponse.ok(null);
    }

    @PostMapping("/GetTenantForEdit")
    public AbpResponse<TenantItem> getTenantForEdit(@RequestBody(required = false) IdRequest input) {
        return AbpResponse.ok(store.tenant(parseInt(input == null ? null : input.id)).orElse(null));
    }

    @GetMapping("/GetTenantForEdit")
    public AbpResponse<TenantItem> getTenantForEditByQuery(@RequestParam(name = "Id", required = false) String id) {
        return getTenantForEdit(idRequest(id));
    }

    @PostMapping("/UpdateTenant")
    public AbpResponse<Void> updateTenant(@RequestBody TenantItem input) {
        TenantItem safeInput = input == null ? new TenantItem() : input;
        String validationError = validateTenantEditInput(safeInput);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        store.updateTenant(safeInput);
        return AbpResponse.ok(null);
    }

    @PutMapping("/UpdateTenant")
    public AbpResponse<Void> putUpdateTenant(@RequestBody TenantItem input) {
        return updateTenant(input);
    }

    @PostMapping("/DeleteTenant")
    public AbpResponse<Void> deleteTenant(@RequestBody IdRequest input) {
        store.deleteTenant(parseInt(input == null ? null : input.id));
        return AbpResponse.ok(null);
    }

    @DeleteMapping("/DeleteTenant")
    public AbpResponse<Void> deleteTenantByQuery(@RequestParam(name = "Id", required = false) String id) {
        store.deleteTenant(parseInt(id));
        return AbpResponse.ok(null);
    }

    @PostMapping("/GetTenantFeaturesForEdit")
    public AbpResponse<GetTenantFeaturesEditOutput> getTenantFeaturesForEdit(@RequestBody(required = false) IdRequest input) {
        Integer id = parseInt(input == null ? null : input.id);
        return AbpResponse.ok(new GetTenantFeaturesEditOutput(store.tenantFeatureValues(id), store.features()));
    }

    @GetMapping("/GetTenantFeaturesForEdit")
    public AbpResponse<GetTenantFeaturesEditOutput> getTenantFeaturesForEditByQuery(@RequestParam(name = "Id", required = false) String id) {
        return getTenantFeaturesForEdit(idRequest(id));
    }

    @PostMapping("/UpdateTenantFeatures")
    public AbpResponse<Void> updateTenantFeatures(@RequestBody UpdateTenantFeaturesInput input) {
        String validationError = validateUpdateTenantFeaturesInput(input);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        store.updateTenantFeatures(input.id, input.featureValues);
        return AbpResponse.ok(null);
    }

    @PutMapping("/UpdateTenantFeatures")
    public AbpResponse<Void> putUpdateTenantFeatures(@RequestBody UpdateTenantFeaturesInput input) {
        return updateTenantFeatures(input);
    }

    @PostMapping("/ResetTenantSpecificFeatures")
    public AbpResponse<Void> resetTenantSpecificFeatures(@RequestBody IdRequest input) {
        store.resetTenantFeatures(parseInt(input == null ? null : input.id));
        return AbpResponse.ok(null);
    }

    @PostMapping("/UnlockTenantAdmin")
    public AbpResponse<Void> unlockTenantAdmin(@RequestBody IdRequest input) {
        store.unlockTenantAdmin(parseInt(input == null ? null : input.id));
        return AbpResponse.ok(null);
    }

    private Integer parseInt(String value) {
        try {
            return value == null || value.isBlank() ? null : Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private IdRequest idRequest(String id) {
        IdRequest request = new IdRequest();
        request.id = id;
        return request;
    }

    private String validateCreateTenantInput(TenantItem input) {
        if (safe(input.tenancyName).isBlank() || safe(input.name).isBlank() || safe(input.adminEmailAddress).isBlank()) {
            return "Validation failed";
        }
        if (safe(input.tenancyName).length() > 64 || safe(input.name).length() > 128) {
            // 原 CreateTenantInput 限制 TenancyName 64、Name 128。
            return "Validation failed";
        }
        if (!safe(input.tenancyName).matches("^[a-zA-Z][a-zA-Z0-9_-]{1,}$")) {
            // 原 CreateTenantInput 要求租户名以字母开头且只含字母、数字、下划线和短横线。
            return "Validation failed";
        }
        if (safe(input.adminEmailAddress).length() > 256 || safe(input.adminPassword).length() > 128
                || safe(input.connectionString).length() > 1024) {
            // 原 CreateTenantInput 限制邮箱、初始密码和连接字符串长度。
            return "Validation failed";
        }
        if (!input.adminEmailAddress.contains("@")) {
            return "Validation failed";
        }
        return null;
    }

    private String validateTenantEditInput(TenantItem input) {
        if (safe(input.tenancyName).isBlank() || safe(input.name).isBlank()) {
            return "Validation failed";
        }
        if (safe(input.tenancyName).length() > 64 || safe(input.name).length() > 128) {
            // 原 TenantEditDto 限制 TenancyName 64、Name 128。
            return "Validation failed";
        }
        return null;
    }

    private String validateUpdateTenantFeaturesInput(UpdateTenantFeaturesInput input) {
        if (input == null || input.id == null || input.id < 1 || input.featureValues == null) {
            // 原 UpdateTenantFeaturesInput 要求 Id 为正数且 FeatureValues 必填。
            return "Validation failed";
        }
        return null;
    }

    private String validatePagedInput(int skipCount, int maxResultCount) {
        if (skipCount < 0 || maxResultCount < 1 || maxResultCount > 1000) {
            // 原 PagedInputDto 要求 MaxResultCount 为 1-1000，SkipCount 不能为负。
            return "Validation failed";
        }
        return null;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public static class GetTenantsInput {
        public String filter;
        public Integer editionId;
        public boolean editionIdSpecified;
        public String sorting;
        public int skipCount;
        public int maxResultCount = 10;
    }

    public static class UpdateTenantFeaturesInput {
        public Integer id;
        public List<NameValueItem> featureValues;
    }

    public record GetTenantFeaturesEditOutput(List<NameValueItem> featureValues, List<FeatureItem> features) {
    }
}
