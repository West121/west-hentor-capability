import { useEffect, useState } from 'react';
import { App as AntdApp, Button, Popconfirm, Space, Table } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { ClearOutlined, ReloadOutlined } from '@ant-design/icons';
import PageTitle from '../../components/PageTitle';
import { api } from '../../services/api';
import type { CacheItem } from '../../types/domain';

// Cache maintenance mirrors AspNet Zero's CachingAppService page.
export default function CacheManagementPage() {
  const { message } = AntdApp.useApp();
  const [items, setItems] = useState<CacheItem[]>([]);
  const [loading, setLoading] = useState(false);

  async function load() {
    setLoading(true);
    try {
      const data = await api.caches();
      setItems(data.items);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  async function clearOne(row: CacheItem) {
    await api.clearCache(row.name);
    await load();
    message.success(`${row.name} 已清理`);
  }

  async function clearAll() {
    await api.clearAllCaches();
    await load();
    message.success('全部缓存已清理');
  }

  const columns: ColumnsType<CacheItem> = [
    { title: '缓存名称', dataIndex: 'name' },
    {
      title: '操作',
      width: 140,
      render: (_, row) => (
        <Popconfirm title={`清理 ${row.name}?`} onConfirm={() => void clearOne(row)}>
          <Button size="small" icon={<ClearOutlined />}>
            清理
          </Button>
        </Popconfirm>
      ),
    },
  ];

  return (
    <>
      <PageTitle title="缓存管理" description="查看并清理本地复制系统的应用缓存状态" />
      <Space style={{ marginBottom: 16 }}>
        <Button icon={<ReloadOutlined />} onClick={() => void load()}>
          刷新
        </Button>
        <Popconfirm title="清理全部缓存?" onConfirm={() => void clearAll()}>
          <Button danger icon={<ClearOutlined />}>
            清理全部
          </Button>
        </Popconfirm>
      </Space>
      <Table rowKey="name" columns={columns} dataSource={items} loading={loading} />
    </>
  );
}
