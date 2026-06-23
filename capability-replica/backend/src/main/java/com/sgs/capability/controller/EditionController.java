package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.dto.IdRequest;
import com.sgs.capability.dto.ListResult;
import com.sgs.capability.model.EditionItem;
import com.sgs.capability.model.FeatureItem;
import com.sgs.capability.model.NameValueItem;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.CapabilityStore;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Mirrors EditionAppService management routes. */
@RestController
@RequestMapping("/api/services/app/Edition")
@RequirePermission("Pages.Editions")
public class EditionController {
    private static final String EDITION_IN_USE_ERROR = "There are tenants subscribed to this edition. Please assign a different edition to them and then delete this edition.";
    private static final String EXPIRING_EDITION_NOT_FREE_ERROR = "Expiring edition must be a free edition";

    private final CapabilityStore store;

    public EditionController(CapabilityStore store) {
        this.store = store;
    }

    @GetMapping("/GetEditions")
    public AbpResponse<ListResult<EditionItem>> getEditions() {
        return AbpResponse.ok(new ListResult<>(store.editions()));
    }

    @PostMapping("/GetEditions")
    public AbpResponse<ListResult<EditionItem>> postEditions() {
        return getEditions();
    }

    @PostMapping("/GetEditionForEdit")
    public AbpResponse<GetEditionEditOutput> getEditionForEdit(@RequestBody(required = false) IdRequest input) {
        EditionItem edition = store.edition(parseInt(input == null ? null : input.id)).orElse(new EditionItem());
        return AbpResponse.ok(new GetEditionEditOutput(edition, store.tenantFeatureValues(null), store.features()));
    }

    @GetMapping("/GetEditionForEdit")
    public AbpResponse<GetEditionEditOutput> getEditionForEditByQuery(@RequestParam(name = "Id", required = false) String id) {
        IdRequest request = new IdRequest();
        request.id = id;
        return getEditionForEdit(request);
    }

    @PostMapping("/CreateEdition")
    public AbpResponse<Void> createEdition(@RequestBody EditionEditInput input) {
        String validationError = validateEditionWriteInput(input);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        EditionItem edition = input.edition;
        // Original CreateEditionAsync only allows a free expiring edition.
        if (usesPaidExpiringEdition(edition)) {
            return AbpResponse.failed(EXPIRING_EDITION_NOT_FREE_ERROR);
        }
        return saveEdition(input, edition);
    }

    @PostMapping("/UpdateEdition")
    public AbpResponse<Void> updateEdition(@RequestBody EditionEditInput input) {
        String validationError = validateEditionWriteInput(input);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        return saveEdition(input, input.edition);
    }

    @PutMapping("/UpdateEdition")
    public AbpResponse<Void> putUpdateEdition(@RequestBody EditionEditInput input) {
        return updateEdition(input);
    }

    @PostMapping("/DeleteEdition")
    public AbpResponse<Void> deleteEdition(@RequestBody IdRequest input) {
        Integer id = parseInt(input == null ? null : input.id);
        if (store.tenantCount(id) > 0) {
            return AbpResponse.failed(EDITION_IN_USE_ERROR);
        }
        store.deleteEdition(id);
        return AbpResponse.ok(null);
    }

    @DeleteMapping("/DeleteEdition")
    public AbpResponse<Void> deleteEditionByQuery(@RequestParam(name = "Id", required = false) String id) {
        IdRequest request = new IdRequest();
        request.id = id;
        return deleteEdition(request);
    }

    @PostMapping("/MoveTenantsToAnotherEdition")
    public AbpResponse<Void> moveTenantsToAnotherEdition(@RequestBody MoveTenantsInput input) {
        String validationError = validateMoveTenantsInput(input);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        store.moveTenantsToAnotherEdition(input.sourceEditionId, input.targetEditionId);
        return AbpResponse.ok(null);
    }

