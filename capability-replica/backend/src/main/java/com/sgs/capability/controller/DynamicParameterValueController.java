package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.dto.IdRequest;
import com.sgs.capability.dto.ListResult;
import com.sgs.capability.model.DynamicParameterValueItem;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.CapabilityStore;
import org.springframework.web.bind.annotation.*;

/** Mirrors DynamicParameterValueAppService value CRUD. */
@RestController
@RequestMapping("/api/services/app/DynamicParameterValue")
@RequirePermission("Pages.Administration.DynamicParameterValue")
public class DynamicParameterValueController {
    private final CapabilityStore store;

    public DynamicParameterValueController(CapabilityStore store) {
        this.store = store;
    }

    @GetMapping("/GetAll")
    public AbpResponse<ListResult<DynamicParameterValueItem>> getAll(@RequestParam(name = "Id", required = false) Integer id) {
        return AbpResponse.ok(new ListResult<>(store.dynamicParameterValues(id)));
    }

    @PostMapping("/GetAll")
    public AbpResponse<ListResult<DynamicParameterValueItem>> postGetAll(@RequestBody(required = false) ParameterInput input) {
        return AbpResponse.ok(new ListResult<>(store.dynamicParameterValues(input == null ? null : input.dynamicParameterId())));
    }

    @PostMapping("/Get")
    public AbpResponse<DynamicParameterValueItem> get(@RequestBody(required = false) IdRequest input) {
        return getById(parseInt(input == null ? null : input.id));
    }

    @GetMapping("/Get")
    public AbpResponse<DynamicParameterValueItem> getByQuery(@RequestParam(required = false) Integer id) {
        return getById(id);
    }

    private AbpResponse<DynamicParameterValueItem> getById(Integer id) {
        return AbpResponse.ok(store.dynamicParameterValue(id).orElse(null));
    }

    @PostMapping("/GetAllValuesOfDynamicParameter")
    public AbpResponse<ListResult<DynamicParameterValueItem>> getAllValuesOfDynamicParameter(
            @RequestBody(required = false) ParameterInput input) {
        return getAllValuesOfDynamicParameterById(input == null ? null : input.dynamicParameterId());
    }

    @GetMapping("/GetAllValuesOfDynamicParameter")
    public AbpResponse<ListResult<DynamicParameterValueItem>> getAllValuesOfDynamicParameterByQuery(
            @RequestParam(name = "Id", required = false) Integer id) {
        return getAllValuesOfDynamicParameterById(id);
    }

    private AbpResponse<ListResult<DynamicParameterValueItem>> getAllValuesOfDynamicParameterById(Integer id) {
        return AbpResponse.ok(new ListResult<>(store.dynamicParameterValues(id)));
    }

    @PostMapping("/Add")
    @RequirePermission("Pages.Administration.DynamicParameterValue.Create")
    public AbpResponse<Void> add(@RequestBody DynamicParameterValueItem input) {
        input.id = null;
        store.saveDynamicParameterValue(input);
        return AbpResponse.ok(null);
    }

    @PostMapping("/Update")
    @RequirePermission("Pages.Administration.DynamicParameterValue.Edit")
    public AbpResponse<Void> update(@RequestBody DynamicParameterValueItem input) {
        store.saveDynamicParameterValue(input);
        return AbpResponse.ok(null);
    }

    @PutMapping("/Update")
    @RequirePermission("Pages.Administration.DynamicParameterValue.Edit")
    public AbpResponse<Void> putUpdate(@RequestBody DynamicParameterValueItem input) {
        return update(input);
    }

    @PostMapping("/Delete")
    @RequirePermission("Pages.Administration.DynamicParameterValue.Delete")
    public AbpResponse<Void> delete(@RequestBody IdRequest input) {
        store.deleteDynamicParameterValue(parseInt(input == null ? null : input.id));
        return AbpResponse.ok(null);
    }

    @DeleteMapping("/Delete")
    @RequirePermission("Pages.Administration.DynamicParameterValue.Delete")
    public AbpResponse<Void> deleteByQuery(@RequestParam(required = false) Integer id) {
        store.deleteDynamicParameterValue(id);
        return AbpResponse.ok(null);
    }

    private Integer parseInt(String value) {
        try {
            return value == null || value.isBlank() ? null : Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static class ParameterInput {
        public String id;
        public Integer dynamicParameterId;

        public Integer dynamicParameterId() {
            return dynamicParameterId != null ? dynamicParameterId : parseId(id);
        }

        private Integer parseId(String value) {
            try {
                return value == null || value.isBlank() ? null : Integer.parseInt(value);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
    }
}
