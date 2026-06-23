package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.dto.FindAbilityRequest;
import com.sgs.capability.dto.IdRequest;
import com.sgs.capability.dto.PageResult;
import com.sgs.capability.model.Ability;
import com.sgs.capability.model.AbilityHistoryItem;
import com.sgs.capability.model.Laboratory;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.CapabilityStore;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Mirrors public ability query and history routes. */
@RestController
@RequestMapping("/api/services/app/AbilityQuery")
@RequirePermission("Pages.AbilityQuery")
public class AbilityQueryController {
    private final CapabilityStore store;

    public AbilityQueryController(CapabilityStore store) {
        this.store = store;
    }

    @PostMapping("/FindAblibities")
    public AbpResponse<AbilityPageResult> find(@RequestBody(required = false) FindAbilityRequest input) {
        FindAbilityRequest safeInput = input == null ? new FindAbilityRequest() : input;
        String validationError = safeInput.validateOriginalPaging();
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        PageResult<Ability> page = store.findQueryAbilities(safeInput);
        return AbpResponse.ok(new AbilityPageResult(page.totalCount, page.items, store.labs()));
    }

    @PostMapping("/FindHistory")
    public AbpResponse<List<AbilityHistoryItem>> history(@RequestBody(required = false) IdRequest input) {
        return AbpResponse.ok(store.abilityHistoryForAbility(input == null ? null : input.id));
    }

    /** Original AbilityPageDto includes the query rows and the lab dropdown source. */
    public static class AbilityPageResult extends PageResult<Ability> {
        public List<Laboratory> labs;

        public AbilityPageResult(long totalCount, List<Ability> items, List<Laboratory> labs) {
            super(totalCount, items);
            this.labs = labs;
        }
    }
}
