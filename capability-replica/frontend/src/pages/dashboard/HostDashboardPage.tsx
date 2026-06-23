import { useEffect, useMemo, useState } from 'react';
import { Card, Col, Progress, Row, Statistic, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import PageTitle from '../../components/PageTitle';
import { api } from '../../services/api';
import type {
  ExpiringTenant,
  HostExpiringTenantsOutput,
  HostIncomeStatisticsOutput,
  HostRecentTenantsOutput,
  HostTopStatsData,
  IncomeStatistic,
  RecentTenant,
  TenantEditionStat,
} from '../../types/domain';

const monthRange = {
  startDate: dayjs().subtract(30, 'day').format('YYYY-MM-DD'),
  endDate: dayjs().format('YYYY-MM-DD'),
};

// Host dashboard copied from HostDashboardAppService.
export default function HostDashboardPage() {
  const [topStats, setTopStats] = useState<HostTopStatsData>();
  const [recentTenants, setRecentTenants] = useState<HostRecentTenantsOutput>();
  const [expiringTenants, setExpiringTenants] = useState<HostExpiringTenantsOutput>();
  const [income, setIncome] = useState<HostIncomeStatisticsOutput>();
  const [editionStats, setEditionStats] = useState<TenantEditionStat[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setLoading(true);
    void Promise.all([
      api.hostTopStats(monthRange),
      api.hostRecentTenants(),
      api.hostExpiringTenants(),
      api.hostIncomeStatistics({ ...monthRange, incomeStatisticsDateInterval: 1 }),
      api.hostEditionTenantStatistics(monthRange),
    ])
      .then(([top, recent, expiring, incomeData, editions]) => {
        setTopStats(top);
        setRecentTenants(recent);
        setExpiringTenants(expiring);
        setIncome(incomeData);
        setEditionStats(editions.editionStatistics);
      })
      .finally(() => setLoading(false));
  }, []);

  const maxIncome = useMemo(
    () => Math.max(...(income?.incomeStatistics.map((row) => Number(row.amount)) ?? [0]), 1),
    [income],
  );
  const totalEditionCount = Math.max(editionStats.reduce((sum, row) => sum + row.value, 0), 1);

  const recentColumns: ColumnsType<RecentTenant> = [
    { title: '租户ID', dataIndex: 'id', width: 100 },
    { title: '租户', dataIndex: 'name' },
    { title: '创建时间', dataIndex: 'creationTime', width: 220 },
  ];

  const expiringColumns: ColumnsType<ExpiringTenant> = [
    { title: '租户', dataIndex: 'tenantName' },
    {
      title: '剩余天数',
      dataIndex: 'remainingDayCount',
      width: 140,
      render: (value: number) => <Tag color={value <= 7 ? 'red' : 'orange'}>{value} 天</Tag>,
    },
  ];

  const incomeColumns: ColumnsType<IncomeStatistic> = [
    { title: '日期', dataIndex: 'label', width: 180 },
    {
      title: '订阅收入',
      dataIndex: 'amount',
      render: (value: number) => (
        <div style={{ display: 'grid', gridTemplateColumns: '120px minmax(120px, 1fr)', gap: 12, alignItems: 'center' }}>
          <strong>¥{Number(value).toFixed(2)}</strong>
          <Progress percent={Math.round((Number(value) / maxIncome) * 100)} showInfo={false} size="small" />
        </div>
      ),
    },
  ];

  return (
    <div className="page-body">
      <PageTitle title="宿主看板" description="租户增长、订阅收入、版本分布和到期提醒" />
      <Row gutter={[16, 16]}>
        <Col xs={24} md={6}>
          <Card loading={loading}>
            <Statistic title="新增租户" value={topStats?.newTenantsCount ?? 0} />
          </Card>
        </Col>
        <Col xs={24} md={6}>
          <Card loading={loading}>
            <Statistic title="新增订阅金额" value={topStats?.newSubscriptionAmount ?? 0} precision={2} prefix="¥" />
          </Card>
        </Col>
        <Col xs={24} md={6}>
          <Card loading={loading}>
            <Statistic title="平台用户" value={topStats?.dashboardPlaceholder1 ?? 0} />
          </Card>
        </Col>
        <Col xs={24} md={6}>
          <Card loading={loading}>
            <Statistic title="能力项目" value={topStats?.dashboardPlaceholder2 ?? 0} />
          </Card>
        </Col>
      </Row>
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          <Card title={`最近 ${recentTenants?.recentTenantsDayCount ?? 7} 天租户`} loading={loading}>
            <Table rowKey="id" columns={recentColumns} dataSource={recentTenants?.recentTenants ?? []} pagination={false} />
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card title={`未来 ${expiringTenants?.subscriptionEndAlertDayCount ?? 30} 天到期租户`} loading={loading}>
            <Table rowKey="tenantName" columns={expiringColumns} dataSource={expiringTenants?.expiringTenants ?? []} pagination={false} />
          </Card>
        </Col>
      </Row>
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={14}>
          <Card title="收入统计" loading={loading}>
            <Table rowKey="label" columns={incomeColumns} dataSource={income?.incomeStatistics ?? []} pagination={{ pageSize: 8 }} />
          </Card>
        </Col>
        <Col xs={24} lg={10}>
          <Card title="版本租户分布" loading={loading}>
            <div style={{ display: 'grid', gap: 16 }}>
              {editionStats.map((row) => (
                <div key={row.label}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6 }}>
                    <span>{row.label}</span>
                    <strong>{row.value}</strong>
                  </div>
                  <Progress percent={Math.round((row.value / totalEditionCount) * 100)} />
                </div>
              ))}
            </div>
          </Card>
        </Col>
      </Row>
    </div>
  );
}
