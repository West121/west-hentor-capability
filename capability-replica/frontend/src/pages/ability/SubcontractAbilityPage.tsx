import { Button, Form, Input, Space, Table, Upload, App as AntdApp } from 'antd';
import { UploadOutlined } from '@ant-design/icons';
import { useEffect, useState } from 'react';
import PageTitle from '../../components/PageTitle';
import { api } from '../../services/api';
import { buildSubcontractSaveInput } from './subcontractImport';
import { buildSubcontractSearchPayload, defaultSubcontractPageSize } from './subcontractSearch';
import type { SubcontractAbility } from '../../types/domain';

interface SubcontractAbilityPageProps {
  embedded?: boolean;
}

// Outsourcing tab copied from the original ability module.
export default function SubcontractAbilityPage({ embedded = false }: SubcontractAbilityPageProps) {
  const { message } = AntdApp.useApp();
  const [items, setItems] = useState<SubcontractAbility[]>([]);
  const [total, setTotal] = useState(0);
  const [searchValues, setSearchValues] = useState<Record<string, unknown>>({});
  const [pageState, setPageState] = useState({ current: 1, pageSize: defaultSubcontractPageSize });
  const [loading, setLoading] = useState(false);
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
      const data = await api.subcontractAbilities(buildSubcontractSearchPayload(values, current, pageSize));
      setItems(data.items);
      setTotal(data.totalCount);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  async function importExcel(file: File) {
    setLoading(true);
    try {
      const output = await api.uploadSubcontractAbility(file);
      await api.saveSubcontractExcel(buildSubcontractSaveInput(output));
      message.success('上传Excel成功');
      await load(searchValues, pageState.current, pageState.pageSize);
    } finally {
      setLoading(false);
    }
  }

  const content = (
    <section className={embedded ? 'subcontract-ability-panel subcontract-ability-panel-embedded' : 'subcontract-ability-panel'}>
      <Space className="subcontract-ability-actions" size={8}>
        <Upload
          accept=".xlsx"
          showUploadList={false}
          beforeUpload={(file) => {
            void importExcel(file);
            return false;
          }}
        >
          <Button size="small" icon={<UploadOutlined />}>
            上传excel
          </Button>
        </Upload>
      </Space>
      <Form
        form={form}
        className="subcontract-ability-search"
        layout="inline"
        onFinish={(values) => void load(values, 1, pageState.pageSize)}
      >
        <span className="subcontract-ability-search-label">关键字:</span>
        <Form.Item name="filter">
          <Input allowClear />
        </Form.Item>
        <Button size="small" type="primary" htmlType="submit">
          搜索
        </Button>
        <Button
          size="small"
          onClick={() => {
            form.resetFields();
            void load({}, 1, pageState.pageSize);
          }}
        >
          重置
        </Button>
      </Form>
      <Table
        className="subcontract-ability-table"
        rowKey="id"
        loading={loading}
        dataSource={items}
        pagination={{
          current: pageState.current,
          pageSize: pageState.pageSize,
          total,
          showSizeChanger: true,
          showTotal: (count) => `共 ${count} 条`,
          onChange: (current, pageSize) => void load(searchValues, current, pageSize),
        }}
        scroll={{ x: 1225 }}
        columns={[
          { title: '实验室名称', dataIndex: 'labName', width: 190 },
          { title: '联系方式', dataIndex: 'contactDetails', width: 235 },
          { title: '检测/校准项目或类别', dataIndex: 'testCategory', width: 410 },
          { title: 'CMA/CNAS No(截止日期)', dataIndex: 'cmaOrCnas', width: 130 },
          { title: '选定依据', dataIndex: 'gist', width: 100 },
          { title: '评估人', dataIndex: 'appraiser', width: 80 },
          { title: '评估结果', dataIndex: 'evaluationResult', width: 80 },
        ]}
      />
    </section>
  );

  if (embedded) {
    return content;
  }

  return (
    <div className="page-body subcontract-ability-page">
      <PageTitle title="分包能力" description="维护外部分包实验室能力、依据和评价结果" />
      {content}
    </div>
  );
}
