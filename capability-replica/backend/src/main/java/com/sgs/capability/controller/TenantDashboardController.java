package com.sgs.capability.controller;

import com.sgs.capability.dto.AbpResponse;
import com.sgs.capability.security.RequirePermission;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Mirrors TenantDashboardAppService random dashboard sample data. */
@RestController
@RequestMapping("/api/services/app/TenantDashboard")
@RequirePermission("Pages.Tenant.Dashboard")
public class TenantDashboardController {
    private static final String[] COUNTRY_NAMES = {
            "Argentina", "China", "France", "Italy", "Japan",
            "Netherlands", "Russia", "Spain", "Turkey", "United States"
    };

    @GetMapping("/GetMemberActivity")
    public AbpResponse<GetMemberActivityOutput> getMemberActivity() {
        List<MemberActivity> rows = List.of(
                memberActivity("Brain"),
                memberActivity("Jane"),
                memberActivity("Tim"),
                memberActivity("Kate"));
        return AbpResponse.ok(new GetMemberActivityOutput(rows));
    }

    @PostMapping("/GetDashboardData")
    public AbpResponse<GetDashboardDataOutput> getDashboardData(@RequestBody(required = false) DashboardInput input) {
        return AbpResponse.ok(dashboardData(input == null ? 1 : input.salesSummaryDatePeriod));
    }

    @GetMapping("/GetDashboardData")
    public AbpResponse<GetDashboardDataOutput> getDashboardDataByQuery(
            @RequestParam(name = "SalesSummaryDatePeriod", required = false, defaultValue = "1") Integer salesSummaryDatePeriod) {
        return AbpResponse.ok(dashboardData(salesSummaryDatePeriod));
    }

    private GetDashboardDataOutput dashboardData(Integer salesSummaryDatePeriod) {
        GetDashboardDataOutput output = new GetDashboardDataOutput();
        output.totalProfit = randomInt(5000, 9000);
        output.newFeedbacks = randomInt(1000, 5000);
        output.newOrders = randomInt(100, 900);
        output.newUsers = randomInt(50, 500);
        output.salesSummary = salesSummary(salesSummaryDatePeriod);
        output.expenses = randomInt(5000, 10000);
        output.growth = randomInt(5000, 10000);
        output.revenue = randomInt(1000, 9000);
        output.totalSales = randomInt(10000, 90000);
        output.transactionPercent = randomInt(10, 100);
        output.newVisitPercent = randomInt(10, 100);
        output.bouncePercent = randomInt(10, 100);
        output.dailySales = randomArray(30, 10, 50);
        output.profitShares = randomPercentageArray(3);
        return output;
    }

    @GetMapping("/GetTopStats")
    public AbpResponse<GetTopStatsOutput> getTopStats() {
        return AbpResponse.ok(new GetTopStatsOutput(randomInt(5000, 9000),
                randomInt(1000, 5000), randomInt(100, 900), randomInt(50, 500)));
    }

    @GetMapping("/GetProfitShare")
    public AbpResponse<GetProfitShareOutput> getProfitShare() {
        return AbpResponse.ok(new GetProfitShareOutput(randomPercentageArray(3)));
    }

    @GetMapping("/GetDailySales")
    public AbpResponse<GetDailySalesOutput> getDailySales() {
        return AbpResponse.ok(new GetDailySalesOutput(randomArray(30, 10, 50)));
    }

    @PostMapping("/GetSalesSummary")
    public AbpResponse<GetSalesSummaryOutput> getSalesSummary(@RequestBody(required = false) DashboardInput input) {
        return AbpResponse.ok(salesSummaryOutput(input == null ? 1 : input.salesSummaryDatePeriod));
    }

    @GetMapping("/GetSalesSummary")
    public AbpResponse<GetSalesSummaryOutput> getSalesSummaryByQuery(
            @RequestParam(name = "SalesSummaryDatePeriod", required = false, defaultValue = "1") Integer salesSummaryDatePeriod) {
        return AbpResponse.ok(salesSummaryOutput(salesSummaryDatePeriod));
    }

    private GetSalesSummaryOutput salesSummaryOutput(Integer salesSummaryDatePeriod) {
        List<SalesSummaryData> rows = salesSummary(salesSummaryDatePeriod);
        return new GetSalesSummaryOutput(randomInt(0, 3000), randomInt(0, 3000),
                randomInt(0, 3000), randomInt(0, 3000), rows);
    }

