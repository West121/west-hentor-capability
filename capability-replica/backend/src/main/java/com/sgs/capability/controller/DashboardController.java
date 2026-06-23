package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.dto.ListResult;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.CapabilityStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Mirrors DashboardAppService metrics used by the tenant workbench. */
@RestController
@RequestMapping("/api/services/app/Dashboard")
@RequirePermission
public class DashboardController {
    private final CapabilityStore store;

    public DashboardController(CapabilityStore store) {
        this.store = store;
    }

    @GetMapping("/Statistics")
    public AbpResponse<StatisticsOutput> statistics() {
        return AbpResponse.ok(new StatisticsOutput(
                store.abilityCount(),
                store.labCount(),
                store.orgCount(),
                store.weekChangeCount(),
                store.monthCreateCount(),
                store.weekDeleteCount()));
    }

    @PostMapping("/Statistics")
    public AbpResponse<StatisticsOutput> postStatistics() {
        return statistics();
    }

    @GetMapping("/OrgCount")
    public AbpResponse<ListResult<OrgCountOutput>> orgCount() {
        List<OrgCountOutput> rows = store.abilityCountByOrg().entrySet().stream()
                .map(item -> new OrgCountOutput(item.getKey(), item.getValue()))
                .toList();
        return AbpResponse.ok(new ListResult<>(rows));
    }

    @PostMapping("/OrgCount")
    public AbpResponse<ListResult<OrgCountOutput>> postOrgCount() {
        return orgCount();
    }

    @GetMapping("/ChangeCountInWeek")
    public AbpResponse<ListResult<NameValueOutput>> changeCountInWeek() {
        List<NameValueOutput> rows = store.abilityChangeCountInWeek().entrySet().stream()
                .map(item -> new NameValueOutput(item.getKey(), item.getValue()))
                .toList();
        return AbpResponse.ok(new ListResult<>(rows));
    }

    @PostMapping("/ChangeCountInWeek")
    public AbpResponse<ListResult<NameValueOutput>> postChangeCountInWeek() {
        return changeCountInWeek();
    }

    public record StatisticsOutput(long abilityCount, long laboratoryCount, long organizationCount,
                                   long changeCountInWeek, long changeCountInMonth, long deleteCountInWeek) {
    }

    public record OrgCountOutput(String orgName, long count) {
    }

    public record NameValueOutput(String name, long value) {
    }
}
