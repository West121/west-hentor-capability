import { useEffect, useMemo, useState } from 'react';
import { Card, Col, Progress, Row, Segmented, Statistic, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import PageTitle from '../../components/PageTitle';
import { api } from '../../services/api';
import type {
  MemberActivity,
  RegionalStatCountry,
  SalesSummaryData,
  TenantDashboardData,
  TenantGeneralStats,
  TenantSalesSummaryOutput,
} from '../../types/domain';

const periodOptions = [
  { label: '日', value: 1 },
  { label: '周', value: 2 },
  { label: '月', value: 3 },
];

// Tenant dashboard copied from TenantDashboardAppService.
export default function TenantDashboardPage() {
  const [dashboard, setDashboard] = useState<TenantDashboardData>();
  const [salesSummary, setSalesSummary] = useState<TenantSalesSummaryOutput>();
  const [members, setMembers] = useState<MemberActivity[]>([]);
  const [regions, setRegions] = useState<RegionalStatCountry[]>([]);
  const [generalStats, setGeneralStats] = useState<TenantGeneralStats>();
  const [period, setPeriod] = useState(1);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setLoading(true);
    void Promise.all([
      api.tenantDashboardData(period),
      api.tenantSalesSummary(period),
      api.tenantMemberActivity(),
      api.tenantRegionalStats(),
      api.tenantGeneralStats(),
    ])
      .then(([dashboardData, salesData, memberData, regionalData, generalData]) => {
        setDashboard(dashboardData);
        setSalesSummary(salesData);
        setMembers(memberData.memberActivities);
        setRegions(regionalData.stats);
        setGeneralStats(generalData);
      })
      .finally(() => setLoading(false));
  }, [period]);

  const maxDailySales = useMemo(() => Math.max(...(dashboard?.dailySales ?? [0]), 1), [dashboard]);
  const maxRegionalSales = useMemo(() => Math.max(...regions.map((row) => Number(row.sales)), 1), [regions]);

  const salesColumns: ColumnsType<SalesSummaryData> = [
    { title: '周期', dataIndex: 'period', width: 120 },
    { title: '销售', dataIndex: 'sales', width: 120 },
    { title: '利润', dataIndex: 'profit', width: 120 },
    {
      title: '利润率',
      render: (_, row) => <Progress percent={Math.round((row.profit / Math.max(row.sales, 1)) * 100)} size="small" />,
    },
  ];

  const memberColumns: ColumnsType<MemberActivity> = [
    { title: '成员', dataIndex: 'name', width: 160 },
    { title: '收益', dataIndex: 'earnings', width: 120 },
    { title: '案件', dataIndex: 'cases', width: 100 },
    { title: '关闭', dataIndex: 'closed', width: 100 },
    { title: '完成率', dataIndex: 'rate', width: 110 },
  ];

  const regionalColumns: ColumnsType<RegionalStatCountry> = [
    { title: '区域', dataIndex: 'countryName', width: 180 },
    {
      title: '销售',
      dataIndex: 'sales',
      render: (value: number) => (
        <div style={{ display: 'grid', gridTemplateColumns: '110px minmax(120px, 1fr)', gap: 12, alignItems: 'center' }}>
          <strong>¥{Number(value).toFixed(2)}</strong>
          <Progress percent={Math.round((Number(value) / maxRegionalSales) * 100)} showInfo={false} size="small" />
        </div>
      ),
    },
    { title: '均价', dataIndex: 'averagePrice', width: 120 },
    { title: '总价', dataIndex: 'totalPrice', width: 120 },
  ];

  return (
    <div className="page-body">
      <PageTitle title="租户看板" description="租户内运营、销售摘要、成员活动和区域统计" />
      <Row gutter={[16, 16]}>
        <Col xs={24} md={6}>
          <Card loading={loading}>
            <Statistic title="总利润" value={dashboard?.totalProfit ?? 0} prefix="¥" />
          </Card>
        </Col>
        <Col xs={24} md={6}>
          <Card loading={loading}>
            <Statistic title="新反馈" value={dashboard?.newFeedbacks ?? 0} />
          </Card>
        </Col>
        <Col xs={24} md={6}>
          <Card loading={loading}>
            <Statistic title="新订单" value={dashboard?.newOrders ?? 0} />
          </Card>
        </Col>
        <Col xs={24} md={6}>
          <Card loading={loading}>
            <Statistic title="新用户" value={dashboard?.newUsers ?? 0} />
          </Card>
        </Col>
      </Row>
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={14}>
          <Card
            title="销售摘要"
            loading={loading}
            extra={<Segmented options={periodOptions} value={period} onChange={(value) => setPeriod(Number(value))} />}
          >
            <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
              <Col xs={12} md={6}>
                <Statistic title="总销售" value={salesSummary?.totalSales ?? 0} />
              </Col>
              <Col xs={12} md={6}>
                <Statistic title="收入" value={salesSummary?.revenue ?? 0} prefix="¥" />
              </Col>
              <Col xs={12} md={6}>
                <Statistic title="支出" value={salesSummary?.expenses ?? 0} prefix="¥" />
              </Col>
              <Col xs={12} md={6}>
                <Statistic title="增长" value={salesSummary?.growth ?? 0} />
              </Col>
            </Row>
            <Table rowKey="period" columns={salesColumns} dataSource={salesSummary?.salesSummary ?? []} pagination={false} />
          </Card>
        </Col>
        <Col xs={24} lg={10}>
          <Card title="综合指标" loading={loading}>
            <div style={{ display: 'grid', gap: 18 }}>
              <Progress type="dashboard" percent={generalStats?.transactionPercent ?? 0} format={(value) => `交易 ${value}%`} />
              <Progress percent={generalStats?.newVisitPercent ?? 0} status="active" />
              <Progress percent={generalStats?.bouncePercent ?? 0} strokeColor="#faad14" />
              <div>
                <strong>利润来源</strong>
                <div style={{ display: 'grid', gap: 8, marginTop: 12 }}>
                  {(dashboard?.profitShares ?? []).map((value, index) => (
                    <Tag key={index} color={index === 0 ? 'green' : index === 1 ? 'blue' : 'orange'}>
                      来源 {index + 1}: {value}%
                    </Tag>
                  ))}
                </div>
              </div>
            </div>
          </Card>
        </Col>
      </Row>
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          <Card title="30日销售" loading={loading}>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(10, minmax(0, 1fr))', gap: 8, alignItems: 'end', height: 180 }}>
              {(dashboard?.dailySales ?? []).map((value, index) => (
                <div key={`${index}-${value}`} style={{ display: 'grid', gap: 4, alignItems: 'end' }}>
                  <div
                    title={`${value}`}
                    style={{
                      height: `${Math.max(12, (value / maxDailySales) * 150)}px`,
                      background: '#fa541c',
                      borderRadius: 4,
                    }}
                  />
                </div>
              ))}
            </div>
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card title="成员活动" loading={loading}>
            <Table rowKey="name" columns={memberColumns} dataSource={members} pagination={false} />
          </Card>
        </Col>
      </Row>
      <Card title="区域统计" loading={loading}>
        <Table rowKey="countryName" columns={regionalColumns} dataSource={regions} pagination={false} />
      </Card>
    </div>
  );
}
