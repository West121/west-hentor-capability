package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.dto.IdRequest;
import com.sgs.capability.dto.ListResult;
import com.sgs.capability.model.DynamicParameterItem;
import com.sgs.capability.model.DynamicParameterValueItem;
import com.sgs.capability.model.EntityDynamicParameterItem;
import com.sgs.capability.model.EntityDynamicParameterValueItem;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.CapabilityStore;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Mirrors EntityDynamicParameterValueAppService value CRUD. */
@RestController
@RequestMapping("/api/services/app/EntityDynamicParameterValue")
@RequirePermission("Pages.Administration.EntityDynamicParameterValue")
public class EntityDynamicParameterValueController {
    private final CapabilityStore store;

    public EntityDynamicParameterValueController(CapabilityStore store) {
        this.store = store;
    }

    @GetMapping("/GetAll")
    public AbpResponse<ListResult<EntityDynamicParameterValueItem>> getAll(
            @RequestParam(name = "EntityId", required = false) String entityId,
            @RequestParam(name = "ParameterId", required = false) Integer parameterId) {
        return AbpResponse.ok(new ListResult<>(store.entityDynamicParameterValues(parameterId, entityId)));
    }

    @PostMapping("/GetAll")
    public AbpResponse<ListResult<EntityDynamicParameterValueItem>> postGetAll(@RequestBody(required = false) ValueInput input) {
        return AbpResponse.ok(new ListResult<>(store.entityDynamicParameterValues(
                input == null ? null : input.entityDynamicParameterId,
                input == null ? null : input.entityId)));
    }

    @PostMapping("/Get")
    public AbpResponse<EntityDynamicParameterValueItem> get(@RequestBody(required = false) IdRequest input) {
        return getById(parseInt(input == null ? null : input.id));
    }

    @GetMapping("/Get")
    public AbpResponse<EntityDynamicParameterValueItem> getByQuery(@RequestParam(required = false) Integer id) {
        return getById(id);
    }

    private AbpResponse<EntityDynamicParameterValueItem> getById(Integer id) {
        return AbpResponse.ok(store.entityDynamicParameterValue(id).orElse(null));
    }

    @PostMapping("/GetAllEntityDynamicParameterValues")
    public AbpResponse<EntityDynamicParameterValuesOutput> getAllEntityDynamicParameterValues(
            @RequestBody(required = false) ValueInput input) {
        if (input == null || !hasText(input.entityFullName) || !hasText(input.entityId)) {
            // 原 GetAllEntityDynamicParameterValuesInput 要求 EntityFullName 和 EntityId 必填。
            return AbpResponse.failed("Validation failed");
        }
        return getAllEntityDynamicParameterValuesByName(input.entityFullName, input.entityId);
    }

    @GetMapping("/GetAllEntityDynamicParameterValues")
    public AbpResponse<EntityDynamicParameterValuesOutput> getAllEntityDynamicParameterValuesByQuery(
            @RequestParam(name = "EntityFullName", required = false) String entityFullName,
            @RequestParam(name = "EntityId", required = false) String entityId) {
        return getAllEntityDynamicParameterValuesByName(entityFullName, entityId);
    }

    private AbpResponse<EntityDynamicParameterValuesOutput> getAllEntityDynamicParameterValuesByName(
            String entityFullName,
            String entityId) {
        if (!hasText(entityFullName) || !hasText(entityId)) {
            // 原 GetAllEntityDynamicParameterValuesInput 要求 EntityFullName 和 EntityId 必填。
            return AbpResponse.failed("Validation failed");
        }
        List<EntityDynamicParameterValuesOutputItem> items = store.entityDynamicParameters(entityFullName).stream()
                .map(mapping -> entityDynamicParameterValueOutputItem(mapping, entityId))
                .toList();
        return AbpResponse.ok(new EntityDynamicParameterValuesOutput(items));
    }

