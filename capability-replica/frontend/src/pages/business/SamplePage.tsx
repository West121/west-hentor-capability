import { App as AntdApp, Button, Card, Col, Form, Input, Modal, Popconfirm, Row, Select, Space, Table } from 'antd';
import { DeleteOutlined, EditOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { useEffect, useState } from 'react';
import PageTitle from '../../components/PageTitle';
import { api } from '../../services/api';
import type { OrganizationUnit, Sample, SampleType } from '../../types/domain';

// Sample page mirrors SampleTypeAppService and SampleAppService CRUD.
export default function SamplePage() {
  const { message } = AntdApp.useApp();
  const [typeForm] = Form.useForm<SampleType>();
  const [sampleForm] = Form.useForm<Sample>();
  const [types, setTypes] = useState<SampleType[]>([]);
  const [samples, setSamples] = useState<Sample[]>([]);
  const [orgs, setOrgs] = useState<OrganizationUnit[]>([]);
  const [selectedTypeId, setSelectedTypeId] = useState<string>();
  const [typeModalOpen, setTypeModalOpen] = useState(false);
  const [sampleModalOpen, setSampleModalOpen] = useState(false);
  const [saving, setSaving] = useState(false);

  async function load(nextTypeId = selectedTypeId) {
    const [typeData, sampleData, orgData] = await Promise.all([
      api.sampleTypes(),
      api.samples(nextTypeId),
      api.orgUnits(),
    ]);
    setTypes(typeData.items);
    setSamples(sampleData.items);
    setOrgs(orgData.items);
  }

  useEffect(() => {
    void load();
  }, []);

  async function openType(id?: string) {
    const output = await api.sampleTypeForEdit(id);
    setOrgs(output.orgList);
    typeForm.setFieldsValue(output.type);
    setTypeModalOpen(true);
  }

  async function saveType() {
    const values = await typeForm.validateFields();
    setSaving(true);
    try {
      await api.saveSampleType(values);
      message.success('样品类型已保存');
      setTypeModalOpen(false);
      await load();
    } finally {
      setSaving(false);
    }
  }

  async function removeType(id?: string) {
    if (!id) return;
    await api.deleteSampleType(id);
    message.success('样品类型已删除');
    const nextTypeId = selectedTypeId === id ? undefined : selectedTypeId;
    setSelectedTypeId(nextTypeId);
    await load(nextTypeId);
  }

  async function openSample(id?: string) {
    const sample = await api.sampleForEdit(id);
    sampleForm.setFieldsValue({ ...sample, typeId: sample.typeId ?? selectedTypeId });
    setSampleModalOpen(true);
  }

  async function saveSample() {
    const values = await sampleForm.validateFields();
    setSaving(true);
    try {
      await api.saveSample(values);
      message.success('样品已保存');
      setSampleModalOpen(false);
      await load();
    } finally {
      setSaving(false);
    }
  }

  async function removeSample(id?: string) {
    if (!id) return;
    await api.deleteSample(id);
    message.success('样品已删除');
    await load();
  }

  async function filterSamples(typeId?: string) {
    setSelectedTypeId(typeId);
    const data = await api.samples(typeId);
    setSamples(data.items);
  }

  return (
    <div className="page-body">
      <PageTitle title="样品管理" description="维护样品类型及样品名称" />
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={9}>
          <Card
            title="样品类型"
            extra={
              <Space>
                <Button icon={<ReloadOutlined />} onClick={() => void load()}>
                  刷新
                </Button>
                <Button type="primary" icon={<PlusOutlined />} onClick={() => void openType()}>
                  新增
                </Button>
              </Space>
            }
          >
            <Table<SampleType>
              rowKey="id"
              size="small"
              dataSource={types}
              pagination={false}
              columns={[
                { title: '名称', dataIndex: 'displayName' },
                { title: '业务线', dataIndex: 'orgName' },
                {
                  title: '操作',
                  width: 132,
                  render: (_, row) => (
                    <Space>
                      <Button size="small" icon={<EditOutlined />} onClick={() => void openType(row.id)} />
                      <Popconfirm title="删除样品类型？" onConfirm={() => void removeType(row.id)}>
                        <Button size="small" danger icon={<DeleteOutlined />} />
                      </Popconfirm>
                    </Space>
                  ),
                },
              ]}
            />
          </Card>
        </Col>
        <Col xs={24} lg={15}>
          <Card
            title="样品"
            extra={
              <Space wrap>
                <Select
                  allowClear
                  style={{ width: 220 }}
                  placeholder="按样品类型筛选"
                  value={selectedTypeId}
                  options={types.map((item) => ({ label: item.displayName, value: item.id }))}
                  onChange={(value) => void filterSamples(value)}
                />
                <Button type="primary" icon={<PlusOutlined />} onClick={() => void openSample()}>
                  新增
                </Button>
              </Space>
            }
          >
            <Table<Sample>
              rowKey="id"
              size="small"
              dataSource={samples}
              columns={[
                { title: '名称', dataIndex: 'displayName' },
                { title: '英文名称', dataIndex: 'engName' },
                { title: '别名', dataIndex: 'alias' },
                { title: '类型', dataIndex: 'typeName' },
                {
                  title: '操作',
                  width: 132,
                  render: (_, row) => (
                    <Space>
                      <Button size="small" icon={<EditOutlined />} onClick={() => void openSample(row.id)} />
                      <Popconfirm title="删除样品？" onConfirm={() => void removeSample(row.id)}>
                        <Button size="small" danger icon={<DeleteOutlined />} />
                      </Popconfirm>
                    </Space>
                  ),
                },
              ]}
            />
          </Card>
        </Col>
      </Row>
      <Modal title="样品类型" open={typeModalOpen} confirmLoading={saving} onOk={() => void saveType()} onCancel={() => setTypeModalOpen(false)}>
        <Form form={typeForm} layout="vertical">
          <Form.Item name="id" hidden>
            <Input />
          </Form.Item>
          <Form.Item name="displayName" label="名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="orgId" label="业务线" rules={[{ required: true, message: '请选择业务线' }]}>
            <Select options={orgs.map((item) => ({ label: item.displayName, value: item.id }))} />
          </Form.Item>
        </Form>
      </Modal>
      <Modal title="样品" open={sampleModalOpen} confirmLoading={saving} onOk={() => void saveSample()} onCancel={() => setSampleModalOpen(false)}>
        <Form form={sampleForm} layout="vertical">
          <Form.Item name="id" hidden>
            <Input />
          </Form.Item>
          <Form.Item name="displayName" label="名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="engName" label="英文名称">
            <Input />
          </Form.Item>
          <Form.Item name="alias" label="别名">
            <Input />
          </Form.Item>
          <Form.Item name="typeId" label="样品类型" rules={[{ required: true, message: '请选择样品类型' }]}>
            <Select options={types.map((item) => ({ label: item.displayName, value: item.id }))} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
