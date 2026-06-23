package com.sgs.capability.controller;

import com.sgs.capability.dto.*;
import com.sgs.capability.model.NameValueItem;
import com.sgs.capability.model.SubscribableEditionComboboxItem;
import com.sgs.capability.model.UserItem;
import com.sgs.capability.security.AuthContext;
import com.sgs.capability.security.AuthService;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.CapabilityStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Mirrors CommonLookupAppService shared lookup routes. */
@RestController
@RequestMapping("/api/services/app/CommonLookup")
@RequirePermission
public class CommonLookupController {
    private final CapabilityStore store;
    private final AuthService auth;

    public CommonLookupController(CapabilityStore store, AuthService auth) {
        this.store = store;
        this.auth = auth;
    }

    @GetMapping("/GetEditionsForCombobox")
    public AbpResponse<ListResult<SubscribableEditionComboboxItem>> getEditionsForCombobox(
            @RequestParam(defaultValue = "false") boolean onlyFreeItems) {
        return AbpResponse.ok(new ListResult<>(editionCombobox(onlyFreeItems)));
    }

    @PostMapping("/GetEditionsForCombobox")
    public AbpResponse<ListResult<SubscribableEditionComboboxItem>> postEditionsForCombobox(
            @RequestBody(required = false) Map<String, Object> input) {
        return AbpResponse.ok(new ListResult<>(editionCombobox(booleanValue(input, "onlyFreeItems"))));
    }

    @PostMapping("/FindUsers")
    public AbpResponse<PageResult<NameValueItem>> findUsers(@RequestBody(required = false) FindUsersInput input,
                                                            HttpServletRequest request) {
        FindUsersInput safeInput = input == null ? new FindUsersInput() : input;
        String validationError = validatePagedInput(safeInput.skipCount, safeInput.maxResultCount);
        if (validationError != null) {
            return AbpResponse.failed(validationError);
        }
        AuthContext context = auth.currentUser(request.getHeader("Authorization")).orElse(null);
        List<UserItem> filtered = store.users(null, 0, Integer.MAX_VALUE).items.stream()
                .filter(user -> matchesOriginalFindUsersFilter(user, safeInput.filter))
                .filter(user -> !safeInput.excludeCurrentUser || context == null || !java.util.Objects.equals(user.id, context.user().id))
                .sorted(Comparator.comparing((UserItem user) -> safe(user.name)).thenComparing(user -> safe(user.surname)))
                .toList();
        int skip = Math.max(safeInput.skipCount, 0);
        int take = safeInput.maxResultCount <= 0 ? 10 : safeInput.maxResultCount;
        List<NameValueItem> page = filtered.stream().skip(skip).limit(take).map(this::userNameValue).toList();
        return AbpResponse.ok(new PageResult<>(filtered.size(), page));
    }

    @RequestMapping(value = "/GetDefaultEditionName", method = {RequestMethod.GET, RequestMethod.POST})
    public AbpResponse<DefaultEditionNameOutput> getDefaultEditionName() {
        return AbpResponse.ok(new DefaultEditionNameOutput("Standard"));
    }

    private List<SubscribableEditionComboboxItem> editionCombobox(boolean onlyFreeItems) {
        return store.editions().stream()
                .filter(edition -> !onlyFreeItems || edition.isFree)
                .sorted(Comparator.comparing(edition -> edition.monthlyPrice == null ? BigDecimal.ZERO : edition.monthlyPrice))
                .map(edition -> new SubscribableEditionComboboxItem(String.valueOf(edition.id), edition.displayName, edition.isFree))
                .toList();
    }

    private NameValueItem userNameValue(UserItem user) {
        NameValueItem item = new NameValueItem();
        item.name = "%s %s (%s)".formatted(safe(user.name), safe(user.surname), safe(user.emailAddress)).trim();
        item.value = String.valueOf(user.id);
        return item;
    }

    private boolean matchesOriginalFindUsersFilter(UserItem user, String filter) {
        String needle = safe(filter).toLowerCase();
        if (needle.isBlank()) {
            return true;
        }
        return safe(user.userName).toLowerCase().contains(needle)
                || safe(user.name).toLowerCase().contains(needle)
                || safe(user.surname).toLowerCase().contains(needle)
                || safe(user.emailAddress).toLowerCase().contains(needle);
    }

    private String validatePagedInput(int skipCount, int maxResultCount) {
        if (skipCount < 0 || maxResultCount < 1 || maxResultCount > 1000) {
            // 原 PagedAndFilteredInputDto 要求 MaxResultCount 为 1-1000，SkipCount 不能为负。
            return "Validation failed";
        }
        return null;
    }

    private boolean booleanValue(Map<String, Object> input, String key) {
        Object value = input == null ? null : input.get(key);
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public record DefaultEditionNameOutput(String name) {
    }
}
