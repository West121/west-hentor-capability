import { useEffect, useMemo, useState } from 'react';
import {
  App as AntdApp,
  Button,
  Card,
  Descriptions,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { DeleteOutlined, EditOutlined, PlusOutlined, ReloadOutlined, SaveOutlined } from '@ant-design/icons';
import PageTitle from '../../components/PageTitle';
import { api } from '../../services/api';
import type {
  DynamicInputTypeInfo,
  DynamicParameterItem,
  DynamicParameterValueItem,
  EntityDynamicParameterItem,
  EntityDynamicParameterValueItem,
  EntityDynamicParameterValuesInputItem,
} from '../../types/domain';

const fallbackInputTypes = [
  { label: '单行文本', value: 'SINGLE_LINE_STRING' },
  { label: '多行文本', value: 'MULTI_LINE_STRING' },
  { label: '下拉框', value: 'COMBOBOX' },
  { label: '复选框', value: 'CHECKBOX' },
  { label: '单选框', value: 'RADIO_BUTTON' },
  { label: '多选下拉框', value: 'MULTI_SELECT_COMBOBOX' },
  { label: '数字', value: 'NUMBER' },
  { label: '日期', value: 'DATE' },
];

const fallbackEntityName = 'SgsMineral.CapabilityTable.AbilityTables.Ability';

function inputTypeLabel(value?: string) {
  return fallbackInputTypes.find((item) => item.value === value)?.label ?? value ?? '-';
}

function entityValueInputItems(rows: EntityDynamicParameterValueItem[]): EntityDynamicParameterValuesInputItem[] {
  const groups = new Map<string, EntityDynamicParameterValuesInputItem>();
  rows.forEach((row) => {
    if (!row.entityDynamicParameterId || !row.entityId) {
      return;
    }
    const key = `${row.entityDynamicParameterId}:${row.entityId}`;
    const group = groups.get(key) ?? {
      entityDynamicParameterId: row.entityDynamicParameterId,
      entityId: row.entityId,
      values: [],
    };
    group.values.push(row.value);
    groups.set(key, group);
  });
  return [...groups.values()];
}

// Dynamic parameter management mirrors AspNet Zero platform dynamic parameter pages.
export default function DynamicParametersPage() {
  const { message } = AntdApp.useApp();
  const [parameterForm] = Form.useForm<DynamicParameterItem>();
  const [valueForm] = Form.useForm<DynamicParameterValueItem>();
  const [entityForm] = Form.useForm<EntityDynamicParameterItem>();
  const [entityValueForm] = Form.useForm<EntityDynamicParameterValueItem>();
  const [parameters, setParameters] = useState<DynamicParameterItem[]>([]);
  const [values, setValues] = useState<DynamicParameterValueItem[]>([]);
  const [entityParameters, setEntityParameters] = useState<EntityDynamicParameterItem[]>([]);
  const [entityValues, setEntityValues] = useState<EntityDynamicParameterValueItem[]>([]);
  const [allowedInputTypes, setAllowedInputTypes] = useState<string[]>([]);
  const [inputTypeInfo, setInputTypeInfo] = useState<DynamicInputTypeInfo>();
  const [allowedEntities, setAllowedEntities] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [activeKey, setActiveKey] = useState('parameters');
  const [valueFilter, setValueFilter] = useState<number>();
  const [entityIdFilter, setEntityIdFilter] = useState('');
  const [editingParameter, setEditingParameter] = useState<DynamicParameterItem>();
  const [editingValue, setEditingValue] = useState<DynamicParameterValueItem>();
  const [editingEntity, setEditingEntity] = useState<EntityDynamicParameterItem>();
  const [editingEntityValue, setEditingEntityValue] = useState<EntityDynamicParameterValueItem>();
  const [parameterModalOpen, setParameterModalOpen] = useState(false);
  const [valueModalOpen, setValueModalOpen] = useState(false);
  const [entityModalOpen, setEntityModalOpen] = useState(false);
  const [entityValueModalOpen, setEntityValueModalOpen] = useState(false);

  async function load() {
    setLoading(true);
    try {
      const [parameterData, valueData, entityData, entityValueData, inputTypeData, entityNameData] = await Promise.all([
        api.dynamicParameters(),
        api.dynamicParameterValues(),
        api.entityDynamicParameters(),
        api.entityDynamicParameterValues(),
        api.dynamicAllowedInputTypeNames(),
        api.dynamicEntityNames(),
      ]);
      setParameters(parameterData.items);
      setValues(valueData.items);
      setEntityParameters(entityData.items);
      setEntityValues(entityValueData.items);
      setAllowedInputTypes(inputTypeData);
      setAllowedEntities(entityNameData);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  const parameterOptions = useMemo(
    () => parameters.map((item) => ({ label: `${item.displayName} (${item.parameterName})`, value: item.id })),
    [parameters],
  );

  const inputTypeOptions = useMemo(() => {
    const types = allowedInputTypes.length ? allowedInputTypes : fallbackInputTypes.map((item) => item.value);
    return types.map((value) => ({ label: inputTypeLabel(value), value }));
  }, [allowedInputTypes]);

  const entityOptions = useMemo(() => {
    const names = allowedEntities.length ? allowedEntities : [fallbackEntityName, 'Capability.Ability'];
    return names.map((value) => ({ label: value, value }));
  }, [allowedEntities]);

  const entityParameterOptions = useMemo(
    () =>
      entityParameters.map((item) => ({
        label: `${item.entityFullName} / ${item.displayName ?? item.parameterName}`,
        value: item.id,
      })),
    [entityParameters],
  );

  const filteredValues = valueFilter ? values.filter((item) => item.dynamicParameterId === valueFilter) : values;
  const filteredEntityValues = entityIdFilter
    ? entityValues.filter((item) => item.entityId?.toLowerCase().includes(entityIdFilter.toLowerCase()))
    : entityValues;

  function openParameterModal(row?: DynamicParameterItem) {
    setEditingParameter(row);
    const inputType = row?.inputType ?? 'SINGLE_LINE_STRING';
    parameterForm.setFieldsValue(row ?? { inputType });
    void loadInputTypeInfo(inputType);
    setParameterModalOpen(true);
  }

  async function loadInputTypeInfo(name?: string) {
    const info = await api.findAllowedInputType(name);
    setInputTypeInfo(info?.name ? info : undefined);
  }

  async function saveParameter(values: DynamicParameterItem) {
    const payload = { ...editingParameter, ...values };
    if (payload.id) {
      await api.updateDynamicParameter(payload);
    } else {
      await api.addDynamicParameter(payload);
    }
    message.success('动态参数已保存');
    setParameterModalOpen(false);
    await load();
  }

  async function deleteParameter(row: DynamicParameterItem) {
    if (!row.id) return;
    await api.deleteDynamicParameter(row.id);
    message.warning('动态参数已删除');
    await load();
  }

  function openValueModal(row?: DynamicParameterValueItem) {
    setEditingValue(row);
    valueForm.setFieldsValue(row ?? { dynamicParameterId: valueFilter ?? parameters[0]?.id });
    setValueModalOpen(true);
  }

  async function saveValue(values: DynamicParameterValueItem) {
    const payload = { ...editingValue, ...values };
    if (payload.id) {
      await api.updateDynamicParameterValue(payload);
    } else {
      await api.addDynamicParameterValue(payload);
    }
    message.success('参数值已保存');
    setValueModalOpen(false);
    await load();
  }

  async function deleteValue(row: DynamicParameterValueItem) {
    if (!row.id) return;
    await api.deleteDynamicParameterValue(row.id);
    message.warning('参数值已删除');
    await load();
  }

  function openEntityModal(row?: EntityDynamicParameterItem) {
    setEditingEntity(row);
    entityForm.setFieldsValue(row ?? { entityFullName: allowedEntities[0] ?? fallbackEntityName, dynamicParameterId: parameters[0]?.id });
    setEntityModalOpen(true);
  }

  async function saveEntity(values: EntityDynamicParameterItem) {
    const payload = { ...editingEntity, ...values };
    if (payload.id) {
      await api.updateEntityDynamicParameter(payload);
    } else {
      await api.addEntityDynamicParameter(payload);
    }
    message.success('实体参数已保存');
    setEntityModalOpen(false);
    await load();
  }

  async function deleteEntity(row: EntityDynamicParameterItem) {
    if (!row.id) return;
    await api.deleteEntityDynamicParameter(row.id);
    message.warning('实体参数已删除');
    await load();
  }

  function openEntityValueModal(row?: EntityDynamicParameterValueItem) {
    setEditingEntityValue(row);
    entityValueForm.setFieldsValue(row ?? { entityDynamicParameterId: entityParameters[0]?.id, entityId: entityIdFilter });
    setEntityValueModalOpen(true);
  }

  async function saveEntityValue(values: EntityDynamicParameterValueItem) {
    const payload = { ...editingEntityValue, ...values };
    if (payload.id) {
      await api.updateEntityDynamicParameterValue(payload);
    } else {
      await api.addEntityDynamicParameterValue(payload);
    }
    message.success('实体参数值已保存');
    setEntityValueModalOpen(false);
    await load();
  }

  async function deleteEntityValue(row: EntityDynamicParameterValueItem) {
    if (!row.id) return;
    await api.deleteEntityDynamicParameterValue(row.id);
    message.warning('实体参数值已删除');
    await load();
  }

  async function syncEntityValues() {
    await api.insertOrUpdateEntityDynamicParameterValues(entityValueInputItems(filteredEntityValues));
    message.success('当前实体参数值已批量提交');
    await load();
  }

  async function cleanEntityValues() {
    await Promise.all(entityValueInputItems(filteredEntityValues).map((item) => api.cleanEntityDynamicParameterValues({
      entityDynamicParameterId: item.entityDynamicParameterId,
      entityId: item.entityId,
    })));
    message.warning('筛选范围内的实体参数值已清理');
    await load();
  }

  const parameterColumns: ColumnsType<DynamicParameterItem> = [
    { title: '参数名', dataIndex: 'parameterName', width: 180 },
    { title: '显示名', dataIndex: 'displayName', width: 180 },
    {
      title: '输入类型',
      dataIndex: 'inputType',
      width: 160,
      render: (value?: string) => <Tag color="blue">{inputTypeLabel(value)}</Tag>,
    },
    { title: '权限名', dataIndex: 'permission', render: (value?: string) => value || '-' },
    {
      title: '操作',
      width: 210,
      render: (_, row) => (
        <Space>
          <Button
            size="small"
            onClick={() => {
              setValueFilter(row.id);
              setActiveKey('values');
            }}
          >
            参数值
          </Button>
          <Button size="small" icon={<EditOutlined />} onClick={() => openParameterModal(row)}>
            编辑
          </Button>
          <Popconfirm title="删除该动态参数?" onConfirm={() => void deleteParameter(row)}>
            <Button danger size="small" icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const valueColumns: ColumnsType<DynamicParameterValueItem> = [
    { title: '所属参数', dataIndex: 'parameterName', width: 220 },
    { title: '参数值', dataIndex: 'value' },
    {
      title: '操作',
      width: 150,
      render: (_, row) => (
        <Space>
          <Button size="small" icon={<EditOutlined />} onClick={() => openValueModal(row)}>
            编辑
          </Button>
          <Popconfirm title="删除该参数值?" onConfirm={() => void deleteValue(row)}>
            <Button danger size="small" icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const entityColumns: ColumnsType<EntityDynamicParameterItem> = [
    { title: '实体类型', dataIndex: 'entityFullName', width: 260 },
    { title: '参数名', dataIndex: 'parameterName', width: 180 },
    { title: '显示名', dataIndex: 'displayName' },
    {
      title: '操作',
      width: 160,
      render: (_, row) => (
        <Space>
          <Button size="small" icon={<EditOutlined />} onClick={() => openEntityModal(row)}>
            编辑
          </Button>
          <Popconfirm title="删除该实体参数?" onConfirm={() => void deleteEntity(row)}>
            <Button danger size="small" icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const entityValueColumns: ColumnsType<EntityDynamicParameterValueItem> = [
    { title: '实体类型', dataIndex: 'entityFullName', width: 220 },
    { title: '实体 ID', dataIndex: 'entityId', width: 260, ellipsis: true },
    { title: '参数名', dataIndex: 'parameterName', width: 180 },
    { title: '值', dataIndex: 'value' },
    {
      title: '操作',
      width: 160,
      render: (_, row) => (
        <Space>
          <Button size="small" icon={<EditOutlined />} onClick={() => openEntityValueModal(row)}>
            编辑
          </Button>
          <Popconfirm title="删除该实体参数值?" onConfirm={() => void deleteEntityValue(row)}>
            <Button danger size="small" icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div className="page-body">
      <PageTitle title="动态参数" description="维护平台动态参数、参数值、实体映射和实体参数值" />
      <Card>
        <Tabs
          activeKey={activeKey}
          onChange={setActiveKey}
          items={[
            {
              key: 'parameters',
              label: '动态参数',
              children: (
                <>
                  <Space style={{ marginBottom: 16 }}>
                    <Button type="primary" icon={<PlusOutlined />} onClick={() => openParameterModal()}>
                      新增动态参数
                    </Button>
                    <Button icon={<ReloadOutlined />} onClick={() => void load()}>
                      刷新
                    </Button>
                  </Space>
                  <Table rowKey="id" columns={parameterColumns} dataSource={parameters} loading={loading} />
                </>
              ),
            },
            {
              key: 'values',
              label: '参数值',
              children: (
                <>
                  <Space style={{ marginBottom: 16 }}>
                    <Select
                      allowClear
                      placeholder="筛选动态参数"
                      style={{ width: 260 }}
                      options={parameterOptions}
                      value={valueFilter}
                      onChange={setValueFilter}
                    />
                    <Button type="primary" icon={<PlusOutlined />} onClick={() => openValueModal()}>
                      新增参数值
                    </Button>
                    <Button icon={<ReloadOutlined />} onClick={() => void load()}>
                      刷新
                    </Button>
                  </Space>
                  <Table rowKey="id" columns={valueColumns} dataSource={filteredValues} loading={loading} />
                </>
              ),
            },
            {
              key: 'entityParameters',
              label: '实体参数',
              children: (
                <>
                  <Space style={{ marginBottom: 16 }}>
                    <Button type="primary" icon={<PlusOutlined />} onClick={() => openEntityModal()}>
                      新增实体参数
                    </Button>
                    <Button icon={<ReloadOutlined />} onClick={() => void load()}>
                      刷新
                    </Button>
                  </Space>
                  <Table rowKey="id" columns={entityColumns} dataSource={entityParameters} loading={loading} />
                </>
              ),
            },
            {
              key: 'entityValues',
              label: '实体参数值',
              children: (
                <>
                  <Space style={{ marginBottom: 16 }} wrap>
                    <Input.Search
                      allowClear
                      placeholder="按实体 ID 筛选"
                      style={{ width: 300 }}
                      onSearch={setEntityIdFilter}
                    />
                    <Button type="primary" icon={<PlusOutlined />} onClick={() => openEntityValueModal()}>
                      新增实体参数值
                    </Button>
                    <Button icon={<SaveOutlined />} disabled={!filteredEntityValues.length} onClick={() => void syncEntityValues()}>
                      批量提交
                    </Button>
                    <Popconfirm title="清理筛选范围内的实体参数值?" onConfirm={() => void cleanEntityValues()}>
                      <Button danger icon={<DeleteOutlined />} disabled={!entityIdFilter}>
                        清理筛选值
                      </Button>
                    </Popconfirm>
                    <Button icon={<ReloadOutlined />} onClick={() => void load()}>
                      刷新
                    </Button>
                  </Space>
                  <Table rowKey="id" columns={entityValueColumns} dataSource={filteredEntityValues} loading={loading} />
                </>
              ),
            },
          ]}
        />
      </Card>

      <Modal
        forceRender
        title={editingParameter ? '编辑动态参数' : '新增动态参数'}
        open={parameterModalOpen}
        onOk={() => parameterForm.submit()}
        onCancel={() => setParameterModalOpen(false)}
      >
        <Form form={parameterForm} layout="vertical" onFinish={saveParameter}>
          <Form.Item name="parameterName" label="参数名" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="displayName" label="显示名" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="inputType" label="输入类型" rules={[{ required: true }]}>
            <Select showSearch options={inputTypeOptions} onChange={(value) => void loadInputTypeInfo(value)} />
          </Form.Item>
          {inputTypeInfo ? (
            <Descriptions size="small" column={1} bordered>
              <Descriptions.Item label="类型名">{inputTypeInfo.name}</Descriptions.Item>
              <Descriptions.Item label="显示名">{inputTypeInfo.displayName ?? inputTypeLabel(inputTypeInfo.name)}</Descriptions.Item>
              <Descriptions.Item label="属性">{JSON.stringify(inputTypeInfo.attributes ?? {})}</Descriptions.Item>
            </Descriptions>
          ) : null}
          <Form.Item name="permission" label="权限名">
            <Input placeholder="可留空" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        forceRender
        title={editingValue ? '编辑参数值' : '新增参数值'}
        open={valueModalOpen}
        onOk={() => valueForm.submit()}
        onCancel={() => setValueModalOpen(false)}
      >
        <Form form={valueForm} layout="vertical" onFinish={saveValue}>
          <Form.Item name="dynamicParameterId" label="所属动态参数" rules={[{ required: true }]}>
            <Select options={parameterOptions} />
          </Form.Item>
          <Form.Item name="value" label="参数值" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        forceRender
        title={editingEntity ? '编辑实体参数' : '新增实体参数'}
        open={entityModalOpen}
        onOk={() => entityForm.submit()}
        onCancel={() => setEntityModalOpen(false)}
      >
        <Form form={entityForm} layout="vertical" onFinish={saveEntity}>
          <Form.Item name="entityFullName" label="实体类型" rules={[{ required: true }]}>
            <Select showSearch options={entityOptions} />
          </Form.Item>
          <Form.Item name="dynamicParameterId" label="动态参数" rules={[{ required: true }]}>
            <Select options={parameterOptions} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        forceRender
        title={editingEntityValue ? '编辑实体参数值' : '新增实体参数值'}
        open={entityValueModalOpen}
        onOk={() => entityValueForm.submit()}
        onCancel={() => setEntityValueModalOpen(false)}
      >
        <Form form={entityValueForm} layout="vertical" onFinish={saveEntityValue}>
          <Form.Item name="entityDynamicParameterId" label="实体参数" rules={[{ required: true }]}>
            <Select options={entityParameterOptions} />
          </Form.Item>
          <Form.Item name="entityId" label="实体 ID" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="value" label="值" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
