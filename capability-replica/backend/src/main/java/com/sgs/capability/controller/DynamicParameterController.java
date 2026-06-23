package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.dto.IdRequest;
import com.sgs.capability.dto.ListResult;
import com.sgs.capability.model.DynamicParameterItem;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.CapabilityStore;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Mirrors DynamicParameterAppService definition CRUD. */
@RestController
@RequestMapping("/api/services/app/DynamicParameter")
@RequirePermission("Pages.Administration.DynamicParameters")
public class DynamicParameterController {
    private final CapabilityStore store;

    public DynamicParameterController(CapabilityStore store) {
        this.store = store;
    }

    @GetMapping("/GetAll")
    public AbpResponse<ListResult<DynamicParameterItem>> getAll() {
        return AbpResponse.ok(new ListResult<>(store.dynamicParameters()));
    }

    @PostMapping("/GetAll")
    public AbpResponse<ListResult<DynamicParameterItem>> postGetAll() {
        return getAll();
    }

    @PostMapping("/Get")
    public AbpResponse<DynamicParameterItem> get(@RequestBody(required = false) IdRequest input) {
        return getById(parseInt(input == null ? null : input.id));
    }

    @GetMapping("/Get")
    public AbpResponse<DynamicParameterItem> getByQuery(@RequestParam(required = false) Integer id) {
        return getById(id);
    }

    private AbpResponse<DynamicParameterItem> getById(Integer id) {
        return AbpResponse.ok(store.dynamicParameter(id).orElse(null));
    }

    @GetMapping("/FindAllowedInputType")
    public AbpResponse<Map<String, Object>> findAllowedInputTypeGet(@RequestParam(required = false) String name) {
        return AbpResponse.ok(store.allowedDynamicInputType(name));
    }

    @PostMapping("/FindAllowedInputType")
    public AbpResponse<Map<String, Object>> findAllowedInputTypePost(@RequestParam(required = false) String name,
                                                                     @RequestBody(required = false) FindAllowedInputTypeInput input) {
        // Original proxy posts the input type name in the query string.
        String safeName = name == null || name.isBlank() ? input == null ? null : input.name : name;
        return findAllowedInputTypeGet(safeName);
    }

    @PostMapping("/Add")
    @RequirePermission("Pages.Administration.DynamicParameters.Create")
    public AbpResponse<Void> add(@RequestBody DynamicParameterItem input) {
        input.id = null;
        store.saveDynamicParameter(input);
        return AbpResponse.ok(null);
    }

    @PostMapping("/Update")
    @RequirePermission("Pages.Administration.DynamicParameters.Edit")
    public AbpResponse<Void> update(@RequestBody DynamicParameterItem input) {
        store.saveDynamicParameter(input);
        return AbpResponse.ok(null);
    }

    @PutMapping("/Update")
    @RequirePermission("Pages.Administration.DynamicParameters.Edit")
    public AbpResponse<Void> putUpdate(@RequestBody DynamicParameterItem input) {
        return update(input);
    }

    @PostMapping("/Delete")
    @RequirePermission("Pages.Administration.DynamicParameters.Delete")
    public AbpResponse<Void> delete(@RequestBody IdRequest input) {
        store.deleteDynamicParameter(parseInt(input == null ? null : input.id));
        return AbpResponse.ok(null);
    }

    @DeleteMapping("/Delete")
    @RequirePermission("Pages.Administration.DynamicParameters.Delete")
    public AbpResponse<Void> deleteByQuery(@RequestParam(required = false) Integer id) {
        store.deleteDynamicParameter(id);
        return AbpResponse.ok(null);
    }

    private Integer parseInt(String value) {
        try {
            return value == null || value.isBlank() ? null : Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static class FindAllowedInputTypeInput {
        public String name;
    }
}
