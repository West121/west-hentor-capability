package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.dto.IdRequest;
import com.sgs.capability.dto.ListResult;
import com.sgs.capability.model.EntityDynamicParameterItem;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.CapabilityStore;
import org.springframework.web.bind.annotation.*;

/** Mirrors EntityDynamicParameterAppService mapping CRUD. */
@RestController
@RequestMapping("/api/services/app/EntityDynamicParameter")
@RequirePermission("Pages.Administration.EntityDynamicParameters")
public class EntityDynamicParameterController {
    private final CapabilityStore store;

    public EntityDynamicParameterController(CapabilityStore store) {
        this.store = store;
    }

    @GetMapping("/GetAll")
    public AbpResponse<ListResult<EntityDynamicParameterItem>> getAll() {
        return AbpResponse.ok(new ListResult<>(store.entityDynamicParameters(null)));
    }

    @PostMapping("/GetAll")
    public AbpResponse<ListResult<EntityDynamicParameterItem>> postGetAll(@RequestBody(required = false) EntityInput input) {
        return AbpResponse.ok(new ListResult<>(store.entityDynamicParameters(input == null ? null : input.entityFullName)));
    }

    @PostMapping("/Get")
    public AbpResponse<EntityDynamicParameterItem> get(@RequestBody(required = false) IdRequest input) {
        return getById(parseInt(input == null ? null : input.id));
    }

    @GetMapping("/Get")
    public AbpResponse<EntityDynamicParameterItem> getByQuery(@RequestParam(required = false) Integer id) {
        return getById(id);
    }

    private AbpResponse<EntityDynamicParameterItem> getById(Integer id) {
        return AbpResponse.ok(store.entityDynamicParameter(id).orElse(null));
    }

    @PostMapping("/GetAllParametersOfAnEntity")
    public AbpResponse<ListResult<EntityDynamicParameterItem>> getAllParametersOfAnEntity(
            @RequestBody(required = false) EntityInput input) {
        return getAllParametersOfAnEntityByName(input == null ? null : input.entityFullName);
    }

    @GetMapping("/GetAllParametersOfAnEntity")
    public AbpResponse<ListResult<EntityDynamicParameterItem>> getAllParametersOfAnEntityByQuery(
            @RequestParam(name = "EntityFullName", required = false) String entityFullName) {
        return getAllParametersOfAnEntityByName(entityFullName);
    }

    private AbpResponse<ListResult<EntityDynamicParameterItem>> getAllParametersOfAnEntityByName(String entityFullName) {
        return AbpResponse.ok(new ListResult<>(store.entityDynamicParameters(entityFullName)));
    }

    @PostMapping("/Add")
    @RequirePermission("Pages.Administration.EntityDynamicParameters.Create")
    public AbpResponse<Void> add(@RequestBody EntityDynamicParameterItem input) {
        input.id = null;
        store.saveEntityDynamicParameter(input);
        return AbpResponse.ok(null);
    }

    @PostMapping("/Update")
    @RequirePermission("Pages.Administration.EntityDynamicParameters.Edit")
    public AbpResponse<Void> update(@RequestBody EntityDynamicParameterItem input) {
        store.saveEntityDynamicParameter(input);
        return AbpResponse.ok(null);
    }

    @PutMapping("/Update")
    @RequirePermission("Pages.Administration.EntityDynamicParameters.Edit")
    public AbpResponse<Void> putUpdate(@RequestBody EntityDynamicParameterItem input) {
        return update(input);
    }

    @PostMapping("/Delete")
    @RequirePermission("Pages.Administration.EntityDynamicParameters.Delete")
    public AbpResponse<Void> delete(@RequestBody IdRequest input) {
        store.deleteEntityDynamicParameter(parseInt(input == null ? null : input.id));
        return AbpResponse.ok(null);
    }

    @DeleteMapping("/Delete")
    @RequirePermission("Pages.Administration.EntityDynamicParameters.Delete")
    public AbpResponse<Void> deleteByQuery(@RequestParam(required = false) Integer id) {
        store.deleteEntityDynamicParameter(id);
        return AbpResponse.ok(null);
    }

    private Integer parseInt(String value) {
        try {
            return value == null || value.isBlank() ? null : Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static class EntityInput {
        public String entityFullName;
    }
}
