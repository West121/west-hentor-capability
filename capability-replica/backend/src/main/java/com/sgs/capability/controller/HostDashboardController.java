package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.model.SubscriptionPaymentItem;
import com.sgs.capability.model.TenantItem;
import com.sgs.capability.security.RequirePermission;
import com.sgs.capability.service.CapabilityStore;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

/** Mirrors HostDashboardAppService with local tenant and payment statistics. */
@RestController
@RequestMapping("/api/services/app/HostDashboard")
@RequirePermission("Pages.Administration.Host.Dashboard")
public class HostDashboardController {
    private static final int DASHBOARD_PLACEHOLDER_1 = 125;
    private static final int DASHBOARD_PLACEHOLDER_2 = 830;
    private static final DayOfWeek FIRST_DAY_OF_WEEK =
            WeekFields.of(Locale.SIMPLIFIED_CHINESE).getFirstDayOfWeek();

    private final CapabilityStore store;
    private final Clock clock;

    public HostDashboardController(CapabilityStore store, ObjectProvider<Clock> clockProvider) {
        this.store = store;
        this.clock = clockProvider.getIfAvailable(Clock::systemDefaultZone);
    }

    @PostMapping("/GetTopStatsData")
    public AbpResponse<TopStatsData> getTopStatsData(@RequestBody(required = false) DashboardInput input) {
        return AbpResponse.ok(topStatsData(input));
    }

    @GetMapping("/GetTopStatsData")
    public AbpResponse<TopStatsData> getTopStatsDataByQuery(
            @RequestParam(name = "StartDate", required = false) String startDate,
            @RequestParam(name = "EndDate", required = false) String endDate) {
        return AbpResponse.ok(topStatsData(dashboardInput(startDate, endDate, null)));
    }

