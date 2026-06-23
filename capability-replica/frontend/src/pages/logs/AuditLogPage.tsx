import { DownloadOutlined, EyeOutlined, SearchOutlined } from '@ant-design/icons';
import {
  App as AntdApp,
  Button,
  Card,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
} from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import type { Dayjs } from 'dayjs';
import { useEffect, useState } from 'react';
import PageTitle from '../../components/PageTitle';
import { api } from '../../services/api';
import type { AuditLog, EntityChangeItem, EntityHistoryObjectType, EntityPropertyChangeItem } from '../../types/domain';

const { RangePicker } = DatePicker;

type AuditFilterValues = {
  range?: [Dayjs, Dayjs];
  userName?: string;
  serviceName?: string;
  methodName?: string;
  browserInfo?: string;
  hasException?: string;
  minExecutionDuration?: number;
  maxExecutionDuration?: number;
};

type EntityFilterValues = {
  range?: [Dayjs, Dayjs];
  userName?: string;
  entityTypeFullName?: string;
};

// Operation and entity history console copied from AuditLogAppService.
export default function AuditLogPage() {
  const { message } = AntdApp.useApp();
  const [auditForm] = Form.useForm<AuditFilterValues>();
  const [entityForm] = Form.useForm<EntityFilterValues>();
  const [auditItems, setAuditItems] = useState<AuditLog[]>([]);
  const [entityItems, setEntityItems] = useState<EntityChangeItem[]>([]);
  const [entityTypes, setEntityTypes] = useState<EntityHistoryObjectType[]>([]);
  const [propertyItems, setPropertyItems] = useState<EntityPropertyChangeItem[]>([]);
  const [typeHistoryItems, setTypeHistoryItems] = useState<EntityChangeItem[]>([]);
  const [selectedEntity, setSelectedEntity] = useState<EntityChangeItem>();
  const [auditTotal, setAuditTotal] = useState(0);
  const [entityTotal, setEntityTotal] = useState(0);
  const [auditLoading, setAuditLoading] = useState(false);
  const [entityLoading, setEntityLoading] = useState(false);
  const [propertyOpen, setPropertyOpen] = useState(false);
  const [typeHistoryOpen, setTypeHistoryOpen] = useState(false);
  const [auditPage, setAuditPage] = useState({ current: 1, pageSize: 10 });
  const [entityPage, setEntityPage] = useState({ current: 1, pageSize: 10 });

  useEffect(() => {
    void Promise.all([loadAuditLogs(), loadEntityChanges(), loadEntityTypes()]);
  }, []);

  async function loadEntityTypes() {
    setEntityTypes(await api.entityHistoryObjectTypes());
  }

  async function loadAuditLogs(page = auditPage) {
    setAuditLoading(true);
    try {
      const payload = auditPayload(page);
      const data = await api.auditLogs(payload);
      setAuditItems(data.items);
      setAuditTotal(data.totalCount);
      setAuditPage(page);
    } finally {
      setAuditLoading(false);
    }
  }

  async function loadEntityChanges(page = entityPage) {
    setEntityLoading(true);
    try {
      const payload = entityPayload(page);
      const data = await api.entityChanges(payload);
      setEntityItems(data.items);
      setEntityTotal(data.totalCount);
      setEntityPage(page);
    } finally {
      setEntityLoading(false);
    }
  }

  async function exportAuditLogs() {
    await api.downloadFile(await api.exportAuditLogs(auditPayload({ current: 1, pageSize: 10000 })));
    message.success('审计日志已导出');
  }

  async function exportEntityChanges() {
    await api.downloadFile(await api.exportEntityChanges(entityPayload({ current: 1, pageSize: 10000 })));
    message.success('实体变更已导出');
  }

  async function openPropertyChanges(row: EntityChangeItem) {
    setSelectedEntity(row);
    setPropertyItems(await api.entityPropertyChanges(row.id));
    setPropertyOpen(true);
  }

  async function openTypeHistory(row: EntityChangeItem) {
    setSelectedEntity(row);
    const data = await api.entityTypeChanges({
      entityTypeFullName: row.entityTypeFullName,
      entityId: row.entityId,
      maxResultCount: 50,
    });
    setTypeHistoryItems(data.items);
    setTypeHistoryOpen(true);
  }

  function auditPayload(page: { current: number; pageSize: number }) {
    const values = auditForm.getFieldsValue();
    return {
      startDate: values.range?.[0]?.format('YYYY-MM-DDTHH:mm:ss'),
      endDate: values.range?.[1]?.format('YYYY-MM-DDTHH:mm:ss'),
      userName: values.userName,
      serviceName: values.serviceName,
      methodName: values.methodName,
      browserInfo: values.browserInfo,
      hasException: values.hasException === undefined ? undefined : values.hasException === 'true',
      minExecutionDuration: values.minExecutionDuration,
      maxExecutionDuration: values.maxExecutionDuration,
      skipCount: (page.current - 1) * page.pageSize,
      maxResultCount: page.pageSize,
      sorting: 'executionTime DESC',
    };
  }

  function entityPayload(page: { current: number; pageSize: number }) {
    const values = entityForm.getFieldsValue();
    return {
      startDate: values.range?.[0]?.format('YYYY-MM-DDTHH:mm:ss'),
      endDate: values.range?.[1]?.format('YYYY-MM-DDTHH:mm:ss'),
      userName: values.userName,
      entityTypeFullName: values.entityTypeFullName,
      skipCount: (page.current - 1) * page.pageSize,
      maxResultCount: page.pageSize,
      sorting: 'changeTime DESC',
    };
  }

  const auditColumns: ColumnsType<AuditLog> = [
    { title: '时间', dataIndex: 'executionTime', width: 190, render: (_, row) => row.executionTime ?? row.time },
    { title: '用户', dataIndex: 'userName', width: 120 },
    { title: '服务', dataIndex: 'serviceName', width: 180 },
    { title: '方法', dataIndex: 'methodName', width: 180 },
    {
      title: '耗时',
      dataIndex: 'executionDuration',
      width: 110,
      render: (value?: number) => <Tag color={value && value > 120 ? 'orange' : 'blue'}>{value ?? 0} ms</Tag>,
    },
    { title: 'IP', dataIndex: 'clientIpAddress', width: 130 },
    { title: '浏览器', dataIndex: 'browserInfo', width: 120 },
    {
      title: '结果',
      dataIndex: 'result',
      width: 120,
      render: (value?: string, row?: AuditLog) =>
        row?.exception ? <Tag color="red">异常</Tag> : <Tag color="green">{value || '成功'}</Tag>,
    },
    { title: '异常', dataIndex: 'exception', ellipsis: true },
  ];

  const entityColumns: ColumnsType<EntityChangeItem> = [
    { title: '时间', dataIndex: 'changeTime', width: 190 },
    { title: '用户', dataIndex: 'userName', width: 120 },
    { title: '实体', dataIndex: 'entityTypeDescription', width: 120 },
    { title: '实体ID', dataIndex: 'entityId', width: 220, ellipsis: true },
    {
      title: '变更类型',
      dataIndex: 'changeTypeName',
      width: 120,
      render: (value?: string) => <Tag color={changeTypeColor(value)}>{value}</Tag>,
    },
    { title: '实体类型', dataIndex: 'entityTypeFullName', ellipsis: true },
    {
      title: '操作',
      width: 180,
      render: (_, row) => (
        <Space>
          <Button size="small" icon={<EyeOutlined />} onClick={() => void openPropertyChanges(row)}>
            属性
          </Button>
          <Button size="small" onClick={() => void openTypeHistory(row)}>
            实例历史
          </Button>
        </Space>
      ),
    },
  ];

  const propertyColumns: ColumnsType<EntityPropertyChangeItem> = [
    { title: '属性', dataIndex: 'propertyName', width: 180 },
    { title: '更新前', dataIndex: 'originalValue' },
    { title: '更新后', dataIndex: 'newValue' },
    { title: '类型', dataIndex: 'propertyTypeFullName', width: 220, ellipsis: true },
  ];

  return (
    <div className="page-body">
      <PageTitle title="操作日志" description="审计日志、实体历史和属性变更" />
      <Tabs
        items={[
          {
            key: 'audit',
            label: '操作日志',
            forceRender: true,
            children: (
              <Space orientation="vertical" size={16} style={{ width: '100%' }}>
                <Card>
                  <Form form={auditForm} layout="inline" onFinish={() => void loadAuditLogs({ current: 1, pageSize: auditPage.pageSize })}>
                    <Form.Item name="range" label="时间">
                      <RangePicker showTime style={{ width: 360 }} />
                    </Form.Item>
                    <Form.Item name="userName" label="用户">
                      <Input allowClear style={{ width: 130 }} />
                    </Form.Item>
                    <Form.Item name="serviceName" label="服务">
                      <Input allowClear style={{ width: 170 }} />
                    </Form.Item>
                    <Form.Item name="methodName" label="方法">
                      <Input allowClear style={{ width: 150 }} />
                    </Form.Item>
                    <Form.Item name="browserInfo" label="浏览器">
                      <Input allowClear style={{ width: 120 }} />
                    </Form.Item>
                    <Form.Item name="hasException" label="异常">
                      <Select
                        allowClear
                        style={{ width: 110 }}
                        options={[
                          { label: '有异常', value: 'true' },
                          { label: '无异常', value: 'false' },
                        ]}
                      />
                    </Form.Item>
                    <Form.Item name="minExecutionDuration" label="最小耗时">
                      <InputNumber min={0} style={{ width: 100 }} />
                    </Form.Item>
                    <Form.Item name="maxExecutionDuration" label="最大耗时">
                      <InputNumber min={0} style={{ width: 100 }} />
                    </Form.Item>
                    <Space>
                      <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>
                        查询
                      </Button>
                      <Button icon={<DownloadOutlined />} onClick={() => void exportAuditLogs()}>
                        导出
                      </Button>
                    </Space>
                  </Form>
                </Card>
                <Table
                  rowKey={(row) => String(row.id ?? `${row.executionTime}-${row.methodName}`)}
                  loading={auditLoading}
                  dataSource={auditItems}
                  columns={auditColumns}
                  scroll={{ x: 1280 }}
                  pagination={pagination(auditPage, auditTotal)}
                  onChange={(next) => void loadAuditLogs(toPage(next, auditPage))}
                />
              </Space>
            ),
          },
          {
            key: 'entity',
            label: '实体变更',
            forceRender: true,
            children: (
              <Space orientation="vertical" size={16} style={{ width: '100%' }}>
                <Card>
                  <Form form={entityForm} layout="inline" onFinish={() => void loadEntityChanges({ current: 1, pageSize: entityPage.pageSize })}>
                    <Form.Item name="range" label="时间">
                      <RangePicker showTime style={{ width: 360 }} />
                    </Form.Item>
                    <Form.Item name="userName" label="用户">
                      <Input allowClear style={{ width: 140 }} />
                    </Form.Item>
                    <Form.Item name="entityTypeFullName" label="实体">
                      <Select
                        allowClear
                        showSearch
                        optionFilterProp="label"
                        style={{ width: 240 }}
                        options={entityTypes.map((item) => ({ label: item.name, value: item.value }))}
                      />
                    </Form.Item>
                    <Space>
                      <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>
                        查询
                      </Button>
                      <Button icon={<DownloadOutlined />} onClick={() => void exportEntityChanges()}>
                        导出
                      </Button>
                    </Space>
                  </Form>
                </Card>
                <Table
                  rowKey={(row) => String(row.id)}
                  loading={entityLoading}
                  dataSource={entityItems}
                  columns={entityColumns}
                  scroll={{ x: 1280 }}
                  pagination={pagination(entityPage, entityTotal)}
                  onChange={(next) => void loadEntityChanges(toPage(next, entityPage))}
                />
              </Space>
            ),
          },
        ]}
      />
      <Modal
        title={`${selectedEntity?.entityTypeDescription ?? '实体'}属性变更`}
        open={propertyOpen}
        footer={null}
        onCancel={() => setPropertyOpen(false)}
        width={760}
      >
        <Table rowKey={(row) => String(row.id)} dataSource={propertyItems} columns={propertyColumns} pagination={false} />
      </Modal>
      <Modal
        title={`${selectedEntity?.entityTypeDescription ?? '实体'}实例历史`}
        open={typeHistoryOpen}
        footer={null}
        onCancel={() => setTypeHistoryOpen(false)}
        width={900}
      >
        <Table
          rowKey={(row) => String(row.id)}
          dataSource={typeHistoryItems}
          columns={entityColumns.filter((column) => column.title !== '操作')}
          pagination={false}
          scroll={{ x: 900 }}
        />
      </Modal>
    </div>
  );
}

function pagination(page: { current: number; pageSize: number }, total: number): TablePaginationConfig {
  return {
    current: page.current,
    pageSize: page.pageSize,
    total,
    showSizeChanger: true,
    showTotal: (count) => `共 ${count} 条`,
  };
}

function toPage(next: TablePaginationConfig, fallback: { current: number; pageSize: number }) {
  return {
    current: next.current ?? fallback.current,
    pageSize: next.pageSize ?? fallback.pageSize,
  };
}

function changeTypeColor(value?: string) {
  if (value === 'Created') return 'green';
  if (value === 'Deleted') return 'red';
  if (value === 'Updated') return 'blue';
  return 'default';
}
