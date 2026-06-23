import assert from 'node:assert/strict';
import {
  hostDashboardEditionTenantStatisticsQuery,
  hostDashboardIncomeStatisticsQuery,
  hostDashboardTopStatsQuery,
  tenantDashboardDataQuery,
  tenantSalesSummaryQuery,
} from '../src/services/requestContracts.ts';

assert.equal(
  hostDashboardTopStatsQuery({
    startDate: new Date('2099-01-01T00:00:00.000Z'),
    endDate: new Date('2099-01-02T00:00:00.000Z'),
  }),
  '/api/services/app/HostDashboard/GetTopStatsData?StartDate=2099-01-01T00%3A00%3A00.000Z&EndDate=2099-01-02T00%3A00%3A00.000Z',
);

assert.equal(
  hostDashboardIncomeStatisticsQuery({
    incomeStatisticsDateInterval: 2,
    StartDate: '2099-01-01T00:00:00.000Z',
    EndDate: '2099-01-14T00:00:00.000Z',
  }),
  '/api/services/app/HostDashboard/GetIncomeStatistics?IncomeStatisticsDateInterval=2&StartDate=2099-01-01T00%3A00%3A00.000Z&EndDate=2099-01-14T00%3A00%3A00.000Z',
);

assert.equal(
  hostDashboardEditionTenantStatisticsQuery({ startDate: '2099-01-01', endDate: '2099-01-02' }),
  '/api/services/app/HostDashboard/GetEditionTenantStatistics?StartDate=2099-01-01&EndDate=2099-01-02',
);

assert.equal(
  tenantDashboardDataQuery(3),
  '/api/services/app/TenantDashboard/GetDashboardData?SalesSummaryDatePeriod=3',
);

assert.equal(
  tenantSalesSummaryQuery(2),
  '/api/services/app/TenantDashboard/GetSalesSummary?SalesSummaryDatePeriod=2',
);