    private TopStatsData topStatsData(DashboardInput input) {
        DateRange range = dateRange(input);
        long newTenants = tenants().stream().filter(tenant -> within(tenant.creationTime, range)).count();
        BigDecimal newSubscriptionAmount = completedPayments().stream()
                .filter(payment -> within(payment.creationTime, range))
                .map(payment -> payment.amount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new TopStatsData((int) newTenants, newSubscriptionAmount,
                DASHBOARD_PLACEHOLDER_1, DASHBOARD_PLACEHOLDER_2);
    }

    @GetMapping("/GetRecentTenantsData")
    public AbpResponse<GetRecentTenantsOutput> getRecentTenantsData() {
        LocalDateTime start = now().minusDays(7);
        List<RecentTenant> recentTenants = tenants().stream()
                .filter(tenant -> parseDateTime(tenant.creationTime).map(time -> !time.isBefore(start)).orElse(false))
                .sorted(Comparator.comparing((TenantItem tenant) -> safe(tenant.creationTime)).reversed())
                .limit(10)
                .map(tenant -> new RecentTenant(tenant.id, tenant.name, tenant.creationTime))
                .toList();
        return AbpResponse.ok(new GetRecentTenantsOutput(7, 10, start.toString(), recentTenants));
    }

    @GetMapping("/GetSubscriptionExpiringTenantsData")
    public AbpResponse<GetExpiringTenantsOutput> getSubscriptionExpiringTenantsData() {
        LocalDateTime start = now();
        LocalDateTime end = start.plusDays(30);
        List<ExpiringTenant> expiringTenants = tenants().stream()
                .filter(tenant -> parseDateTime(tenant.subscriptionEndDateUtc)
                        .map(time -> !time.isBefore(start) && !time.isAfter(end))
                        .orElse(false))
                .sorted(Comparator.comparing(tenant -> safe(tenant.subscriptionEndDateUtc)))
                .limit(10)
                .map(tenant -> new ExpiringTenant(tenant.name, remainingDays(tenant.subscriptionEndDateUtc, start)))
                .sorted(Comparator.comparingInt(ExpiringTenant::remainingDayCount)
                        .thenComparing(tenant -> safe(tenant.tenantName)))
                .toList();
        return AbpResponse.ok(new GetExpiringTenantsOutput(expiringTenants, 30, 10, start.toString(), end.toString()));
    }

    @PostMapping("/GetIncomeStatistics")
    public AbpResponse<GetIncomeStatisticsDataOutput> getIncomeStatistics(@RequestBody(required = false) DashboardInput input) {
        return AbpResponse.ok(incomeStatisticsData(input));
    }

    @GetMapping("/GetIncomeStatistics")
    public AbpResponse<GetIncomeStatisticsDataOutput> getIncomeStatisticsByQuery(
            @RequestParam(name = "IncomeStatisticsDateInterval", required = false) Integer incomeStatisticsDateInterval,
            @RequestParam(name = "StartDate", required = false) String startDate,
            @RequestParam(name = "EndDate", required = false) String endDate) {
        return AbpResponse.ok(incomeStatisticsData(dashboardInput(startDate, endDate, incomeStatisticsDateInterval)));
    }

    private GetIncomeStatisticsDataOutput incomeStatisticsData(DashboardInput input) {
        DateRange range = dateRange(input);
        int interval = input == null || input.incomeStatisticsDateInterval == null ? 1 : input.incomeStatisticsDateInterval;
        List<IncomeStatistic> rows = incomeStatistics(range, interval);
        return new GetIncomeStatisticsDataOutput(rows);
    }

    @PostMapping("/GetEditionTenantStatistics")
    public AbpResponse<GetEditionTenantStatisticsOutput> getEditionTenantStatistics(@RequestBody(required = false) DashboardInput input) {
        return AbpResponse.ok(editionTenantStatistics(input));
    }

    @GetMapping("/GetEditionTenantStatistics")
    public AbpResponse<GetEditionTenantStatisticsOutput> getEditionTenantStatisticsByQuery(
            @RequestParam(name = "StartDate", required = false) String startDate,
            @RequestParam(name = "EndDate", required = false) String endDate) {
        return AbpResponse.ok(editionTenantStatistics(dashboardInput(startDate, endDate, null)));
    }

    private GetEditionTenantStatisticsOutput editionTenantStatistics(DashboardInput input) {
        DateRange range = dateRange(input);
        Map<String, Long> grouped = tenants().stream()
                .filter(tenant -> within(tenant.creationTime, range))
                .filter(tenant -> tenant.editionId != null)
                .collect(Collectors.groupingBy(tenant -> tenant.editionDisplayName,
                        LinkedHashMap::new, Collectors.counting()));
        List<TenantEdition> rows = grouped.entrySet().stream()
                .map(entry -> new TenantEdition(entry.getKey(), entry.getValue().intValue()))
                .sorted(Comparator.comparing(item -> item.label))
                .toList();
        return new GetEditionTenantStatisticsOutput(rows);
    }

    private DashboardInput dashboardInput(String startDate, String endDate, Integer incomeStatisticsDateInterval) {
        DashboardInput input = new DashboardInput();
        input.startDate = startDate;
        input.endDate = endDate;
        input.incomeStatisticsDateInterval = incomeStatisticsDateInterval;
        return input;
    }

    private List<IncomeStatistic> incomeStatistics(DateRange range, int interval) {
        if (interval == 2) {
            return weeklyIncomeStatistics(range);
        }
        if (interval == 3) {
            return monthlyIncomeStatistics(range);
        }
        return bucketedIncomeStatistics(range, interval);
    }

    private List<IncomeStatistic> bucketedIncomeStatistics(DateRange range, int interval) {
        List<IncomeStatistic> rows = new ArrayList<>();
        LocalDate cursor = range.start.toLocalDate();
        LocalDate end = range.end.toLocalDate();
        while (!cursor.isAfter(end)) {
            LocalDate bucketStart = cursor;
            LocalDate bucketEnd = nextBucketEnd(bucketStart, interval, end);
            rows.add(incomeStatistic(bucketStart, bucketEnd));
            cursor = bucketEnd.plusDays(1);
        }
        return rows;
    }

    private List<IncomeStatistic> weeklyIncomeStatistics(DateRange range) {
        List<IncomeStatistic> rows = new ArrayList<>();
        LocalDate end = range.end.toLocalDate();
        LocalDate cursor = range.start.toLocalDate();
        LocalDate bucketStart = cursor;
        boolean isFirstWeek = bucketStart.getDayOfWeek() == FIRST_DAY_OF_WEEK;
        while (!cursor.isAfter(end)) {
            // 原系统遇到每周第一天才开启新周。
            if (cursor.getDayOfWeek() == FIRST_DAY_OF_WEEK) {
                if (!isFirstWeek) {
                    rows.add(incomeStatistic(bucketStart, cursor.minusDays(1)));
                }
                isFirstWeek = false;
                bucketStart = cursor;
            }
            cursor = cursor.plusDays(1);
        }
        rows.add(incomeStatistic(bucketStart, end));
        return rows;
    }

    private List<IncomeStatistic> monthlyIncomeStatistics(DateRange range) {
        List<IncomeStatistic> rows = new ArrayList<>();
        LocalDate cursor = range.start.toLocalDate();
        LocalDate end = range.end.toLocalDate();
        while (!cursor.isAfter(end)) {
            // 原系统按自然月汇总，首月从查询开始日算起。
            LocalDate bucketStart = cursor;
            LocalDate bucketEnd = cursor.withDayOfMonth(cursor.lengthOfMonth());
            if (bucketEnd.isAfter(end)) {
                bucketEnd = end;
            }
            rows.add(incomeStatistic(bucketStart, bucketEnd));
            cursor = bucketEnd.plusDays(1);
        }
        return rows;
    }

    private IncomeStatistic incomeStatistic(LocalDate bucketStart, LocalDate bucketEnd) {
        BigDecimal amount = completedPayments().stream()
                .filter(payment -> parseDateTime(payment.creationTime)
                        .map(time -> {
                            LocalDate day = time.toLocalDate();
                            return !day.isBefore(bucketStart) && !day.isAfter(bucketEnd);
                        })
                        .orElse(false))
                .map(payment -> payment.amount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new IncomeStatistic(bucketStart.toString(), bucketStart.atStartOfDay(), amount);
    }

    private LocalDate nextBucketEnd(LocalDate bucketStart, int interval, LocalDate maxEnd) {
        LocalDate end = switch (interval) {
            case 2 -> bucketStart.plusDays(6);
            default -> bucketStart;
        };
        return end.isAfter(maxEnd) ? maxEnd : end;
    }

    private DateRange dateRange(DashboardInput input) {
        LocalDateTime end = parseDateTime(input == null ? null : input.endDate).orElse(now());
        LocalDateTime start = parseDateTime(input == null ? null : input.startDate).orElse(end.minusDays(30));
        return start.isAfter(end) ? new DateRange(end, start) : new DateRange(start, end);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), clock.getZone());
    }

