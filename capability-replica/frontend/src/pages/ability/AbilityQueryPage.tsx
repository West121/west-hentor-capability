import { Alert, Button, Card, Form, Input, Modal, Select, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useEffect, useState } from 'react';
import PageTitle from '../../components/PageTitle';
import { api } from '../../services/api';
import { safeAbilityDescriptionHtml } from './abilityDescriptionHtml';
import {
  abilityQueryColumnTitles,
  abilityQueryColumnWidths,
  abilityQuerySearchFields,
  abilityQueryTableFields,
} from './abilityQueryColumns';
import {
  buildAbilitySearchPayload,
  defaultAbilityPageSize,
  qualificationOptions,
  queryLabAbilityText,
} from './abilitySearch';
import type { Ability, AbilityHistoryItem, Laboratory } from '../../types/domain';

// Public query page focuses on searching and viewing history.
export default function AbilityQueryPage() {
  const [items, setItems] = useState<Ability[]>([]);
  const [total, setTotal] = useState(0);
  const [searchValues, setSearchValues] = useState<Record<string, unknown>>({});
  const [pageState, setPageState] = useState({ current: 1, pageSize: defaultAbilityPageSize });
  const [loading, setLoading] = useState(false);
  const [labs, setLabs] = useState<Laboratory[]>([]);
  const [history, setHistory] = useState<AbilityHistoryItem[]>([]);
  const [historyAbility, setHistoryAbility] = useState<Ability>();
  const [historyOpen, setHistoryOpen] = useState(false);
  const [abilityDescription, setAbilityDescription] = useState('');
  const [form] = Form.useForm();

  async function load(
    values: Record<string, unknown> = searchValues,
    current = pageState.current,
    pageSize = pageState.pageSize,
  ) {
    setLoading(true);
    setSearchValues(values);
    setPageState({ current, pageSize });
    try {
      const data = await api.queryAbilities(buildAbilitySearchPayload(values, undefined, pageSize, (current - 1) * pageSize));
      setItems(data.items);
      setLabs(data.labs ?? []);
      setTotal(data.totalCount);
    } finally {
      setLoading(false);
    }
  }

  async function showHistory(row: Ability) {
    setHistoryAbility(row);
    const data = await api.queryAbilityHistory(row.id);
    setHistory(data);
    setHistoryOpen(true);
  }

  async function bootstrap() {
    const session = await api.session();
    setAbilityDescription(session.application?.settings?.['Ability.Description'] ?? '');
    await load({}, 1, defaultAbilityPageSize);
  }

  useEffect(() => {
    void bootstrap();
  }, []);

  const columns: ColumnsType<Ability> = abilityQueryTableFields.map((field) => {
    if (field === 'labAbilities') {
      return {
        title: abilityQueryColumnTitles[field],
        dataIndex: field,
        width: abilityQueryColumnWidths[field],
        render: (values: Ability['labAbilities']) => queryLabAbilityText(values),
      };
    }
    if (field === 'actions') {
      return {
        title: abilityQueryColumnTitles[field],
        fixed: 'right',
        width: abilityQueryColumnWidths[field],
        render: (_, row) => (
          <Button type="link" onClick={() => void showHistory(row)}>
            历史
          </Button>
        ),
      };
    }
    return {
      title: abilityQueryColumnTitles[field],
      dataIndex: field,
      width: abilityQueryColumnWidths[field],
    };
  });
  const tableScrollX = abilityQueryTableFields.reduce((total, field) => total + abilityQueryColumnWidths[field], 0);

  return (
    <div className="page-body ability-query-page">
      <PageTitle title="能力表查询" description="按样品、项目、标准号、方法描述查询检测能力" />
      {abilityDescription ? (
        <Alert
          showIcon
          type="info"
          title="Instruction"
          description={
            <div
              className="ability-description-content"
              dangerouslySetInnerHTML={{ __html: safeAbilityDescriptionHtml(abilityDescription) }}
            />
          }
        />
      ) : null}
      <Card className="ability-query-search-card">
        <Form
          form={form}
          className="ability-query-form"
          layout="inline"
          initialValues={{ ability: '无' }}
          onFinish={(values) => void load(values, 1, pageState.pageSize)}
        >
          {abilityQuerySearchFields.map((field) => (
            <Form.Item key={field.name} name={field.name}>
              {field.name === 'labAbility' ? (
                <Select
                  allowClear
                  showSearch
                  placeholder={field.placeholder}
                  style={{ width: 120 }}
                  options={labs.map((lab) => ({ label: lab.code ?? lab.name, value: lab.code }))}
                />
              ) : field.name === 'ability' ? (
                <Select style={{ width: 110 }} options={qualificationOptions} />
              ) : (
                <Input placeholder={field.placeholder} allowClear />
              )}
            </Form.Item>
          ))}
          <Button type="primary" htmlType="submit">
            查询
          </Button>
          <Button
            onClick={() => {
              form.resetFields();
              void load({}, 1, pageState.pageSize);
            }}
          >
            重置
          </Button>
        </Form>
      </Card>
      <Card className="ability-query-table-card">
        <Table
          className="ability-query-table"
          rowKey="id"
          loading={loading}
          dataSource={items}
          pagination={{
            current: pageState.current,
            pageSize: pageState.pageSize,
            total,
            showSizeChanger: false,
            showTotal: (count) => `共 ${count} 条`,
            onChange: (current, pageSize) => void load(searchValues, current, pageSize),
          }}
          scroll={{ x: tableScrollX }}
          columns={columns}
        />
      </Card>
      <Modal
        title={`${historyAbility?.samplingName ?? ''}${historyAbility?.testItem ? ` / ${historyAbility.testItem}` : ''} 变更历史`}
        open={historyOpen}
        footer={null}
        onCancel={() => setHistoryOpen(false)}
        width={800}
      >
        <Table
          rowKey={(row) => String(row.id ?? `${row.changeTime}-${row.displayName}`)}
          dataSource={history}
          pagination={false}
          columns={[
            {
              title: '类型',
              dataIndex: 'changeType',
              render: (value: string) => <Tag color={historyTypeColor(value)}>{historyTypeLabel(value)}</Tag>,
            },
            { title: '时间', dataIndex: 'changeTime' },
            { title: '操作人', dataIndex: 'user' },
            { title: '属性', dataIndex: 'displayName' },
            { title: '更新前', dataIndex: 'originalValue' },
            { title: '更新后', dataIndex: 'newValue' },
          ]}
        />
      </Modal>
    </div>
  );
}

function historyTypeLabel(value?: string) {
  if (value === 'Created' || value === '创建') return '创建';
  if (value === 'Updated' || value === '更新') return '更新';
  if (value === 'Deleted' || value === '删除') return '删除';
  return value || '-';
}

function historyTypeColor(value?: string) {
  const label = historyTypeLabel(value);
  if (label === '创建') return 'green';
  if (label === '删除') return 'red';
  return 'orange';
}
