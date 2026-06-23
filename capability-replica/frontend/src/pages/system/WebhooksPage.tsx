import { useEffect, useMemo, useState } from 'react';
import {
  App as AntdApp,
  Button,
  Card,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { EditOutlined, PlusOutlined, ReloadOutlined, SendOutlined, SyncOutlined } from '@ant-design/icons';
import PageTitle from '../../components/PageTitle';
import { api } from '../../services/api';
import type {
  WebhookDefinitionItem,
  WebhookEventItem,
  WebhookSendAttemptItem,
  WebhookSubscriptionItem,
} from '../../types/domain';

type SubscriptionFormValues = WebhookSubscriptionItem & { headersText?: string };

// Webhook management mirrors AspNet Zero webhook subscriptions and send attempts.
export default function WebhooksPage() {
  const { message } = AntdApp.useApp();
  const [form] = Form.useForm<SubscriptionFormValues>();
  const [subscriptions, setSubscriptions] = useState<WebhookSubscriptionItem[]>([]);
  const [definitions, setDefinitions] = useState<WebhookDefinitionItem[]>([]);
  const [attempts, setAttempts] = useState<WebhookSendAttemptItem[]>([]);
  const [selectedSubscriptionId, setSelectedSubscriptionId] = useState<string>();
  const [editing, setEditing] = useState<WebhookSubscriptionItem>();
  const [event, setEvent] = useState<WebhookEventItem>();
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [eventModalOpen, setEventModalOpen] = useState(false);

  async function load(subscriptionId = selectedSubscriptionId) {
    setLoading(true);
    try {
      const [subscriptionData, definitionData] = await Promise.all([api.webhookSubscriptions(), api.availableWebhooks()]);
      setSubscriptions(subscriptionData.items);
      setDefinitions(definitionData.items);
      const nextSubscriptionId = subscriptionId ?? subscriptionData.items[0]?.id;
      setSelectedSubscriptionId(nextSubscriptionId);
      if (nextSubscriptionId) {
        const attemptData = await api.webhookSendAttempts({ subscriptionId: nextSubscriptionId, maxResultCount: 20 });
        setAttempts(attemptData.items);
      } else {
        setAttempts([]);
      }
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  const webhookOptions = useMemo(
    () => definitions.map((item) => ({ label: item.displayName ?? item.name, value: item.name })),
    [definitions],
  );

  const subscriptionOptions = useMemo(
    () => subscriptions.map((item) => ({ label: item.webhookUri, value: item.id })),
    [subscriptions],
  );

  function openModal(row?: WebhookSubscriptionItem) {
    setEditing(row);
    form.setFieldsValue(
      row
        ? { ...row, headersText: headersToText(row.headers) }
        : { webhookUri: 'https://example.local/webhook', isActive: true, webhooks: ['App.TestWebhook'], headersText: '' },
    );
    setModalOpen(true);
  }

  async function save(values: SubscriptionFormValues) {
    const payload: WebhookSubscriptionItem = {
      ...editing,
      ...values,
      headers: textToHeaders(values.headersText),
    };
    if (payload.id) {
      await api.updateWebhookSubscription(payload);
    } else {
      await api.addWebhookSubscription(payload);
    }
    message.success('Webhook订阅已保存');
    setModalOpen(false);
    await load(payload.id);
  }

  async function toggle(row: WebhookSubscriptionItem, isActive: boolean) {
    if (!row.id) return;
    await api.activateWebhookSubscription(row.id, isActive);
    message.success(isActive ? '订阅已启用' : '订阅已停用');
    await load(row.id);
  }

  async function publishTestWebhook() {
    const result = await api.publishTestWebhook();
    message.success(result);
    await load(selectedSubscriptionId);
  }

  async function changeSubscription(subscriptionId: string) {
    setSelectedSubscriptionId(subscriptionId);
    await load(subscriptionId);
  }

  async function resend(row: WebhookSendAttemptItem) {
    if (!row.id) return;
    await api.resendWebhookAttempt(row.id);
    message.success('已重发到本地队列');
    await load(selectedSubscriptionId);
  }

  async function openEvent(row: WebhookSendAttemptItem) {
    if (!row.webhookEventId) return;
    const data = await api.webhookEvent(row.webhookEventId);
    setEvent(data);
    setEventModalOpen(true);
  }

  const subscriptionColumns: ColumnsType<WebhookSubscriptionItem> = [
    { title: 'Webhook地址', dataIndex: 'webhookUri', ellipsis: true },
    {
      title: '订阅事件',
      dataIndex: 'webhooks',
      width: 220,
      render: (values?: string[]) => (
        <Space wrap>{values?.map((value) => <Tag key={value}>{value}</Tag>)}</Space>
      ),
    },
    {
      title: '启用',
      dataIndex: 'isActive',
      width: 120,
      render: (value: boolean, row) => <Switch checked={value} onChange={(checked) => void toggle(row, checked)} />,
    },
    { title: '创建时间', dataIndex: 'creationTime', width: 220 },
    {
      title: '操作',
      width: 110,
      render: (_, row) => (
        <Button size="small" icon={<EditOutlined />} onClick={() => openModal(row)}>
          编辑
        </Button>
      ),
    },
  ];

  const definitionColumns: ColumnsType<WebhookDefinitionItem> = [
    { title: '事件名', dataIndex: 'name', width: 220 },
    { title: '显示名', dataIndex: 'displayName', width: 180 },
    { title: '说明', dataIndex: 'description' },
  ];

  const attemptColumns: ColumnsType<WebhookSendAttemptItem> = [
    { title: '事件', dataIndex: 'webhookName', width: 180 },
    { title: 'Webhook地址', dataIndex: 'webhookUri', ellipsis: true },
    {
      title: '状态',
      dataIndex: 'responseStatusCode',
      width: 110,
      render: (value?: number) => <Tag color={value && value < 300 ? 'green' : 'orange'}>{value ?? '-'}</Tag>,
    },
    { title: '响应', dataIndex: 'response', width: 220 },
    { title: '重试', dataIndex: 'retryCount', width: 90 },
    { title: '创建时间', dataIndex: 'creationTime', width: 220 },
    {
      title: '操作',
      width: 170,
      render: (_, row) => (
        <Space>
          <Button size="small" onClick={() => void openEvent(row)}>
            事件
          </Button>
          <Button size="small" icon={<SyncOutlined />} onClick={() => void resend(row)}>
            重发
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <div className="page-body">
      <PageTitle title="Webhook订阅" description="维护Webhook订阅、测试事件和发送尝试记录" />
      <Card>
        <Tabs
          items={[
            {
              key: 'subscriptions',
              label: '订阅',
              children: (
                <>
                  <Space style={{ marginBottom: 16 }}>
                    <Button type="primary" icon={<PlusOutlined />} onClick={() => openModal()}>
                      新增订阅
                    </Button>
                    <Button icon={<SendOutlined />} onClick={() => void publishTestWebhook()}>
                      发布测试Webhook
                    </Button>
                    <Button icon={<ReloadOutlined />} onClick={() => void load()}>
                      刷新
                    </Button>
                  </Space>
                  <Table rowKey="id" columns={subscriptionColumns} dataSource={subscriptions} loading={loading} />
                </>
              ),
            },
            {
              key: 'definitions',
              label: '可用事件',
              children: <Table rowKey="name" columns={definitionColumns} dataSource={definitions} loading={loading} />,
            },
            {
              key: 'attempts',
              label: '发送尝试',
              children: (
                <>
                  <Space style={{ marginBottom: 16 }}>
                    <Select
                      style={{ width: 360 }}
                      placeholder="选择订阅"
                      options={subscriptionOptions}
                      value={selectedSubscriptionId}
                      onChange={(value) => void changeSubscription(value)}
                    />
                    <Button icon={<ReloadOutlined />} onClick={() => void load(selectedSubscriptionId)}>
                      刷新
                    </Button>
                  </Space>
                  <Table rowKey="id" columns={attemptColumns} dataSource={attempts} loading={loading} />
                </>
              ),
            },
          ]}
        />
      </Card>

      <Modal
        forceRender
        title={editing ? '编辑Webhook订阅' : '新增Webhook订阅'}
        open={modalOpen}
        onOk={() => form.submit()}
        onCancel={() => setModalOpen(false)}
        width={720}
      >
        <Form form={form} layout="vertical" onFinish={save}>
          <Form.Item name="webhookUri" label="Webhook地址" rules={[{ required: true }, { type: 'url' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="webhooks" label="订阅事件" rules={[{ required: true }]}>
            <Select mode="multiple" options={webhookOptions} />
          </Form.Item>
          <Form.Item name="isActive" label="启用" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name="secret" label="Secret">
            <Input.Password placeholder="可留空" />
          </Form.Item>
          <Form.Item name="headersText" label="请求头">
            <Input.TextArea rows={4} placeholder="每行一个请求头，例如 X-Token=demo" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal title="Webhook事件" open={eventModalOpen} onCancel={() => setEventModalOpen(false)} footer={null} width={720}>
        <Typography.Paragraph>
          <Typography.Text strong>事件：</Typography.Text>
          {event?.webhookName}
        </Typography.Paragraph>
        <Typography.Paragraph>
          <Typography.Text strong>时间：</Typography.Text>
          {event?.creationTime}
        </Typography.Paragraph>
        <Input.TextArea rows={8} readOnly value={event?.data ?? ''} />
      </Modal>
    </div>
  );
}

function headersToText(headers?: Record<string, string>) {
  return Object.entries(headers ?? {})
    .map(([key, value]) => `${key}=${value}`)
    .join('\n');
}

function textToHeaders(value?: string) {
  return (value ?? '')
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
    .reduce<Record<string, string>>((headers, line) => {
      const [key, ...rest] = line.split('=');
      if (key) {
        headers[key.trim()] = rest.join('=').trim();
      }
      return headers;
    }, {});
}