    private boolean within(String value, DateRange range) {
        return parseDateTime(value).map(time -> !time.isBefore(range.start) && !time.isAfter(range.end)).orElse(false);
    }

    private int remainingDays(String value, LocalDateTime start) {
        return parseDateTime(value)
                .map(time -> {
                    // 原系统用总天数四舍五入，而不是只比较日期。
                    double totalDays = Duration.between(start, time).toMillis()
                            / (double) Duration.ofDays(1).toMillis();
                    return Math.max(0, (int) Math.rint(totalDays));
                })
                .orElse(0);
    }

    private List<TenantItem> tenants() {
        return store.tenants(null, null, false, 0, Integer.MAX_VALUE).items;
    }

    private List<SubscriptionPaymentItem> completedPayments() {
        return store.subscriptionPayments().stream()
                .filter(payment -> payment.status == 2)
                .toList();
    }

    private Optional<LocalDateTime> parseDateTime(String value) {
        try {
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            if (value.length() == 10) {
                return Optional.of(LocalDate.parse(value).atStartOfDay());
            }
            try {
                return Optional.of(LocalDateTime.parse(value));
            } catch (RuntimeException ex) {
                return Optional.of(OffsetDateTime.parse(value).toLocalDateTime());
            }
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record DateRange(LocalDateTime start, LocalDateTime end) {
    }

    public static class DashboardInput {
        public String startDate;
        public String endDate;
        public Integer incomeStatisticsDateInterval;
    }

    public record TopStatsData(int newTenantsCount, BigDecimal newSubscriptionAmount,
                               int dashboardPlaceholder1, int dashboardPlaceholder2) {
    }

    public record RecentTenant(int id, String name, String creationTime) {
    }

    public record GetRecentTenantsOutput(int recentTenantsDayCount, int maxRecentTenantsShownCount,
                                         String tenantCreationStartDate, List<RecentTenant> recentTenants) {
    }

    public record ExpiringTenant(String tenantName, int remainingDayCount) {
    }

    public record GetExpiringTenantsOutput(List<ExpiringTenant> expiringTenants, int subscriptionEndAlertDayCount,
                                           int maxExpiringTenantsShownCount, String subscriptionEndDateStart,
                                           String subscriptionEndDateEnd) {
    }

    public record IncomeStatistic(String label, LocalDateTime date, BigDecimal amount) {
    }

    public record GetIncomeStatisticsDataOutput(List<IncomeStatistic> incomeStatistics) {
    }

    public record TenantEdition(String label, int value) {
    }

    public record GetEditionTenantStatisticsOutput(List<TenantEdition> editionStatistics) {
    }
}
