import { Button, Card, Form, Input, Modal, Space, Table, App as AntdApp } from 'antd';
import { useEffect, useState } from 'react';
import PageTitle from '../../components/PageTitle';
import { api } from '../../services/api';
import type { Laboratory } from '../../types/domain';

// Laboratory management mirrors the original business/lab route.
export default function LabPage() {
  const { message } = AntdApp.useApp();
  const [items, setItems] = useState<Laboratory[]>([]);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<Laboratory | undefined>();
  const [form] = Form.useForm<Laboratory>();

  async function load() {
    const data = await api.labs();
    setItems(data.list);
  }

  useEffect(() => {
    void load();
  }, []);

  function edit(row?: Laboratory) {
    setEditing(row);
    form.setFieldsValue(row ?? {});
    setOpen(true);
  }

  async function save() {
    await api.saveLab({ ...editing, ...(await form.validateFields()) });
    message.success('保存成功');
    setOpen(false);
    await load();
  }

  async function remove(row: Laboratory) {
    if (!row.id) return;
    await api.deleteLab(row.id);
    message.warning('删除成功');
    await load();
  }

  return (
    <div className="page-body">
      <PageTitle title="实验室管理" />
      <Card>
        <Button type="primary" onClick={() => edit()}>
          新建
        </Button>
        <Table
          rowKey="id"
          dataSource={items}
          style={{ marginTop: 16 }}
          columns={[
            { title: '简称', dataIndex: 'code' },
            { title: '名称', dataIndex: 'name' },
            { title: '负责人', dataIndex: 'leader' },
            { title: '联系方式', dataIndex: 'contactInfo' },
            { title: '地址', dataIndex: 'address' },
            {
              title: '操作',
              render: (_, row) => (
                <Space>
                  <Button type="link" onClick={() => edit(row)}>
                    编辑
                  </Button>
                  <Button type="link" danger onClick={() => remove(row)}>
                    删除
                  </Button>
                </Space>
              ),
            },
          ]}
        />
      </Card>
      <Modal title="实验室信息" open={open} onOk={save} onCancel={() => setOpen(false)}>
        <Form form={form} layout="vertical">
          <Form.Item name="code" label="简称" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="name" label="名称" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="leader" label="负责人">
            <Input />
          </Form.Item>
          <Form.Item name="contactInfo" label="联系方式">
            <Input />
          </Form.Item>
          <Form.Item name="address" label="地址">
            <Input />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
