import { Button, Modal, Table } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useEffect, useState } from 'react';
import { api } from '../../services/api';
import type { AbilityHistoryDetailItem, AbilityHistoryItem } from '../../types/domain';

// Ability history log copied from Pages.Log.AbilityHistory.
export default function AbilityHistoryPage() {
  const [items, setItems] = useState<AbilityHistoryItem[]>([]);
  const [detailItems, setDetailItems] = useState<AbilityHistoryDetailItem[]>([]);
  const [selectedHistory, setSelectedHistory] = useState<AbilityHistoryItem>();
  const [detailOpen, setDetailOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });

  useEffect(() => {
    void loadHistory();
  }, []);

  async function loadHistory(page = pagination) {
    setLoading(true);
    try {
      const data = await api.abilityHistory({
        sorting: 'changeTime DESC',
        maxResultCount: page.pageSize,
        skipCount: (page.current - 1) * page.pageSize,
      });
      setItems(data.items);
      setPagination({ ...page, total: data.totalCount });
    } finally {
      setLoading(false);
    }
  }

  async function openDetail(row: AbilityHistoryItem) {
    setSelectedHistory(row);
    setDetailOpen(true);
    setDetailLoading(true);
    try {
      const data = await api.abilityHistoryDetail(row.id);
      setDetailItems(data.items);
    } finally {
      setDetailLoading(false);
    }
  }

  const historyColumns: ColumnsType<AbilityHistoryItem> = [
    {
      title: '',
      dataIndex: 'detail',
      width: 230,
      render: (_, row) => (
        <button type="button" className="ability-history-detail-link" onClick={() => void openDetail(row)}>
          详情
        </button>
      ),
    },
    {
      title: '类型',
      dataIndex: 'changeType',
      width: 520,
      render: (value?: string) => <span className={`ability-history-type ability-history-type-${changeTypeKey(value)}`}>{changeTypeLabel(value)}</span>,
    },
    {
      title: '时间',
      dataIndex: 'changeTime',
      width: 330,
      render: (value?: string) => formatHistoryTime(value),
    },
    { title: '操作人', dataIndex: 'user', render: (value?: string) => <UserText value={value} /> },
  ];

  return (
    <div className="ability-history-page">
      <Table
        className="ability-history-table"
        rowKey={(row) => String(row.id ?? `${row.changeTime}-${row.entityId}`)}
        loading={loading}
        dataSource={items}
        columns={historyColumns}
        scroll={{ x: 1120 }}
        pagination={{
          current: pagination.current,
          pageSize: pagination.pageSize,
          total: pagination.total,
          showSizeChanger: true,
          pageSizeOptions: [10, 20, 50],
          showTotal: (total) => `共 ${total} 条`,
        }}
        onChange={(next) =>
          void loadHistory({
            current: next.current ?? 1,
            pageSize: next.pageSize ?? pagination.pageSize,
            total: pagination.total,
          })
        }
      />
      <Modal
        title={`查看${userDisplay(selectedHistory?.user)} ${formatHistoryTime(selectedHistory?.changeTime, true)} ${changeTypeLabel(
          selectedHistory?.changeType,
        )} 详细`}
        open={detailOpen}
        footer={<Button onClick={() => setDetailOpen(false)}>关闭</Button>}
        onCancel={() => setDetailOpen(false)}
        width={960}
        className="ability-history-modal"
      >
        <div className={`ability-history-detail-list ${detailLoading ? 'ability-history-detail-loading' : ''}`}>
          {detailLoading ? <div className="ability-history-detail-empty">加载中...</div> : null}
          {!detailLoading && detailItems.length === 0 ? <div className="ability-history-detail-empty" /> : null}
          {!detailLoading
            ? detailItems.map((item, index) => (
                <div className="ability-history-detail-row" key={`${item.propertyName}-${index}`}>
                  <div>属性： {item.displayName || item.propertyName || '-'}</div>
                  <div>更新前： {detailValue(item.originalValue)}</div>
                  <div>更新后： {detailValue(item.newValue)}</div>
                </div>
              ))
            : null}
        </div>
      </Modal>
    </div>
  );
}

function changeTypeKey(value?: string) {
  if (value === 'Created' || value === '创建') return 'created';
  if (value === 'Deleted' || value === '删除') return 'deleted';
  if (value === 'Updated' || value === '更新') return 'updated';
  return 'default';
}

function changeTypeLabel(value?: string) {
  if (value === 'Created' || value === '创建') return '创建';
  if (value === 'Deleted' || value === '删除') return '删除';
  if (value === 'Updated' || value === '更新') return '更新';
  return value || '-';
}

function formatHistoryTime(value?: string, seconds = false) {
  if (!value) {
    return '-';
  }
  const normalized = value.replace('T', ' ');
  return normalized.slice(0, seconds ? 19 : 16);
}

function detailValue(value?: string) {
  return value === undefined || value === null || value === '' ? '-' : value;
}

function userDisplay(value?: string) {
  if (!value) {
    return '-';
  }
  return value.includes('[') ? value : `${value}[${value}]`;
}

function UserText({ value }: { value?: string }) {
  const text = userDisplay(value);
  const match = text.match(/^(.*?)(\[[^\]]+\])$/);
  if (!match) {
    return <span className="ability-history-user">{text}</span>;
  }
  return (
    <span className="ability-history-user">
      <span>{match[1]}</span>
      <span className="ability-history-user-login">{match[2]}</span>
    </span>
  );
}