    @GetMapping("/GetRegionalStats")
    public AbpResponse<GetRegionalStatsOutput> getRegionalStats() {
        List<RegionalStatCountry> stats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            stats.add(new RegionalStatCountry(COUNTRY_NAMES[randomInt(0, COUNTRY_NAMES.length)],
                    BigDecimal.valueOf(randomInt(10000, 100000)),
                    randomChangeValues(),
                    BigDecimal.valueOf(randomInt(10, 100)),
                    BigDecimal.valueOf(randomInt(10000, 50000))));
        }
        return AbpResponse.ok(new GetRegionalStatsOutput(stats));
    }

    @GetMapping("/GetGeneralStats")
    public AbpResponse<GetGeneralStatsOutput> getGeneralStats() {
        return AbpResponse.ok(new GetGeneralStatsOutput(randomInt(10, 100),
                randomInt(10, 100), randomInt(10, 100)));
    }

    private List<SalesSummaryData> salesSummary(Integer period) {
        LocalDate today = LocalDate.now();
        if (period != null && period == 3) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");
            return List.of(
                    salesSummaryData(today.minusMonths(4).format(formatter)),
                    salesSummaryData(today.minusMonths(3).format(formatter)),
                    salesSummaryData(today.minusMonths(2).format(formatter)),
                    salesSummaryData(today.minusMonths(1).format(formatter))
            );
        }
        if (period != null && period == 2) {
            int previousYear = today.minusYears(1).getYear();
            return List.of(
                    salesSummaryData(previousYear + " W4"),
                    salesSummaryData(previousYear + " W3"),
                    salesSummaryData(previousYear + " W2"),
                    salesSummaryData(previousYear + " W1")
            );
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        return List.of(
                salesSummaryData(today.minusDays(5).format(formatter)),
                salesSummaryData(today.minusDays(4).format(formatter)),
                salesSummaryData(today.minusDays(3).format(formatter)),
                salesSummaryData(today.minusDays(2).format(formatter)),
                salesSummaryData(today.minusDays(1).format(formatter))
        );
    }

    private SalesSummaryData salesSummaryData(String period) {
        return new SalesSummaryData(period, randomInt(1000, 2000), randomInt(100, 999));
    }

    private MemberActivity memberActivity(String name) {
        return new MemberActivity(name, "$" + randomInt(100, 500),
                randomInt(10, 100), randomInt(10, 150), randomInt(10, 99) + "%");
    }

    private int[] randomArray(int size, int min, int max) {
        int[] values = new int[size];
        for (int index = 0; index < size; index++) {
            values[index] = randomInt(min, max);
        }
        return values;
    }

    private int[] randomPercentageArray(int size) {
        if (size == 1) {
            return new int[100];
        }
        int[] values = new int[size];
        int total = 0;
        for (int index = 0; index < size - 1; index++) {
            values[index] = randomInt(0, 100 - total);
            total += values[index];
        }
        values[size - 1] = 100 - total;
        return values;
    }

    private List<Integer> randomChangeValues() {
        List<Integer> values = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            values.add(randomInt(-20, 20));
        }
        return values;
    }

    private int randomInt(int min, int max) {
        if (max <= min) {
            return min;
        }
        return ThreadLocalRandom.current().nextInt(min, max);
    }

    public static class DashboardInput {
        public Integer salesSummaryDatePeriod = 1;
    }

    public record GetMemberActivityOutput(List<MemberActivity> memberActivities) {
    }

    public record MemberActivity(String name, String earnings, int cases, int closed, String rate) {
    }

    public static class GetDashboardDataOutput {
        public int totalProfit;
        public int newFeedbacks;
        public int newOrders;
        public int newUsers;
        public List<SalesSummaryData> salesSummary;
        public int totalSales;
        public int revenue;
        public int expenses;
        public int growth;
        public int transactionPercent;
        public int newVisitPercent;
        public int bouncePercent;
        public int[] dailySales;
        public int[] profitShares;
    }

    public record GetTopStatsOutput(int totalProfit, int newFeedbacks, int newOrders, int newUsers) {
    }

    public record GetProfitShareOutput(int[] profitShares) {
    }

    public record GetDailySalesOutput(int[] dailySales) {
    }

    public record GetSalesSummaryOutput(int totalSales, int revenue, int expenses, int growth,
                                        List<SalesSummaryData> salesSummary) {
    }

    public record SalesSummaryData(String period, int sales, int profit) {
    }

    public record GetRegionalStatsOutput(List<RegionalStatCountry> stats) {
    }

    public record RegionalStatCountry(String countryName, BigDecimal sales, List<Integer> change,
                                      BigDecimal averagePrice, BigDecimal totalPrice) {
    }

    public record GetGeneralStatsOutput(int transactionPercent, int newVisitPercent, int bouncePercent) {
    }
}