    private EntityDynamicParameterValuesOutputItem entityDynamicParameterValueOutputItem(
            EntityDynamicParameterItem mapping,
            String entityId) {
        DynamicParameterItem parameter = store.dynamicParameter(mapping.dynamicParameterId).orElse(null);
        String inputTypeName = parameter == null ? null : parameter.inputType;
        List<String> selectedValues = store.entityDynamicParameterValues(mapping.id, entityId).stream()
                .map(item -> item.value)
                .toList();
        List<String> allValues = store.dynamicParameterValues(mapping.dynamicParameterId).stream()
                .map(item -> item.value)
                .toList();
        return new EntityDynamicParameterValuesOutputItem(mapping.id, mapping.parameterName,
                store.allowedDynamicInputType(inputTypeName), selectedValues, allValues);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @PostMapping("/Add")
    @RequirePermission("Pages.Administration.EntityDynamicParameterValue.Create")
    public AbpResponse<Void> add(@RequestBody EntityDynamicParameterValueItem input) {
        input.id = null;
        store.saveEntityDynamicParameterValue(input);
        return AbpResponse.ok(null);
    }

    @PostMapping("/Update")
    @RequirePermission("Pages.Administration.EntityDynamicParameterValue.Edit")
    public AbpResponse<Void> update(@RequestBody EntityDynamicParameterValueItem input) {
        store.saveEntityDynamicParameterValue(input);
        return AbpResponse.ok(null);
    }

    @PutMapping("/Update")
    @RequirePermission("Pages.Administration.EntityDynamicParameterValue.Edit")
    public AbpResponse<Void> putUpdate(@RequestBody EntityDynamicParameterValueItem input) {
        return update(input);
    }

    @PostMapping("/Delete")
    @RequirePermission("Pages.Administration.EntityDynamicParameterValue.Delete")
    public AbpResponse<Void> delete(@RequestBody IdRequest input) {
        store.deleteEntityDynamicParameterValue(parseInt(input == null ? null : input.id));
        return AbpResponse.ok(null);
    }

    @DeleteMapping("/Delete")
    @RequirePermission("Pages.Administration.EntityDynamicParameterValue.Delete")
    public AbpResponse<Void> deleteByQuery(@RequestParam(required = false) Integer id) {
        store.deleteEntityDynamicParameterValue(id);
        return AbpResponse.ok(null);
    }

    @PostMapping("/InsertOrUpdateAllValues")
    @RequirePermission("Pages.Administration.EntityDynamicParameterValue.Edit")
    public AbpResponse<Void> insertOrUpdateAllValues(@RequestBody(required = false) BulkValueInput input) {
        List<InsertOrUpdateAllValuesInputItem> items = input == null || input.items() == null ? List.of() : input.items();
        for (InsertOrUpdateAllValuesInputItem item : items) {
            if (item != null) {
                store.replaceEntityDynamicParameterValues(item.entityDynamicParameterId, item.entityId, item.values);
            }
        }
        return AbpResponse.ok(null);
    }

    @PostMapping("/CleanValues")
    @RequirePermission("Pages.Administration.EntityDynamicParameterValue.Delete")
    public AbpResponse<Void> cleanValues(@RequestBody(required = false) ValueInput input) {
        store.cleanEntityDynamicParameterValues(input == null ? null : input.entityDynamicParameterId,
                input == null ? null : input.entityId);
        return AbpResponse.ok(null);
    }

    private Integer parseInt(String value) {
        try {
            return value == null || value.isBlank() ? null : Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static class ValueInput {
        public Integer entityDynamicParameterId;
        public String entityFullName;
        public String entityId;
    }

    public static class BulkValueInput {
        public List<InsertOrUpdateAllValuesInputItem> items;
        public List<InsertOrUpdateAllValuesInputItem> values;

        public List<InsertOrUpdateAllValuesInputItem> items() {
            return items != null ? items : values;
        }
    }

    public static class InsertOrUpdateAllValuesInputItem {
        public String entityId;
        public Integer entityDynamicParameterId;
        public List<String> values = new ArrayList<>();
    }

    public record EntityDynamicParameterValuesOutput(List<EntityDynamicParameterValuesOutputItem> items) {
    }

    public record EntityDynamicParameterValuesOutputItem(Integer entityDynamicParameterId,
                                                         String parameterName,
                                                         Map<String, Object> inputType,
                                                         List<String> selectedValues,
                                                         List<String> allValuesInputTypeHas) {
    }
}
