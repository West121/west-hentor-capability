package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.dto.IdRequest;
import com.sgs.capability.dto.ListResult;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.CapabilityStore;
import org.springframework.web.bind.annotation.*;

/** Mirrors CachingAppService maintenance routes. */
@RestController
@RequestMapping("/api/services/app/Caching")
@RequirePermission("Pages.Administration.Host.Maintenance")
public class CachingController {
    private final CapabilityStore store;

    public CachingController(CapabilityStore store) {
        this.store = store;
    }

    @GetMapping("/GetAllCaches")
    public AbpResponse<ListResult<CacheDto>> getAllCaches() {
        return AbpResponse.ok(new ListResult<>(store.caches().stream()
                .map(cache -> new CacheDto(cache.name))
                .toList()));
    }

    @PostMapping("/ClearCache")
    public AbpResponse<Void> clearCache(@RequestBody IdRequest input) {
        store.clearCache(input == null ? null : input.id);
        return AbpResponse.ok(null);
    }

    @PostMapping("/ClearAllCaches")
    public AbpResponse<Void> clearAllCaches() {
        store.clearAllCaches();
        return AbpResponse.ok(null);
    }

    public record CacheDto(String name) {
    }
}
