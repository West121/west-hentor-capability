import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  Form,
  Input,
  List,
  Progress,
  Result,
  Row,
  Segmented,
  Select,
  Space,
  Statistic,
  Steps,
  Switch,
  Table,
  Tag,
  Typography,
  Upload,
} from 'antd';
import type { TableProps } from 'antd';
import {
  CheckCircleOutlined,
  DownloadOutlined,
  FileExcelOutlined,
  FileZipOutlined,
  LockOutlined,
  PlusOutlined,
  PrinterOutlined,
  SaveOutlined,
  SearchOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import PageTitle from '../components/PageTitle';
import type { TemplateDemoRoute } from '../config/templateRoutes';

interface TemplateDemoPageProps {
  route: TemplateDemoRoute;
}

interface DemoRow {
  key: string;
  name: string;
  owner: string;
  status: string;
  updatedAt: string;
}

const tableRows: DemoRow[] = [
  { key: '10001', name: 'Capability Template', owner: 'Admin', status: '启用', updatedAt: '2021-07-28' },
  { key: '10002', name: 'NG-Alain Demo', owner: 'Operator', status: '待审核', updatedAt: '2021-07-20' },
  { key: '10003', name: 'Ant Design Copy', owner: 'System', status: '归档', updatedAt: '2021-06-25' },
];

const tableColumns: TableProps<DemoRow>['columns'] = [
  { title: '编号', dataIndex: 'key', width: 120 },
  { title: '名称', dataIndex: 'name' },
  { title: '负责人', dataIndex: 'owner', width: 140 },
  {
    title: '状态',
    dataIndex: 'status',
    width: 120,
    render: (status: string) => <Tag color={status === '启用' ? 'success' : status === '待审核' ? 'processing' : 'default'}>{status}</Tag>,
  },
  { title: '更新时间', dataIndex: 'updatedAt', width: 140 },
];

const colorTokens = [
  { name: 'Primary', value: '#1677ff' },
  { name: 'Success', value: '#52c41a' },
  { name: 'Warning', value: '#faad14' },
  { name: 'Error', value: '#ff4d4f' },
  { name: 'Text', value: '#1f2933' },
  { name: 'Border', value: '#d9d9d9' },
];

// Shared page for original template routes that were part of the Angular bundle.
export default function TemplateDemoPage({ route }: TemplateDemoPageProps) {
  return (
    <div className="page-body">
      <PageTitle title={route.title} description={`${route.group} · ${route.path}`} />
      <Alert type="info" showIcon message={route.description} />
      {renderTemplate(route)}
    </div>
  );
}

function renderTemplate(route: TemplateDemoRoute) {
  switch (route.kind) {
    case 'table':
      return renderTable(route);
    case 'cards':
      return renderCards();
    case 'form':
      return renderForm(route);
    case 'settings':
      return renderSettings();
    case 'profile':
      return renderProfile(route);
    case 'colors':
      return renderColors();
    case 'resultSuccess':
      return renderResult('success');
    case 'resultFail':
      return renderResult('error');
    case 'utility':
      return renderUtility(route);
    case 'guard':
      return renderGuard(route);
    case 'download':
      return renderDownload(route);
    default:
      return renderOverview(route);
  }
}

function renderOverview(route: TemplateDemoRoute) {
  return (
    <Card>
      <Descriptions column={{ xs: 1, md: 3 }} size="small">
        <Descriptions.Item label="Route">{route.path}</Descriptions.Item>
        <Descriptions.Item label="Module">{route.originalModule}</Descriptions.Item>
        <Descriptions.Item label="Type">{route.kind}</Descriptions.Item>
      </Descriptions>
      <List
        style={{ marginTop: 16 }}
        grid={{ gutter: 12, xs: 1, md: 3 }}
        dataSource={['表格', '表单', '结果页']}
        renderItem={(item) => (
          <List.Item>
            <div style={{ border: '1px solid #f0f0f0', borderRadius: 6, padding: 12 }}>
              <Statistic title={item} value={item.length * 12} suffix="%" />
            </div>
          </List.Item>
        )}
      />
    </Card>
  );
}

function renderTable(route: TemplateDemoRoute) {
  return (
    <Card
      title="数据列表"
      extra={
        <Space>
          <Button icon={<SearchOutlined />}>查询</Button>
          <Button type="primary" icon={<PlusOutlined />}>
            新增
          </Button>
        </Space>
      }
    >
      <Table<DemoRow>
        rowKey="key"
        size="middle"
        columns={tableColumns}
        dataSource={tableRows}
        pagination={{ pageSize: 5 }}
        summary={() => (
          <Table.Summary.Row>
            <Table.Summary.Cell index={0} colSpan={5}>
              <Typography.Text type="secondary">{route.title} 模板数据</Typography.Text>
            </Table.Summary.Cell>
          </Table.Summary.Row>
        )}
      />
    </Card>
  );
}

function renderCards() {
  return (
    <Row gutter={[16, 16]}>
      {['项目管理', '应用服务', '帮助主题'].map((title, index) => (
        <Col xs={24} md={8} key={title}>
          <Card title={title}>
            <Typography.Paragraph className="muted">模板项 {index + 1}</Typography.Paragraph>
            <Progress percent={60 + index * 10} size="small" />
          </Card>
        </Col>
      ))}
    </Row>
  );
}

function renderForm(route: TemplateDemoRoute) {
  return (
    <Card title="表单">
      <Form layout="vertical">
        <Row gutter={12}>
          <Col xs={24} md={12}>
            <Form.Item label="标题" name="title" initialValue={route.title}>
              <Input />
            </Form.Item>
          </Col>
          <Col xs={24} md={12}>
            <Form.Item label="类型" name="type" initialValue={route.kind}>
              <Select
                options={[
                  { label: '基础', value: 'basic' },
                  { label: '分步', value: 'step' },
                  { label: '高级', value: 'advanced' },
                ]}
              />
            </Form.Item>
          </Col>
        </Row>
        <Form.Item label="说明" name="description" initialValue={route.description}>
          <Input.TextArea rows={4} />
        </Form.Item>
        <Space>
          <Button type="primary" icon={<SaveOutlined />}>
            保存
          </Button>
          <Button>重置</Button>
        </Space>
      </Form>
    </Card>
  );
}

function renderSettings() {
  return (
    <Row gutter={[16, 16]}>
      <Col xs={24} lg={6}>
        <Card>
          <Segmented block options={['Base', 'Security', 'Binding', 'Notification']} />
        </Card>
      </Col>
      <Col xs={24} lg={18}>
        <Card title="账户设置">
          <Form layout="vertical">
            <Form.Item label="邮箱" name="email" initialValue="admin@capability.local">
              <Input />
            </Form.Item>
            <Form.Item label="昵称" name="name" initialValue="Capability Admin">
              <Input />
            </Form.Item>
            <Form.Item label="双因素认证" name="twoFactor" valuePropName="checked" initialValue>
              <Switch />
            </Form.Item>
            <Button type="primary" icon={<SaveOutlined />}>
              更新
            </Button>
          </Form>
        </Card>
      </Col>
    </Row>
  );
}

function renderProfile(route: TemplateDemoRoute) {
  return (
    <Row gutter={[16, 16]}>
      <Col xs={24} lg={8}>
        <Card>
          <Space direction="vertical" size="small">
            <Typography.Title level={4} style={{ margin: 0 }}>
              Capability User
            </Typography.Title>
            <Typography.Text className="muted">Template profile</Typography.Text>
            <Tag color="blue">{route.group}</Tag>
          </Space>
        </Card>
      </Col>
      <Col xs={24} lg={16}>
        <Card title="详情">
          <Steps
            current={1}
            items={[
              { title: '创建', icon: <CheckCircleOutlined /> },
              { title: '审核' },
              { title: '完成' },
            ]}
          />
          <Descriptions style={{ marginTop: 16 }} column={{ xs: 1, md: 2 }}>
            <Descriptions.Item label="单号">234231029431</Descriptions.Item>
            <Descriptions.Item label="模块">{route.originalModule}</Descriptions.Item>
            <Descriptions.Item label="路由">{route.path}</Descriptions.Item>
            <Descriptions.Item label="状态">处理中</Descriptions.Item>
          </Descriptions>
        </Card>
      </Col>
    </Row>
  );
}

function renderColors() {
  return (
    <Card title="颜色">
      <Row gutter={[12, 12]}>
        {colorTokens.map((color) => (
          <Col xs={12} md={8} lg={4} key={color.name}>
            <div
              style={{
                border: '1px solid #d9d9d9',
                borderRadius: 6,
                overflow: 'hidden',
                background: '#fff',
              }}
            >
              <div style={{ height: 56, background: color.value }} />
              <div style={{ padding: 8 }}>
                <Typography.Text strong>{color.name}</Typography.Text>
                <br />
                <Typography.Text className="muted">{color.value}</Typography.Text>
              </div>
            </div>
          </Col>
        ))}
      </Row>
    </Card>
  );
}

function renderResult(status: 'success' | 'error') {
  return (
    <Card>
      <Result
        status={status}
        title={status === 'success' ? '提交成功' : '提交失败'}
        subTitle={status === 'success' ? '模板操作已完成。' : '请核对表单内容后重新提交。'}
        extra={[
          <Button type="primary" key="back">
            返回列表
          </Button>,
          <Button key="detail">查看详情</Button>,
        ]}
      />
    </Card>
  );
}

function renderUtility(route: TemplateDemoRoute) {
  const isPrint = route.path.includes('print');
  return (
    <Card title={route.title}>
      <Space direction="vertical" style={{ width: '100%' }}>
        <Input.TextArea
          rows={5}
          defaultValue={isPrint ? '<h1>Title</h1><p>Capability print template</p>' : route.description}
        />
        <Space>
          <Button type="primary" icon={isPrint ? <PrinterOutlined /> : <SaveOutlined />}>
            {isPrint ? '打印预览' : '执行'}
          </Button>
          <Button>清空</Button>
        </Space>
      </Space>
    </Card>
  );
}

function renderGuard(route: TemplateDemoRoute) {
  return (
    <Card title={route.title}>
      <Space direction="vertical" size="middle">
        <Space wrap>
          <Button icon={<LockOutlined />}>设置管理员</Button>
          <Button>设置员工 1</Button>
          <Button>设置员工 2</Button>
        </Space>
        <Alert type="warning" showIcon message="ACL / Guard" description={route.description} />
      </Space>
    </Card>
  );
}

function renderDownload(route: TemplateDemoRoute) {
  const isZip = route.path.includes('zip');
  return (
    <Card title={route.title}>
      <Space wrap>
        <Button icon={isZip ? <FileZipOutlined /> : <FileExcelOutlined />}>生成文件</Button>
        <Upload beforeUpload={() => false} showUploadList={false}>
          <Button icon={<UploadOutlined />}>选择文件</Button>
        </Upload>
        <Button type="primary" icon={<DownloadOutlined />}>
          下载
        </Button>
      </Space>
    </Card>
  );
}
