import { useEffect, useState } from 'react';
import { App as AntdApp, Button, Card, Form, Input, Select, Space, Switch, Table, Tabs, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { DeleteOutlined, DownloadOutlined, MailOutlined, SyncOutlined } from '@ant-design/icons';
import PageTitle from '../../components/PageTitle';
import { api } from '../../services/api';
import type { NotificationItem, NotificationSettings } from '../../types/domain';

type NotificationState = 'ALL' | 'UNREAD' | 'READ';

// Notification center mirrors AspNet Zero user notification and settings pages.
export default function NotificationsPage() {
  const { message } = AntdApp.useApp();
  const [items, setItems] = useState<NotificationItem[]>([]);
  const [settings, setSettings] = useState<NotificationSettings>();
  const [filter, setFilter] = useState('');
  const [state, setState] = useState<NotificationState>('ALL');
  const [unreadCount, setUnreadCount] = useState(0);
  const [loading, setLoading] = useState(false);

  async function loadNotifications(nextState = state, nextFilter = filter) {
    setLoading(true);
    try {
      const data = await api.userNotifications({ state: nextState, filter: nextFilter, maxResultCount: 100 });
      setItems(data.items);
      setUnreadCount(data.unreadCount);
    } finally {
      setLoading(false);
    }
  }

  async function loadSettings() {
    const data = await api.notificationSettings();
    setSettings(data);
  }

  useEffect(() => {
    void Promise.all([loadNotifications(), loadSettings()]);
  }, []);

  async function markRead(row: NotificationItem) {
    if (!row.id) {
      return;
    }
    await api.setNotificationAsRead(row.id);
    await loadNotifications();
  }

  async function markAllRead() {
    await api.setAllNotificationsAsRead();
    await loadNotifications();
    message.success('通知已全部标记为已读');
  }

  async function remove(row: NotificationItem) {
    if (!row.id) {
      return;
    }
    await api.deleteNotification(row.id);
    await loadNotifications();
    message.success('通知已删除');
  }

  async function clearAll() {
    await api.deleteAllUserNotifications(state);
    await loadNotifications();
    message.success('通知已清空');
  }

  async function downloadCollectedData(row: NotificationItem) {
    const binaryObjectId = row.data?.binaryObjectId;
    if (!binaryObjectId) {
      return;
    }
    await api.downloadBinaryFile(binaryObjectId, 'CollectedData.zip', 'application/zip');
    message.success('个人数据已下载');
  }

  async function downloadInvalidUsers(row: NotificationItem) {
    const fileToken = row.data?.fileToken;
    const fileName = row.data?.fileName;
    const fileType = row.data?.fileType;
    if (!fileToken || !fileName || !fileType) {
      return;
    }
    await api.downloadFile({ fileToken, fileName, fileType });
    message.success('错误报告已下载');
  }

  async function saveSettings(values: NotificationSettings) {
    await api.updateNotificationSettings({
      ...settings,
      ...values,
      notifications: values.notifications ?? settings?.notifications ?? [],
    });
    const saved = await api.notificationSettings();
    setSettings(saved);
    message.success('通知设置已保存');
  }

  const columns: ColumnsType<NotificationItem> = [
    {
      title: '状态',
      dataIndex: 'readState',
      width: 90,
      render: (readState: number) => <Tag color={readState === 0 ? 'red' : 'default'}>{readState === 0 ? '未读' : '已读'}</Tag>,
    },
    { title: '通知', dataIndex: 'message' },
    { title: '类型', dataIndex: 'notificationName', width: 210 },
    {
      title: '级别',
      dataIndex: 'severity',
      width: 100,
      render: (severity: string) => <Tag color={severity === 'Success' ? 'green' : 'blue'}>{severity}</Tag>,
    },
    { title: '时间', dataIndex: 'creationTime', width: 210 },
    {
      title: '操作',
      width: 240,
      render: (_, row) => (
        <Space>
          {row.notificationName === 'App.GdprDataPrepared' && row.data?.binaryObjectId ? (
            <Button size="small" icon={<DownloadOutlined />} onClick={() => void downloadCollectedData(row)}>
              下载
            </Button>
          ) : null}
          {row.notificationName === 'App.DownloadInvalidImportUsers' && row.data?.fileToken ? (
            <Button size="small" icon={<DownloadOutlined />} onClick={() => void downloadInvalidUsers(row)}>
              下载
            </Button>
          ) : null}
          <Button size="small" disabled={row.readState !== 0} onClick={() => void markRead(row)}>
            已读
          </Button>
          <Button danger size="small" icon={<DeleteOutlined />} onClick={() => void remove(row)}>
            删除
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <>
      <PageTitle title="通知中心" description="查看用户通知、处理已读状态并维护通知订阅设置" />
      <Tabs
        items={[
          {
            key: 'list',
            label: `通知列表 (${unreadCount})`,
            children: (
              <Space orientation="vertical" size={16} style={{ width: '100%' }}>
                <Card>
                  <Space wrap>
                    <Input.Search
                      allowClear
                      placeholder="搜索通知"
                      style={{ width: 280 }}
                      value={filter}
                      onChange={(event) => setFilter(event.target.value)}
                      onSearch={(value) => {
                        setFilter(value);
                        void loadNotifications(state, value);
                      }}
                    />
                    <Select<NotificationState>
                      value={state}
                      style={{ width: 140 }}
                      options={[
                        { label: '全部', value: 'ALL' },
                        { label: '未读', value: 'UNREAD' },
                        { label: '已读', value: 'READ' },
                      ]}
                      onChange={(value) => {
                        setState(value);
                        void loadNotifications(value, filter);
                      }}
                    />
                    <Button icon={<SyncOutlined />} onClick={() => void loadNotifications()}>
                      刷新
                    </Button>
                    <Button icon={<MailOutlined />} onClick={() => void markAllRead()}>
                      全部已读
                    </Button>
                    <Button danger icon={<DeleteOutlined />} onClick={() => void clearAll()}>
                      清空当前筛选
                    </Button>
                  </Space>
                </Card>
                <Table rowKey={(row) => String(row.id)} columns={columns} dataSource={items} loading={loading} />
              </Space>
            ),
          },
          {
            key: 'settings',
            label: '通知设置',
            children: (
              <Card>
                <Form
                  key={settings ? JSON.stringify(settings) : 'notification-settings'}
                  initialValues={settings}
                  layout="vertical"
                  onFinish={saveSettings}
                  style={{ maxWidth: 780 }}
                >
                  <Form.Item name="receiveNotifications" label="接收通知" valuePropName="checked">
                    <Switch />
                  </Form.Item>
                  <Form.List name="notifications">
                    {(fields) => (
                      <Space orientation="vertical" style={{ width: '100%' }}>
                        {fields.map((field) => (
                          <Card size="small" key={field.key}>
                            <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                              <Form.Item name={[field.name, 'displayName']} noStyle>
                                <Input variant="borderless" readOnly style={{ width: 260 }} />
                              </Form.Item>
                              <Form.Item name={[field.name, 'name']} noStyle hidden>
                                <Input maxLength={96} />
                              </Form.Item>
                              <Form.Item name={[field.name, 'isSubscribed']} valuePropName="checked" noStyle>
                                <Switch />
                              </Form.Item>
                            </Space>
                          </Card>
                        ))}
                      </Space>
                    )}
                  </Form.List>
                  <Button type="primary" htmlType="submit" style={{ marginTop: 16 }}>
                    保存设置
                  </Button>
                </Form>
              </Card>
            ),
          },
        ]}
      />
    </>
  );
}
