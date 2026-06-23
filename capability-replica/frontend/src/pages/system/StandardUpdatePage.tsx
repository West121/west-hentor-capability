import { useState } from 'react';
import { Alert, App as AntdApp, Button, Card, Space, Table, Upload } from 'antd';
import { CheckCircleOutlined, DownloadOutlined, UploadOutlined } from '@ant-design/icons';
import PageTitle from '../../components/PageTitle';
import { api } from '../../services/api';
import type { UpdateStandardNumber, UploadStandardOutput } from '../../types/domain';

// Standard update keeps the original upload preview and AppService apply split.
export default function StandardUpdatePage() {
  const { message } = AntdApp.useApp();
  const [uploaded, setUploaded] = useState<UploadStandardOutput>();
  const [loading, setLoading] = useState(false);

  const rows = uploaded?.items ?? [];

  async function upload(file: File) {
    setLoading(true);
    try {
      const output = await api.uploadStandardFile(file);
      setUploaded(output);
      message.success('解析完成');
    } finally {
      setLoading(false);
    }
  }

  async function applyUpdate() {
    if (!uploaded) return;
    setLoading(true);
    try {
      await api.uploadStandard(uploaded);
      message.success('标准更新已提交');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="page-body">
      <PageTitle title="标准方法更新" description="解析新版标准号并更新能力表" />
      <Card>
        <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
          <Space wrap>
            <Upload
              accept=".xlsx"
              showUploadList={false}
              beforeUpload={(file) => {
                void upload(file);
                return false;
              }}
            >
              <Button icon={<UploadOutlined />} loading={loading}>
                上传新版标准
              </Button>
            </Upload>
            <Button
              type="primary"
              icon={<CheckCircleOutlined />}
              disabled={!uploaded || rows.length === 0}
              loading={loading}
              onClick={applyUpdate}
            >
              确认更新
            </Button>
            {uploaded?.errorFile ? (
              <Button danger icon={<DownloadOutlined />} onClick={() => void api.downloadFile(uploaded.errorFile!)}>
                下载错误报告
              </Button>
            ) : null}
          </Space>
          <Alert
            type={uploaded ? 'info' : 'warning'}
            showIcon
            title={
              uploaded
                ? `文件：${uploaded.file.fileName}，解析 ${uploaded.totalCount ?? rows.length} 行`
                : '待上传'
            }
          />
          <Table<UpdateStandardNumber>
            rowKey={(row) => [row.old, standardNewValue(row), row.name, row.remark].filter(Boolean).join('|')}
            size="small"
            loading={loading}
            dataSource={rows}
            pagination={false}
            scroll={{ x: 980 }}
            columns={[
              { title: '原标准号', dataIndex: 'old', width: 160 },
              { title: '新标准号', dataIndex: 'new', width: 160, render: (_value, row) => standardNewValue(row) },
              { title: '标准名称', dataIndex: 'name', width: 220 },
              { title: '标准状态', dataIndex: 'statu', width: 120 },
              { title: '备注', dataIndex: 'remark', width: 180 },
            ]}
          />
        </Space>
      </Card>
    </div>
  );
}

function standardNewValue(row: UpdateStandardNumber) {
  return row['new'] ?? row.newValue;
}