    @GetMapping("/GetEditionComboboxItems")
    public AbpResponse<List<EditionComboboxItem>> getEditionComboboxItems(@RequestParam(required = false) Integer selectedEditionId,
                                                                          @RequestParam(defaultValue = "false") boolean addAllItem,
                                                                          @RequestParam(name = "onlyFreeItems", required = false) Boolean onlyFreeItems,
                                                                          @RequestParam(name = "onlyFree", required = false) Boolean onlyFree) {
        boolean onlyFreeFilter = Boolean.TRUE.equals(onlyFreeItems) || Boolean.TRUE.equals(onlyFree);
        List<EditionComboboxItem> items = new ArrayList<>(store.editions().stream()
                // Original generated proxy calls the filter flag onlyFreeItems.
                .filter(item -> !onlyFreeFilter || item.isFree)
                .sorted(Comparator.comparing(item -> amount(item.monthlyPrice)))
                .map(item -> new EditionComboboxItem(String.valueOf(item.id), item.displayName, item.isFree,
                        selectedEditionId != null && selectedEditionId.equals(item.id)))
                .toList());
        items.add(0, new EditionComboboxItem("", "Not assigned", null, selectedEditionId == null));
        if (addAllItem) {
            // Original EditionAppService inserts an all item before the not-assigned item.
            items.add(0, new EditionComboboxItem("-1", "全部", null, selectedEditionId == null));
            items.get(1).isSelected = false;
        }
        return AbpResponse.ok(items);
    }

    @PostMapping("/GetTenantCount")
    public AbpResponse<Integer> getTenantCount(@RequestBody IdRequest input) {
        return AbpResponse.ok(store.tenantCount(parseInt(input == null ? null : input.id)));
    }

    @GetMapping("/GetTenantCount")
    public AbpResponse<Integer> getTenantCountByQuery(@RequestParam(name = "editionId", required = false) String editionId,
                                                      @RequestParam(name = "Id", required = false) String id) {
        return AbpResponse.ok(store.tenantCount(parseInt(firstText(editionId, id))));
    }

    private Integer parseInt(String value) {
        try {
            return value == null || value.isBlank() ? null : Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String firstText(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }

    private BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private boolean usesPaidExpiringEdition(EditionItem edition) {
        return edition.expiringEditionId != null
                && store.edition(edition.expiringEditionId).map(item -> !item.isFree).orElse(false);
    }

    private String validateEditionWriteInput(EditionEditInput input) {
        if (input == null || input.edition == null || input.featureValues == null) {
            return "Validation failed";
        }
        if (safe(input.edition.displayName).isBlank()) {
            // 原 CreateEditionDto/UpdateEditionDto 要求 Edition、FeatureValues 和 DisplayName。
            return "Validation failed";
        }
        return null;
    }

    private String validateMoveTenantsInput(MoveTenantsInput input) {
        if (input == null || input.sourceEditionId == null || input.targetEditionId == null
                || input.sourceEditionId < 1 || input.targetEditionId < 1) {
            // 原 MoveTenantsToAnotherEditionDto 对两个 EditionId 都要求 Range(1, int.MaxValue)。
            return "Validation failed";
        }
        return null;
    }

    private AbpResponse<Void> saveEdition(EditionEditInput input, EditionItem edition) {
        store.saveEdition(edition, input.featureValues);
        return AbpResponse.ok(null);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public static class EditionEditInput {
        public EditionItem edition;
        public List<NameValueItem> featureValues;
    }

    public static class MoveTenantsInput {
        public Integer sourceEditionId;
        public Integer targetEditionId;
    }

    public record GetEditionEditOutput(EditionItem edition, List<NameValueItem> featureValues, List<FeatureItem> features) {
    }

    public static class EditionComboboxItem {
        public String value;
        public String displayText;
        public Boolean isFree;
        public boolean isSelected;

        public EditionComboboxItem(String value, String displayText, Boolean isFree, boolean isSelected) {
            this.value = value;
            this.displayText = displayText;
            this.isFree = isFree;
            this.isSelected = isSelected;
        }
    }
}
