package com.sgs.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.model.SubscriptionPaymentItem;
import com.sgs.capability.model.TenantItem;
import com.sgs.capability.security.AuthService;
import com.sgs.capability.service.CapabilityStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "replica.store.path=target/test-data/dashboard-get-route-parity-store.json")
@AutoConfigureMockMvc
class DashboardGetRouteParityTest {
    private static final LocalDateTime FIXED_HOST_DASHBOARD_NOW = LocalDateTime.of(2026, 6, 15, 8, 0);

    static {
        try {
            Files.deleteIfExists(Path.of("target/test-data/dashboard-get-route-parity-store.json"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to reset dashboard GET route parity test store", ex);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthService authService;

    @Autowired
    CapabilityStore store;

    @Autowired
    ObjectMapper objectMapper;

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        Clock hostDashboardClock() {
            return Clock.fixed(FIXED_HOST_DASHBOARD_NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        }
    }

    @Test
    void hostDashboardReadRoutesAcceptOriginalGetQueryParameters() throws Exception {
        JsonNode topStats = getAbp(get("/api/services/app/HostDashboard/GetTopStatsData")
                .param("StartDate", "2099-01-01T00:00:00.000Z")
                .param("EndDate", "2099-01-02T00:00:00.000Z")).path("result");
        assertThat(topStats.path("newTenantsCount").asInt()).isZero();
        assertThat(topStats.path("newSubscriptionAmount").asText()).isEqualTo("0");
        assertThat(topStats.path("dashboardPlaceholder1").asInt()).isEqualTo(125);
        assertThat(topStats.path("dashboardPlaceholder2").asInt()).isEqualTo(830);

        JsonNode income = getAbp(get("/api/services/app/HostDashboard/GetIncomeStatistics")
                .param("IncomeStatisticsDateInterval", "2")
                .param("StartDate", "2099-01-01T00:00:00.000Z")
                .param("EndDate", "2099-01-14T00:00:00.000Z")).path("result");
        assertThat(income.path("incomeStatistics")).hasSize(3);
        assertThat(income.path("incomeStatistics").get(0).path("date").asText()).startsWith("2099-01-01T00:00:00");
        assertThat(income.path("incomeStatistics").get(1).path("date").asText()).startsWith("2099-01-05T00:00:00");
        assertThat(income.path("incomeStatistics").get(2).path("date").asText()).startsWith("2099-01-12T00:00:00");
        assertThat(income.path("incomeStatistics").get(0).path("label").asText()).isEqualTo("2099-01-01");
        assertThat(income.path("incomeStatistics").get(1).path("label").asText()).isEqualTo("2099-01-05");
        assertThat(income.path("incomeStatistics").get(2).path("label").asText()).isEqualTo("2099-01-12");

        JsonNode editions = getAbp(get("/api/services/app/HostDashboard/GetEditionTenantStatistics")
                .param("StartDate", "2099-01-01T00:00:00.000Z")
                .param("EndDate", "2099-01-02T00:00:00.000Z")).path("result");
        assertThat(editions.path("editionStatistics")).isEmpty();
    }

    @Test
    void hostIncomeMonthlyStatisticsUseOriginalCalendarMonthDates() throws Exception {
        JsonNode incomeStatistics = getAbp(get("/api/services/app/HostDashboard/GetIncomeStatistics")
                .param("IncomeStatisticsDateInterval", "3")
                .param("StartDate", "2099-01-15T00:00:00.000Z")
                .param("EndDate", "2099-03-02T00:00:00.000Z"))
                .path("result")
                .path("incomeStatistics");

        assertThat(incomeStatistics).hasSize(3);
        assertThat(incomeStatistics.get(0).path("date").asText()).startsWith("2099-01-15T00:00:00");
        assertThat(incomeStatistics.get(1).path("date").asText()).startsWith("2099-02-01T00:00:00");
        assertThat(incomeStatistics.get(2).path("date").asText()).startsWith("2099-03-01T00:00:00");
        assertThat(incomeStatistics.get(0).path("label").asText()).isEqualTo("2099-01-15");
        assertThat(incomeStatistics.get(1).path("label").asText()).isEqualTo("2099-02-01");
        assertThat(incomeStatistics.get(2).path("label").asText()).isEqualTo("2099-03-01");
    }

    @Test
    void hostIncomeStatisticsOnlyCountsOriginalCompletedPaymentStatus() throws Exception {
        SubscriptionPaymentItem completed = store.createPayment(2, 1, 30, 2, true, "ok", "error");
        store.markPaymentStatus(completed.id, 2);
        completed.amount = new BigDecimal("120.50");
        completed.creationTime = "2099-04-10T12:00:00";

        SubscriptionPaymentItem locallyCompleted = store.createPayment(2, 1, 30, 2, true, "ok", "error");
        store.markPaymentStatus(locallyCompleted.id, 5);
        locallyCompleted.amount = new BigDecimal("300.75");
        locallyCompleted.creationTime = "2099-04-10T13:00:00";

        JsonNode topStats = getAbp(get("/api/services/app/HostDashboard/GetTopStatsData")
                .param("StartDate", "2099-04-10T00:00:00.000Z")
                .param("EndDate", "2099-04-10T23:59:59.000Z")).path("result");
        assertThat(topStats.path("newSubscriptionAmount").decimalValue()).isEqualByComparingTo("120.50");

        JsonNode incomeStatistics = getAbp(get("/api/services/app/HostDashboard/GetIncomeStatistics")
                .param("IncomeStatisticsDateInterval", "1")
                .param("StartDate", "2099-04-10T00:00:00.000Z")
                .param("EndDate", "2099-04-10T23:59:59.000Z"))
                .path("result")
                .path("incomeStatistics");
        assertThat(incomeStatistics).hasSize(1);
        assertThat(incomeStatistics.get(0).path("amount").decimalValue()).isEqualByComparingTo("120.50");
    }

    @Test
    void hostExpiringTenantsUseOriginalFinalTenantNameOrdering() throws Exception {
        String expiringAt = LocalDateTime.now().plusDays(2).withNano(0).toString();
        TenantItem zTenancyAlphaName = tenant("zz-host-expiring-" + System.nanoTime(), "Alpha Expiring", expiringAt);
        TenantItem aTenancyZuluName = tenant("aa-host-expiring-" + System.nanoTime(), "Zulu Expiring", expiringAt);

        JsonNode expiringTenants = getAbp(get("/api/services/app/HostDashboard/GetSubscriptionExpiringTenantsData"))
                .path("result")
                .path("expiringTenants");

        assertThat(expiringTenantIndex(expiringTenants, zTenancyAlphaName.name))
                .isLessThan(expiringTenantIndex(expiringTenants, aTenancyZuluName.name));
    }

    @Test
    void hostExpiringTenantsRoundRemainingDaysFromOriginalStartInstant() throws Exception {
        LocalDateTime expiringAt = expiringEndWithRoundingMismatch(FIXED_HOST_DASHBOARD_NOW);
        TenantItem tenant = tenant("rounding-host-expiring-" + System.nanoTime(),
                "Rounding Expiring " + System.nanoTime(), expiringAt.toString());

        JsonNode result = getAbp(get("/api/services/app/HostDashboard/GetSubscriptionExpiringTenantsData"))
                .path("result");
        JsonNode row = expiringTenantByName(result.path("expiringTenants"), tenant.name);
        int expectedRemainingDays = originalRoundedRemainingDays(
                result.path("subscriptionEndDateStart").asText(), expiringAt);

        assertThat(row.path("remainingDayCount").asInt()).isEqualTo(expectedRemainingDays);
    }

    @Test
    void hostEditionTenantStatisticsExcludeTenantsWithoutEdition() throws Exception {
        tenantWithoutEdition("no-edition-host-" + System.nanoTime());

        JsonNode editionStatistics = getAbp(get("/api/services/app/HostDashboard/GetEditionTenantStatistics")
                .param("StartDate", LocalDateTime.now().minusMinutes(5).toString())
                .param("EndDate", LocalDateTime.now().plusMinutes(5).toString()))
                .path("result")
                .path("editionStatistics");

        assertThat(editionStatistics.findValuesAsText("label")).doesNotContain("未分配版本");
    }

    @Test
    void tenantDashboardReadRoutesAcceptOriginalGetQueryParameters() throws Exception {
        JsonNode dashboard = getAbp(get("/api/services/app/TenantDashboard/GetDashboardData")
                .param("SalesSummaryDatePeriod", "3")).path("result");
        assertThat(dashboard.path("salesSummary")).hasSize(4);

        JsonNode summary = getAbp(get("/api/services/app/TenantDashboard/GetSalesSummary")
                .param("SalesSummaryDatePeriod", "2")).path("result");
        assertThat(summary.path("salesSummary")).hasSize(4);
    }

    @Test
    void tenantDashboardUsesOriginalRandomGeneratorShapeAndLabels() throws Exception {
        JsonNode monthlyDashboard = getAbp(get("/api/services/app/TenantDashboard/GetDashboardData")
                .param("SalesSummaryDatePeriod", "3")).path("result");
        assertThat(monthlyDashboard.path("totalProfit").asInt()).isBetween(5000, 8999);
        assertThat(monthlyDashboard.path("newFeedbacks").asInt()).isBetween(1000, 4999);
        assertThat(monthlyDashboard.path("newOrders").asInt()).isBetween(100, 899);
        assertThat(monthlyDashboard.path("newUsers").asInt()).isBetween(50, 499);
        assertThat(monthlyDashboard.path("expenses").asInt()).isBetween(5000, 9999);
        assertThat(monthlyDashboard.path("growth").asInt()).isBetween(5000, 9999);
        assertThat(monthlyDashboard.path("revenue").asInt()).isBetween(1000, 8999);
        assertThat(monthlyDashboard.path("totalSales").asInt()).isBetween(10000, 89999);
        assertPercent(monthlyDashboard.path("transactionPercent").asInt());
        assertPercent(monthlyDashboard.path("newVisitPercent").asInt());
        assertPercent(monthlyDashboard.path("bouncePercent").asInt());
        assertThat(monthlyDashboard.path("dailySales")).hasSize(30)
                .allSatisfy(value -> assertThat(value.asInt()).isBetween(10, 49));
        assertThat(monthlyDashboard.path("profitShares")).hasSize(3);
        assertThat(sumArray(monthlyDashboard.path("profitShares"))).isEqualTo(100);
        assertSalesSummary(monthlyDashboard.path("salesSummary"), monthlyPeriods());

        JsonNode weeklySummary = getAbp(get("/api/services/app/TenantDashboard/GetSalesSummary")
                .param("SalesSummaryDatePeriod", "2")).path("result");
        assertThat(weeklySummary.path("totalSales").asInt()).isBetween(0, 2999);
        assertThat(weeklySummary.path("revenue").asInt()).isBetween(0, 2999);
        assertThat(weeklySummary.path("expenses").asInt()).isBetween(0, 2999);
        assertThat(weeklySummary.path("growth").asInt()).isBetween(0, 2999);
        assertSalesSummary(weeklySummary.path("salesSummary"), weeklyPeriods());

        JsonNode dailySummary = getAbp(get("/api/services/app/TenantDashboard/GetSalesSummary")
                .param("SalesSummaryDatePeriod", "1")).path("result");
        assertSalesSummary(dailySummary.path("salesSummary"), dailyPeriods());

        JsonNode memberActivity = getAbp(get("/api/services/app/TenantDashboard/GetMemberActivity"))
                .path("result")
                .path("memberActivities");
        assertThat(memberActivity.findValuesAsText("name")).containsExactly("Brain", "Jane", "Tim", "Kate");
        assertThat(memberActivity).allSatisfy(row -> {
            assertThat(row.path("earnings").asText()).startsWith("$");
            assertThat(row.path("cases").asInt()).isBetween(10, 99);
            assertThat(row.path("closed").asInt()).isBetween(10, 149);
            assertThat(Integer.parseInt(row.path("rate").asText().replace("%", ""))).isBetween(10, 98);
        });

        JsonNode regionalStats = getAbp(get("/api/services/app/TenantDashboard/GetRegionalStats"))
                .path("result")
                .path("stats");
        Set<String> countries = Set.of("Argentina", "China", "France", "Italy", "Japan",
                "Netherlands", "Russia", "Spain", "Turkey", "United States");
        assertThat(regionalStats).hasSize(4).allSatisfy(row -> {
            assertThat(countries).contains(row.path("countryName").asText());
            assertThat(row.path("sales").asInt()).isBetween(10000, 99999);
            assertThat(row.path("averagePrice").asInt()).isBetween(10, 99);
            assertThat(row.path("totalPrice").asInt()).isBetween(10000, 49999);
            assertThat(row.path("change")).hasSize(10)
                    .allSatisfy(value -> assertThat(value.asInt()).isBetween(-20, 19));
        });
    }

    private JsonNode getAbp(MockHttpServletRequestBuilder request) throws Exception {
        String body = mockMvc.perform(request.header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("success").asBoolean()).isTrue();
        return response;
    }

    private String adminToken() {
        return authService.authenticate("admin", "123qwe").orElseThrow().token();
    }

    private TenantItem tenant(String tenancyName, String name, String subscriptionEndDateUtc) {
        TenantItem item = new TenantItem();
        item.tenancyName = tenancyName;
        item.name = name;
        item.editionId = 2;
        item.isActive = true;
        TenantItem created = store.createTenant(item);
        created.subscriptionEndDateUtc = subscriptionEndDateUtc;
        return store.updateTenant(created);
    }

    private TenantItem tenantWithoutEdition(String tenancyName) {
        TenantItem item = new TenantItem();
        item.tenancyName = tenancyName;
        item.name = "No Edition Host Tenant";
        item.isActive = true;
        return store.createTenant(item);
    }

    private LocalDateTime expiringEndWithRoundingMismatch(LocalDateTime now) {
        for (int minutes = 15; minutes < 30 * 24 * 60; minutes += 15) {
            LocalDateTime candidate = now.plusMinutes(minutes).withSecond(0).withNano(0);
            double totalDays = Duration.between(now, candidate).toMinutes() / 1440.0;
            int originalRoundedDays = (int) Math.rint(totalDays);
            long localDateDays = ChronoUnit.DAYS.between(now.toLocalDate(), candidate.toLocalDate());
            double dayFraction = totalDays - Math.floor(totalDays);
            if (originalRoundedDays != localDateDays && Math.abs(dayFraction - 0.5) > 0.05) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to find an expiring date that exposes original rounding behavior");
    }

    private int originalRoundedRemainingDays(String startDate, LocalDateTime expiringAt) {
        LocalDateTime start = LocalDateTime.parse(startDate);
        double totalDays = Duration.between(start, expiringAt).toMillis() / (double) Duration.ofDays(1).toMillis();
        return Math.max(0, (int) Math.rint(totalDays));
    }

    private JsonNode expiringTenantByName(JsonNode rows, String tenantName) {
        for (JsonNode row : rows) {
            if (tenantName.equals(row.path("tenantName").asText())) {
                return row;
            }
        }
        throw new AssertionError("Expected expiring tenant was not returned: " + tenantName);
    }

    private int expiringTenantIndex(JsonNode rows, String tenantName) {
        for (int index = 0; index < rows.size(); index++) {
            if (tenantName.equals(rows.get(index).path("tenantName").asText())) {
                return index;
            }
        }
        throw new AssertionError("Expected expiring tenant was not returned: " + tenantName);
    }

    private void assertPercent(int value) {
        assertThat(value).isBetween(10, 99);
    }

    private void assertSalesSummary(JsonNode rows, List<String> periods) {
        assertThat(rows).hasSize(periods.size());
        for (int index = 0; index < periods.size(); index++) {
            JsonNode row = rows.get(index);
            assertThat(row.path("period").asText()).isEqualTo(periods.get(index));
            assertThat(row.path("sales").asInt()).isBetween(1000, 1999);
            assertThat(row.path("profit").asInt()).isBetween(100, 998);
        }
    }

    private int sumArray(JsonNode values) {
        int sum = 0;
        for (JsonNode value : values) {
            sum += value.asInt();
        }
        return sum;
    }

    private List<String> dailyPeriods() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate today = LocalDate.now();
        return List.of(
                today.minusDays(5).format(formatter),
                today.minusDays(4).format(formatter),
                today.minusDays(3).format(formatter),
                today.minusDays(2).format(formatter),
                today.minusDays(1).format(formatter));
    }

    private List<String> weeklyPeriods() {
        int previousYear = LocalDate.now().minusYears(1).getYear();
        return List.of(previousYear + " W4", previousYear + " W3",
                previousYear + " W2", previousYear + " W1");
    }

    private List<String> monthlyPeriods() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");
        LocalDate today = LocalDate.now();
        return List.of(
                today.minusMonths(4).format(formatter),
                today.minusMonths(3).format(formatter),
                today.minusMonths(2).format(formatter),
                today.minusMonths(1).format(formatter));
    }
}
