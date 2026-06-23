import { useEffect, useMemo, useState } from 'react';
import {
  App as AntdApp,
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  CheckOutlined,
  CloseOutlined,
  DeleteOutlined,
  EditOutlined,
  FileTextOutlined,
  PlusOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import PageTitle from '../../components/PageTitle';
import { api } from '../../services/api';
import { paymentGatewayOptions } from './paymentGatewayOptions';
import type {
  EditionItem,
  FeatureItem,
  FeatureValueItem,
  InvoiceItem,
  PaymentGatewayItem,
  SubscriptionPaymentItem,
  TenantItem,
} from '../../types/domain';

type FeatureTarget = { type: 'tenant'; id?: number; title: string };

// Copies AspNet Zero tenant, edition, payment, and invoice admin flows.
export default function TenantsEditionsPage() {
  const { message } = AntdApp.useApp();
  const [tenantForm] = Form.useForm<TenantItem>();
  const [editionForm] = Form.useForm<EditionItem>();
  const [tenants, setTenants] = useState<TenantItem[]>([]);
  const [editions, setEditions] = useState<EditionItem[]>([]);
  const [payments, setPayments] = useState<SubscriptionPaymentItem[]>([]);
  const [gateways, setGateways] = useState<PaymentGatewayItem[]>([]);
  const [features, setFeatures] = useState<FeatureItem[]>([]);
  const [featureValues, setFeatureValues] = useState<Record<string, string>>({});
  const [featureTarget, setFeatureTarget] = useState<FeatureTarget>();
  const [editingTenant, setEditingTenant] = useState<TenantItem>();
  const [editingEdition, setEditingEdition] = useState<EditionItem>();
  const [invoice, setInvoice] = useState<InvoiceItem>();
  const [tenantModalOpen, setTenantModalOpen] = useState(false);
  const [editionModalOpen, setEditionModalOpen] = useState(false);
  const [featureModalOpen, setFeatureModalOpen] = useState(false);
  const [invoiceModalOpen, setInvoiceModalOpen] = useState(false);
  const [selectedEditionId, setSelectedEditionId] = useState<number>();
  const [selectedGateway, setSelectedGateway] = useState<number>(2);
  const [selectedPeriod, setSelectedPeriod] = useState<number>(30);
  const [recurring, setRecurring] = useState(true);
  const [stripeKey, setStripeKey] = useState('');
  const [paypalClientId, setPaypalClientId] = useState('');
  const [loading, setLoading] = useState(false);

  async function load() {
    setLoading(true);
    try {
      const [tenantData, editionData, paymentData, gatewayData, stripeConfig, paypalConfig] = await Promise.all([
        api.tenants({ maxResultCount: 100 }),
        api.editions(),
        api.paymentHistory({ maxResultCount: 100 }),
        api.activePaymentGateways(),
        api.stripeConfiguration(),
        api.payPalConfiguration(),
      ]);
      setTenants(tenantData.items);
      setEditions(editionData.items);
      setPayments(paymentData.items);
      setGateways(gatewayData);
      setStripeKey(stripeConfig.publishableKey);
      setPaypalClientId(paypalConfig.clientId);
      setSelectedEditionId((current) => current ?? editionData.items[0]?.id);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  const editionOptions = useMemo(
    () => editions.map((item) => ({ label: item.displayName, value: item.id })),
    [editions],
  );

  const gatewayOptions = useMemo(() => paymentGatewayOptions(gateways), [gateways]);

  function openTenant(row?: TenantItem) {
    setEditingTenant(row);
    tenantForm.setFieldsValue(
      row ?? {
        tenancyName: '',
        name: '',
        adminEmailAddress: '',
        isActive: true,
        isInTrialPeriod: false,
        editionId: editions[0]?.id,
      },
    );
    setTenantModalOpen(true);
  }

  async function saveTenant(values: TenantItem) {
    if (editingTenant?.id) {
      await api.updateTenant({ ...editingTenant, ...values });
    } else {
      await api.createTenant(values);
    }
    message.success('租户已保存');
    setTenantModalOpen(false);
    await load();
  }

  async function openTenantFeatures(row: TenantItem) {
    const data = await api.tenantFeatures(row.id);
    setFeatureTarget({ type: 'tenant', id: row.id, title: row.name });
    setFeatures(data.features);
    setFeatureValues(toFeatureMap(data.featureValues));
    setFeatureModalOpen(true);
  }

  async function saveTenantFeatures() {
    if (!featureTarget?.id) return;
    await api.updateTenantFeatures(featureTarget.id, toNameValues(featureValues));
    message.success('租户功能已保存');
    setFeatureModalOpen(false);
    await load();
  }

  async function resetTenantFeatures() {
    if (!featureTarget?.id) return;
    await api.resetTenantFeatures(featureTarget.id);
    const data = await api.tenantFeatures(featureTarget.id);
    setFeatureValues(toFeatureMap(data.featureValues));
    message.success('已恢复默认功能值');
  }

  function openEdition(row?: EditionItem) {
    setEditingEdition(row);
    editionForm.setFieldsValue(
      row ?? {
        displayName: '',
        dailyPrice: 0,
        weeklyPrice: 0,
        monthlyPrice: 0,
        annualPrice: 0,
        waitingDayAfterExpire: 30,
        trialDayCount: 14,
      },
    );
    setFeatureValues(row?.featureValues ?? {});
    setEditionModalOpen(true);
  }

  async function saveEdition(values: EditionItem) {
    const payload = { ...editingEdition, ...values };
    if (editingEdition?.id) {
      await api.updateEdition(payload, toNameValues(featureValues));
    } else {
      await api.createEdition(payload, toNameValues(featureValues));
    }
    message.success('版本已保存');
    setEditionModalOpen(false);
    await load();
  }

  async function createPayment() {
    const paymentId = await api.createPayment({
      editionId: selectedEditionId,
      editionPaymentType: 1,
      paymentPeriodType: selectedPeriod,
      subscriptionPaymentGatewayType: selectedGateway,
      recurringPaymentEnabled: recurring,
      successUrl: window.location.href,
      errorUrl: window.location.href,
    });
    message.success(`付款已创建：${paymentId}`);
    await load();
  }

  async function completePayment(row: SubscriptionPaymentItem) {
    await api.completePayment(row.id);
    message.success('付款已标记完成');
    await load();
  }

  async function failPayment(row: SubscriptionPaymentItem) {
    await api.failPayment(row.id);
    message.warning('付款已标记失败');
    await load();
  }

  async function createInvoice(row: SubscriptionPaymentItem) {
    await api.createInvoice(row.id);
    const data = await api.invoiceInfo(row.id);
    setInvoice(data ?? undefined);
    setInvoiceModalOpen(true);
    await load();
  }

  async function confirmStripe(row: SubscriptionPaymentItem) {
    const sessionId = await api.createStripePaymentSession(row.id, window.location.href, window.location.href);
    await api.confirmStripePayment(sessionId);
    message.success(`Stripe会话已确认：${sessionId}`);
    await load();
  }

  async function confirmPayPal(row: SubscriptionPaymentItem) {
    await api.confirmPayPalPayment(row.id, `PAYPAL-${row.id}`);
    message.success('PayPal订单已确认');
    await load();
  }

  const tenantColumns: ColumnsType<TenantItem> = [
    { title: '租户名', dataIndex: 'tenancyName', width: 150 },
    { title: '名称', dataIndex: 'name', width: 160 },
    { title: '版本', dataIndex: 'editionDisplayName', width: 140 },
    {
      title: '状态',
      dataIndex: 'isActive',
      width: 100,
      render: (value: boolean) => <Tag color={value ? 'green' : 'red'}>{value ? '启用' : '停用'}</Tag>,
    },
    {
      title: '试用',
      dataIndex: 'isInTrialPeriod',
      width: 90,
      render: (value: boolean) => (value ? <Tag color="blue">试用</Tag> : <Tag>正式</Tag>),
    },
    { title: '订阅到期', dataIndex: 'subscriptionEndDateUtc', width: 210 },
    { title: '管理员邮箱', dataIndex: 'adminEmailAddress', width: 220 },
    {
      title: '操作',
      width: 300,
      render: (_, row) => (
        <Space>
          <Button size="small" icon={<EditOutlined />} onClick={() => openTenant(row)}>
            编辑
          </Button>
          <Button size="small" onClick={() => void openTenantFeatures(row)}>
            功能
          </Button>
          <Button
            size="small"
            onClick={() => {
              if (row.id) void api.unlockTenantAdmin(row.id).then(() => message.success('管理员已解锁'));
            }}
          >
            解锁管理员
          </Button>
          {row.id !== 1 ? (
            <Popconfirm title="确定删除该租户吗？" onConfirm={() => row.id && api.deleteTenant(row.id).then(load)}>
              <Button size="small" danger icon={<DeleteOutlined />} />
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  const editionColumns: ColumnsType<EditionItem> = [
    { title: '名称', dataIndex: 'displayName', width: 160 },
    { title: '月价', dataIndex: 'monthlyPrice', width: 100 },
    { title: '年价', dataIndex: 'annualPrice', width: 100 },
    { title: '试用天数', dataIndex: 'trialDayCount', width: 110 },
    { title: '过期等待天数', dataIndex: 'waitingDayAfterExpire', width: 130 },
    { title: '到期切换版本', dataIndex: 'expiringEditionDisplayName', width: 160 },
    {
      title: '类型',
      dataIndex: 'isFree',
      width: 90,
      render: (value: boolean) => <Tag color={value ? 'green' : 'blue'}>{value ? '免费' : '付费'}</Tag>,
    },
    {
      title: '操作',
      width: 180,
      render: (_, row) => (
        <Space>
          <Button size="small" icon={<EditOutlined />} onClick={() => openEdition(row)}>
            编辑
          </Button>
          <Popconfirm title="确定删除该版本吗？" onConfirm={() => row.id && api.deleteEdition(row.id).then(load)}>
            <Button size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const paymentColumns: ColumnsType<SubscriptionPaymentItem> = [
    { title: '描述', dataIndex: 'description', width: 260 },
    { title: '网关', dataIndex: 'gatewayName', width: 100 },
    { title: '金额', dataIndex: 'amount', width: 100 },
    { title: '版本', dataIndex: 'editionDisplayName', width: 140 },
    { title: '周期', dataIndex: 'paymentPeriodTypeName', width: 100 },
    {
      title: '状态',
      dataIndex: 'statusName',
      width: 110,
      render: (value: string) => <Tag color={paymentStatusColor(value)}>{value}</Tag>,
    },
    { title: '发票号', dataIndex: 'invoiceNo', width: 150 },
    { title: '创建时间', dataIndex: 'creationTime', width: 220 },
    {
      title: '操作',
      width: 360,
      render: (_, row) => (
        <Space>
          <Button size="small" icon={<CheckOutlined />} onClick={() => void completePayment(row)}>
            完成
          </Button>
          <Button size="small" icon={<CloseOutlined />} onClick={() => void failPayment(row)}>
            失败
          </Button>
          <Button size="small" onClick={() => void confirmStripe(row)}>
            Stripe
          </Button>
          <Button size="small" onClick={() => void confirmPayPal(row)}>
            PayPal
          </Button>
          <Button size="small" icon={<FileTextOutlined />} onClick={() => void createInvoice(row)}>
            发票
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <div className="page-body">
      <PageTitle title="租户与订阅" description="维护租户、版本、订阅付款和本地发票" />
      <Card>
        <Tabs
          items={[
            {
              key: 'tenants',
              label: '租户',
              children: (
                <>
                  <Space style={{ marginBottom: 16 }}>
                    <Button type="primary" icon={<PlusOutlined />} onClick={() => openTenant()}>
                      新增租户
                    </Button>
                    <Button icon={<ReloadOutlined />} onClick={() => void load()}>
                      刷新
                    </Button>
                  </Space>
                  <Table rowKey="id" columns={tenantColumns} dataSource={tenants} loading={loading} />
                </>
              ),
            },
            {
              key: 'editions',
              label: '版本',
              children: (
                <>
                  <Space style={{ marginBottom: 16 }}>
                    <Button type="primary" icon={<PlusOutlined />} onClick={() => openEdition()}>
                      新增版本
                    </Button>
                    <Button icon={<ReloadOutlined />} onClick={() => void load()}>
                      刷新
                    </Button>
                  </Space>
                  <Table rowKey="id" columns={editionColumns} dataSource={editions} loading={loading} />
                </>
              ),
            },
            {
              key: 'payments',
              label: '订阅支付',
              children: (
                <>
                  <Space wrap style={{ marginBottom: 16 }}>
                    <Select style={{ width: 180 }} options={editionOptions} value={selectedEditionId} onChange={setSelectedEditionId} />
                    <Select
                      style={{ width: 140 }}
                      value={selectedPeriod}
                      onChange={setSelectedPeriod}
                      options={[
                        { label: '日付', value: 1 },
                        { label: '周付', value: 7 },
                        { label: '月付', value: 30 },
                        { label: '年付', value: 365 },
                      ]}
                    />
                    <Select style={{ width: 140 }} options={gatewayOptions} value={selectedGateway} onChange={setSelectedGateway} />
                    <Switch checkedChildren="循环" unCheckedChildren="单次" checked={recurring} onChange={setRecurring} />
                    <Button type="primary" icon={<PlusOutlined />} onClick={() => void createPayment()}>
                      创建付款
                    </Button>
                    <Typography.Text type="secondary">Stripe: {stripeKey}</Typography.Text>
                    <Typography.Text type="secondary">PayPal: {paypalClientId}</Typography.Text>
                  </Space>
                  <Table rowKey="id" columns={paymentColumns} dataSource={payments} loading={loading} />
                </>
              ),
            },
          ]}
        />
      </Card>

      <Modal
        forceRender
        title={editingTenant ? '编辑租户' : '新增租户'}
        open={tenantModalOpen}
        onOk={() => tenantForm.submit()}
        onCancel={() => setTenantModalOpen(false)}
        width={760}
      >
        <Form form={tenantForm} layout="vertical" onFinish={(values) => void saveTenant(values)}>
          <Form.Item name="tenancyName" label="租户名" rules={[{ required: true, max: 64, pattern: /^[a-zA-Z][a-zA-Z0-9_-]{1,}$/ }]}>
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="name" label="名称" rules={[{ required: true, max: 128 }]}>
            <Input maxLength={128} />
          </Form.Item>
          <Form.Item name="adminEmailAddress" label="管理员邮箱" rules={[{ required: true, type: 'email', max: 256 }]}>
            <Input maxLength={256} />
          </Form.Item>
          <Form.Item name="editionId" label="版本">
            <Select allowClear options={editionOptions} />
          </Form.Item>
          <Form.Item name="subscriptionEndDateUtc" label="订阅到期">
            <Input placeholder="2026-12-31T23:59:59" />
          </Form.Item>
          <Form.Item name="connectionString" label="连接字符串">
            <Input.TextArea maxLength={1024} autoSize={{ minRows: 2, maxRows: 4 }} />
          </Form.Item>
          <Space>
            <Form.Item name="isActive" label="启用" valuePropName="checked">
              <Switch />
            </Form.Item>
            <Form.Item name="isInTrialPeriod" label="试用期" valuePropName="checked">
              <Switch />
            </Form.Item>
            <Form.Item name="shouldChangePasswordOnNextLogin" label="下次登录改密" valuePropName="checked">
              <Switch />
            </Form.Item>
            <Form.Item name="sendActivationEmail" label="发送激活邮件" valuePropName="checked">
              <Switch />
            </Form.Item>
          </Space>
        </Form>
      </Modal>

      <Modal
        forceRender
        title={editingEdition ? '编辑版本' : '新增版本'}
        open={editionModalOpen}
        onOk={() => editionForm.submit()}
        onCancel={() => setEditionModalOpen(false)}
        width={760}
      >
        <Form form={editionForm} layout="vertical" onFinish={(values) => void saveEdition(values)}>
          <Form.Item name="displayName" label="显示名" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Space wrap>
            <Form.Item name="dailyPrice" label="日价">
              <InputNumber min={0} precision={2} />
            </Form.Item>
            <Form.Item name="weeklyPrice" label="周价">
              <InputNumber min={0} precision={2} />
            </Form.Item>
            <Form.Item name="monthlyPrice" label="月价">
              <InputNumber min={0} precision={2} />
            </Form.Item>
            <Form.Item name="annualPrice" label="年价">
              <InputNumber min={0} precision={2} />
            </Form.Item>
          </Space>
          <Space wrap>
            <Form.Item name="trialDayCount" label="试用天数">
              <InputNumber min={0} />
            </Form.Item>
            <Form.Item name="waitingDayAfterExpire" label="过期等待天数">
              <InputNumber min={0} />
            </Form.Item>
            <Form.Item name="expiringEditionId" label="到期切换版本">
              <Select allowClear style={{ width: 180 }} options={editionOptions} />
            </Form.Item>
          </Space>
          <Typography.Title level={5}>功能值</Typography.Title>
          <FeatureEditor features={features.length ? features : editionFeatureDefinitions(editions)} values={featureValues} onChange={setFeatureValues} />
        </Form>
      </Modal>

      <Modal
        title={`功能设置：${featureTarget?.title ?? ''}`}
        open={featureModalOpen}
        onOk={() => void saveTenantFeatures()}
        onCancel={() => setFeatureModalOpen(false)}
        footer={[
          <Button key="reset" onClick={() => void resetTenantFeatures()}>
            恢复默认
          </Button>,
          <Button key="cancel" onClick={() => setFeatureModalOpen(false)}>
            取消
          </Button>,
          <Button key="save" type="primary" onClick={() => void saveTenantFeatures()}>
            保存
          </Button>,
        ]}
        width={760}
      >
        <FeatureEditor features={features} values={featureValues} onChange={setFeatureValues} />
      </Modal>

      <Modal title="发票信息" open={invoiceModalOpen} onCancel={() => setInvoiceModalOpen(false)} footer={null} width={680}>
        {invoice ? (
          <div style={{ display: 'grid', gap: 10 }}>
            <Typography.Text strong>{invoice.invoiceNo}</Typography.Text>
            <Typography.Text>版本：{invoice.editionDisplayName}</Typography.Text>
            <Typography.Text>金额：{invoice.amount}</Typography.Text>
            <Typography.Text>租户：{invoice.tenantLegalName}</Typography.Text>
            <Typography.Text>租户地址：{invoice.tenantAddress?.join(' / ')}</Typography.Text>
            <Typography.Text>税号：{invoice.tenantTaxNo || '-'}</Typography.Text>
            <Typography.Text>宿主：{invoice.hostLegalName}</Typography.Text>
            <Typography.Text>宿主地址：{invoice.hostAddress?.join(' / ')}</Typography.Text>
          </div>
        ) : null}
      </Modal>
    </div>
  );
}

function FeatureEditor({
  features,
  values,
  onChange,
}: {
  features: FeatureItem[];
  values: Record<string, string>;
  onChange: (values: Record<string, string>) => void;
}) {
  if (!features.length) {
    return <Typography.Text type="secondary">暂无功能定义</Typography.Text>;
  }
  return (
    <div style={{ display: 'grid', gap: 10 }}>
      {features.map((feature) => {
        const isCheckbox = String(feature.inputType?.name ?? '').toUpperCase().includes('CHECKBOX');
        const value = values[feature.name] ?? feature.defaultValue ?? '';
        return (
          <div key={feature.name} style={{ display: 'grid', gridTemplateColumns: '220px 1fr', alignItems: 'center', gap: 12 }}>
            <div>
              <Typography.Text strong>{feature.displayName ?? feature.name}</Typography.Text>
              <Typography.Text type="secondary" style={{ display: 'block', fontSize: 12 }}>
                {feature.description}
              </Typography.Text>
            </div>
            {isCheckbox ? (
              <Switch
                checked={value === 'true'}
                onChange={(checked) => onChange({ ...values, [feature.name]: checked ? 'true' : 'false' })}
              />
            ) : (
              <Input value={value} onChange={(event) => onChange({ ...values, [feature.name]: event.target.value })} />
            )}
          </div>
        );
      })}
    </div>
  );
}

function toFeatureMap(values?: FeatureValueItem[]) {
  return Object.fromEntries((values ?? []).map((item) => [item.name, item.value ?? '']));
}

function toNameValues(values: Record<string, string>): FeatureValueItem[] {
  return Object.entries(values).map(([name, value]) => ({ name, value }));
}

function paymentStatusColor(status?: string) {
  if (status === 'Completed' || status === 'Paid') return 'green';
  if (status === 'Failed') return 'red';
  if (status === 'Cancelled') return 'orange';
  return 'blue';
}

function editionFeatureDefinitions(editions: EditionItem[]): FeatureItem[] {
  const first = editions.find((item) => item.featureValues)?.featureValues ?? {};
  return Object.keys(first).map((name) => ({
    name,
    displayName: name,
    defaultValue: first[name],
    inputType: { name: first[name] === 'true' || first[name] === 'false' ? 'CHECKBOX' : 'SINGLE_LINE_STRING' },
  }));
}
