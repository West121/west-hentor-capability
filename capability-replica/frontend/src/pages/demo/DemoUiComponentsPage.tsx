import { useEffect, useState } from 'react';
import { App as AntdApp, Button, Card, Col, DatePicker, Form, Input, Row, Select, Space, Table, Typography, Upload } from 'antd';
import { ClockCircleOutlined, SendOutlined, UploadOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import PageTitle from '../../components/PageTitle';
import { api } from '../../services/api';
import type { NameValue, UploadFileOutput } from '../../types/domain';

// Demo UI page copied from the original DemoUiComponents service and MVC upload route.
export default function DemoUiComponentsPage() {
  const { message } = AntdApp.useApp();
  const [form] = Form.useForm<DemoForm>();
  const [countries, setCountries] = useState<NameValue[]>([]);
  const [selectedCountries, setSelectedCountries] = useState<NameValue[]>([]);
  const [dateResult, setDateResult] = useState<string>();
  const [dateTimeResult, setDateTimeResult] = useState<string>();
  const [rangeResult, setRangeResult] = useState<string>();
  const [valueResult, setValueResult] = useState<string>();
  const [uploaded, setUploaded] = useState<UploadFileOutput[]>([]);

  useEffect(() => {
    void api.demoCountries().then(setCountries);
    form.setFieldsValue({
      date: dayjs(),
      dateTime: dayjs(),
      range: [dayjs().subtract(7, 'day'), dayjs()],
      value: 'Capability Demo',
    });
  }, [form]);

  async function sendDates() {
    const values = await form.validateFields();
    const [date, dateTime, range] = await Promise.all([
      api.demoSendAndGetDate(values.date?.toISOString()),
      api.demoSendAndGetDateTime(values.dateTime?.toISOString()),
      api.demoSendAndGetDateRange(values.range?.[0]?.toISOString(), values.range?.[1]?.toISOString()),
    ]);
    setDateResult(date.dateString);
    setDateTimeResult(dateTime.dateString);
    setRangeResult(range.dateString);
  }

  async function sendCountries(values: string[]) {
    const selected = countries.filter((item) => values.includes(String(item.value)));
    const result = await api.demoSelectedCountries(selected);
    setSelectedCountries(result);
  }

  async function sendValue() {
    const values = await form.validateFields();
    const result = await api.demoSendAndGetValue(values.value);
    setValueResult(result.output);
  }

  async function uploadFile(file: File) {
    const result = await api.demoUploadFile(file);
    setUploaded((current) => [...result, ...current]);
    message.success('文件已上传');
  }

  return (
    <div className="page-body">
      <PageTitle title="示例组件" description="日期、选择、文本和文件上传组件接口" />
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={14}>
          <Card title="日期">
            <Form form={form} layout="vertical">
              <Row gutter={12}>
                <Col xs={24} md={8}>
                  <Form.Item name="date" label="日期">
                    <DatePicker style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
                <Col xs={24} md={8}>
                  <Form.Item name="dateTime" label="日期时间">
                    <DatePicker showTime style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
                <Col xs={24} md={8}>
                  <Form.Item name="range" label="日期范围">
                    <DatePicker.RangePicker style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
              </Row>
              <Button type="primary" icon={<ClockCircleOutlined />} onClick={() => void sendDates()}>
                回传日期
              </Button>
            </Form>
            <Space direction="vertical" style={{ marginTop: 16 }}>
              <Typography.Text>日期：{dateResult || '-'}</Typography.Text>
              <Typography.Text>日期时间：{dateTimeResult || '-'}</Typography.Text>
              <Typography.Text>范围：{rangeResult || '-'}</Typography.Text>
            </Space>
          </Card>
        </Col>
        <Col xs={24} lg={10}>
          <Card title="国家">
            <Select
              mode="multiple"
              style={{ width: '100%' }}
              options={countries.map((item) => ({ label: item.name, value: String(item.value) }))}
              onChange={(values) => void sendCountries(values)}
            />
            <Table<NameValue>
              style={{ marginTop: 16 }}
              rowKey="value"
              size="small"
              pagination={false}
              dataSource={selectedCountries}
              columns={[
                { title: 'Name', dataIndex: 'name' },
                { title: 'Value', dataIndex: 'value', width: 120 },
              ]}
            />
          </Card>
        </Col>
        <Col xs={24} lg={10}>
          <Card title="文本">
            <Form form={form} layout="vertical">
              <Form.Item name="value" label="输入值">
                <Input />
              </Form.Item>
              <Button icon={<SendOutlined />} onClick={() => void sendValue()}>
                回传文本
              </Button>
            </Form>
            <Typography.Text style={{ display: 'block', marginTop: 16 }}>结果：{valueResult || '-'}</Typography.Text>
          </Card>
        </Col>
        <Col xs={24} lg={14}>
          <Card title="文件">
            <Upload
              multiple
              showUploadList={false}
              beforeUpload={(file) => {
                void uploadFile(file);
                return false;
              }}
            >
              <Button icon={<UploadOutlined />}>上传文件</Button>
            </Upload>
            <Table<UploadFileOutput>
              style={{ marginTop: 16 }}
              rowKey="id"
              size="small"
              pagination={false}
              dataSource={uploaded}
              columns={[
                { title: 'Id', dataIndex: 'id', ellipsis: true },
                { title: 'FileName', dataIndex: 'fileName', width: 240 },
              ]}
            />
          </Card>
        </Col>
      </Row>
    </div>
  );
}

interface DemoForm {
  date?: dayjs.Dayjs;
  dateTime?: dayjs.Dayjs;
  range?: [dayjs.Dayjs, dayjs.Dayjs];
  value?: string;
}
