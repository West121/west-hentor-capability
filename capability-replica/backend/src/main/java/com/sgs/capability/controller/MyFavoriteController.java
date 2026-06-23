package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.dto.ListResult;
import com.sgs.capability.model.Ability;
import com.sgs.capability.model.FavoriteGroup;
import com.sgs.capability.security.AuthService;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.CapabilityStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Mirrors MyFavoriteAppService for user saved ability lists. */
@RestController
@RequestMapping("/api/services/app/MyFavorite")
@RequirePermission
public class MyFavoriteController {
    private static final String DUPLICATE_FAVORITE_NAME_ERROR = "名称已存在";
    private static final String DEFAULT_FAVORITE_NAME = "默认清单";

    private final CapabilityStore store;
    private final AuthService auth;

    public MyFavoriteController(CapabilityStore store, AuthService auth) {
        this.store = store;
        this.auth = auth;
    }

    @GetMapping("/GetMyFavoriteList")
    public AbpResponse<ListResult<MyFavoriteDto>> getMyFavoriteList(HttpServletRequest request) {
        long userId = currentUserId(request);
        List<MyFavoriteDto> groups = new ArrayList<>();
        groups.add(new MyFavoriteDto(null, DEFAULT_FAVORITE_NAME));
        groups.addAll(store.favoriteGroups(userId).stream().map(this::toDto).toList());
        return AbpResponse.ok(new ListResult<>(groups));
    }

    @PostMapping("/GetMyFavoriteAbilityList")
    public AbpResponse<ListResult<Ability>> getMyFavoriteAbilityList(@RequestBody(required = false) Map<String, Object> input,
                                                                     HttpServletRequest request) {
        return AbpResponse.ok(new ListResult<>(store.favoriteAbilities(value(input, "id", "myFavoriteId"), currentUserId(request))));
    }

    @GetMapping("/GetMyFavoriteAbilityList")
    public AbpResponse<ListResult<Ability>> getMyFavoriteAbilityListByQuery(
            @RequestParam(name = "MyFavoriteId", required = false) String myFavoriteId,
            HttpServletRequest request) {
        return AbpResponse.ok(new ListResult<>(store.favoriteAbilities(myFavoriteId, currentUserId(request))));
    }

    @PostMapping("/GetMyFavoriteForEdit")
    public AbpResponse<MyFavoriteDto> getForEdit(@RequestBody(required = false) Map<String, Object> input,
                                                 HttpServletRequest request) {
        return AbpResponse.ok(store.favorite(value(input, "id", "myFavoriteId"), currentUserId(request)).map(this::toDto).orElseGet(() -> new MyFavoriteDto(null, null)));
    }

    @GetMapping("/GetMyFavoriteForEdit")
    public AbpResponse<MyFavoriteDto> getForEditByQuery(@RequestParam(name = "Id", required = false) String id,
                                                        HttpServletRequest request) {
        return AbpResponse.ok(store.favorite(id, currentUserId(request)).map(this::toDto).orElseGet(() -> new MyFavoriteDto(null, null)));
    }

    @PostMapping("/SaveOrUpdateMyFavorite")
    public AbpResponse<Void> save(@RequestBody FavoriteGroup input, HttpServletRequest request) {
        long userId = currentUserId(request);
        // Original MyFavoriteAppService rejects duplicate list names per user.
        if (store.favoriteNameExists(input == null ? null : input.id, input == null ? null : input.name, userId)) {
            return AbpResponse.failed(DUPLICATE_FAVORITE_NAME_ERROR);
        }
        store.saveFavorite(input, userId);
        return AbpResponse.ok(null);
    }

    @PostMapping("/DeleteMyFavorite")
    public AbpResponse<Void> delete(@RequestBody Map<String, Object> input, HttpServletRequest request) {
        store.deleteFavorite(value(input, "id", "myFavoriteId"), currentUserId(request));
        return AbpResponse.ok(null);
    }

    @DeleteMapping("/DeleteMyFavorite")
    public AbpResponse<Void> deleteByQuery(@RequestParam(name = "Id", required = false) String id,
                                           HttpServletRequest request) {
        store.deleteFavorite(id, currentUserId(request));
        return AbpResponse.ok(null);
    }

    @PostMapping("/AddItem")
    public AbpResponse<Void> addItem(@RequestBody Map<String, Object> input, HttpServletRequest request) {
        store.addFavoriteItem(value(input, "myFavoriteId", "favoriteId"), value(input, "abilityId", "id"), currentUserId(request));
        return AbpResponse.ok(null);
    }

    @PostMapping("/RemoveItem")
    public AbpResponse<Void> removeItem(@RequestBody Map<String, Object> input, HttpServletRequest request) {
        store.removeFavoriteItem(value(input, "abilityId", "id"), currentUserId(request));
        return AbpResponse.ok(null);
    }

    @DeleteMapping("/RemoveItem")
    public AbpResponse<Void> removeItemByQuery(@RequestParam(name = "Id", required = false) String id,
                                               HttpServletRequest request) {
        store.removeFavoriteItem(id, currentUserId(request));
        return AbpResponse.ok(null);
    }

    private String value(Map<String, Object> input, String primary, String fallback) {
        if (input == null) {
            return null;
        }
        Object value = input.get(primary);
        if (value == null) {
            value = input.get(fallback);
        }
        return value == null ? null : String.valueOf(value);
    }

    private MyFavoriteDto toDto(FavoriteGroup group) {
        return new MyFavoriteDto(group.id, group.name);
    }

    private long currentUserId(HttpServletRequest request) {
        return auth.currentUser(request.getHeader("Authorization")).map(context -> context.user().id).orElse(1L);
    }

    public record MyFavoriteDto(java.util.UUID id, String name) {
    }
}
