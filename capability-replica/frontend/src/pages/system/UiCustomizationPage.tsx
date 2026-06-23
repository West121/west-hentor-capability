import { useEffect, useMemo, useState } from 'react';
import { App as AntdApp, Button, Card, Col, Divider, Form, Row, Select, Space, Switch, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { ReloadOutlined, SaveOutlined, SkinOutlined, UndoOutlined } from '@ant-design/icons';
import PageTitle from '../../components/PageTitle';
import { api } from '../../services/api';
import type { ThemeSettingsItem } from '../../types/domain';

const layoutOptions = [
  { label: '自适应', value: 'fluid' },
  { label: '固定宽度', value: 'boxed' },
];

const skinOptions = [
  { label: '亮色', value: 'light' },
  { label: '深色', value: 'dark' },
];

const menuPositionOptions = [
  { label: '左侧菜单', value: 'left' },
  { label: '顶部菜单', value: 'top' },
];

const submenuOptions = [
  { label: '手风琴', value: 'accordion' },
  { label: '下拉', value: 'dropdown' },
];

const subHeaderOptions = [
  { label: '实色', value: 'solid' },
  { label: '透明', value: 'transparent' },
];

const minimizeOptions = [
  { label: '不缩小', value: 'none' },
  { label: '顶部栏', value: 'topbar' },
  { label: '菜单', value: 'menu' },
  { label: '顶部栏和菜单', value: 'all' },
];

// UI customization mirrors UiCustomizationSettingsAppService theme settings.
export default function UiCustomizationPage() {
  const { message } = AntdApp.useApp();
  const [form] = Form.useForm<ThemeSettingsItem>();
  const [settings, setSettings] = useState<ThemeSettingsItem[]>([]);
  const [selectedTheme, setSelectedTheme] = useState('default');
  const [loading, setLoading] = useState(false);

  async function load(nextTheme = selectedTheme) {
    setLoading(true);
    try {
      const data = await api.uiManagementSettings();
      setSettings(data);
      const activeTheme = data.find((item) => item.isActive)?.theme ?? nextTheme ?? data[0]?.theme ?? 'default';
      setSelectedTheme(activeTheme);
      const activeItem = data.find((item) => item.theme === activeTheme) ?? data[0];
      if (activeItem) {
        form.setFieldsValue(activeItem);
      }
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  const selected = useMemo(
    () => settings.find((item) => item.theme === selectedTheme) ?? settings[0],
    [selectedTheme, settings],
  );

  const themeOptions = useMemo(
    () => settings.map((item) => ({ label: item.theme, value: item.theme })),
    [settings],
  );

  function chooseTheme(theme: string) {
    setSelectedTheme(theme);
    const next = settings.find((item) => item.theme === theme);
    if (next) {
      form.setFieldsValue(next);
    }
  }

  async function applyTheme() {
    await api.changeThemeWithDefaultValues(selectedTheme);
    message.success('主题已切换为默认值');
    await load(selectedTheme);
  }

  async function saveUserSettings() {
    const values = await form.validateFields();
    await api.updateUiManagementSettings({ ...values, theme: selectedTheme });
    message.success('UI设置已保存');
    await load(selectedTheme);
  }

  async function saveDefaultSettings() {
    const values = await form.validateFields();
    await api.updateDefaultUiManagementSettings({ ...values, theme: selectedTheme });
    message.success('默认UI设置已更新');
    await load(selectedTheme);
  }

  async function restoreSystemDefault() {
    await api.useSystemDefaultSettings();
    message.warning('已恢复系统默认UI设置');
    await load('default');
  }

  const columns: ColumnsType<ThemeSettingsItem> = [
    {
      title: '主题',
      dataIndex: 'theme',
      width: 160,
      render: (value: string, row) => (
        <Space>
          <span>{value}</span>
          {row.isActive ? <Tag color="green">当前</Tag> : null}
        </Space>
      ),
    },
    { title: '布局', dataIndex: ['layout', 'layoutType'], width: 120 },
    { title: '菜单位置', dataIndex: ['menu', 'position'], width: 120 },
    { title: '菜单皮肤', dataIndex: ['menu', 'asideSkin'], width: 120 },
    {
      title: '操作',
      width: 120,
      render: (_, row) => (
        <Button size="small" onClick={() => chooseTheme(row.theme)}>
          编辑
        </Button>
      ),
    },
  ];

  return (
    <div className="page-body">
      <PageTitle title="UI定制" description="维护主题、布局、菜单、页头和页脚设置" />
      <Card>
        <Space style={{ marginBottom: 16 }} wrap>
          <Select style={{ width: 220 }} options={themeOptions} value={selectedTheme} onChange={chooseTheme} />
          <Button type="primary" icon={<SkinOutlined />} onClick={() => void applyTheme()}>
            应用主题默认值
          </Button>
          <Button icon={<SaveOutlined />} onClick={() => void saveUserSettings()}>
            保存当前设置
          </Button>
          <Button icon={<SaveOutlined />} onClick={() => void saveDefaultSettings()}>
            保存为默认设置
          </Button>
          <Button danger icon={<UndoOutlined />} onClick={() => void restoreSystemDefault()}>
            恢复系统默认
          </Button>
          <Button icon={<ReloadOutlined />} onClick={() => void load()}>
            刷新
          </Button>
        </Space>

        <Row gutter={24}>
          <Col xs={24} xl={10}>
            <Table
              rowKey="theme"
              columns={columns}
              dataSource={settings}
              loading={loading}
              pagination={false}
              onRow={(row) => ({ onClick: () => chooseTheme(row.theme) })}
            />
          </Col>
          <Col xs={24} xl={14}>
            <Form form={form} layout="vertical" initialValues={selected}>
              <Divider>布局</Divider>
              <Row gutter={16}>
                <Col xs={24} md={12}>
                  <Form.Item name={['layout', 'layoutType']} label="布局类型">
                    <Select options={layoutOptions} />
                  </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                  <Form.Item name={['subHeader', 'subheaderStyle']} label="子页头样式">
                    <Select options={subHeaderOptions} />
                  </Form.Item>
                </Col>
              </Row>

              <Divider>页头</Divider>
              <Row gutter={16}>
                <Col xs={24} md={12}>
                  <Form.Item name={['header', 'headerSkin']} label="页头皮肤">
                    <Select options={skinOptions} />
                  </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                  <Form.Item name={['header', 'minimizeDesktopHeaderType']} label="桌面缩小类型">
                    <Select options={minimizeOptions} />
                  </Form.Item>
                </Col>
                <Col xs={24} md={8}>
                  <Form.Item name={['header', 'desktopFixedHeader']} label="桌面固定页头" valuePropName="checked">
                    <Switch />
                  </Form.Item>
                </Col>
                <Col xs={24} md={8}>
                  <Form.Item name={['header', 'mobileFixedHeader']} label="移动固定页头" valuePropName="checked">
                    <Switch />
                  </Form.Item>
                </Col>
                <Col xs={24} md={8}>
                  <Form.Item name={['header', 'headerMenuArrows']} label="菜单箭头" valuePropName="checked">
                    <Switch />
                  </Form.Item>
                </Col>
              </Row>

              <Divider>菜单</Divider>
              <Row gutter={16}>
                <Col xs={24} md={12}>
                  <Form.Item name={['menu', 'position']} label="菜单位置">
                    <Select options={menuPositionOptions} />
                  </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                  <Form.Item name={['menu', 'asideSkin']} label="菜单皮肤">
                    <Select options={skinOptions} />
                  </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                  <Form.Item name={['menu', 'submenuToggle']} label="子菜单打开方式">
                    <Select options={submenuOptions} />
                  </Form.Item>
                </Col>
                <Col xs={24} md={6}>
                  <Form.Item name={['menu', 'fixedAside']} label="固定侧边栏" valuePropName="checked">
                    <Switch />
                  </Form.Item>
                </Col>
                <Col xs={24} md={6}>
                  <Form.Item name={['menu', 'allowAsideMinimizing']} label="允许折叠" valuePropName="checked">
                    <Switch />
                  </Form.Item>
                </Col>
                <Col xs={24} md={6}>
                  <Form.Item name={['menu', 'defaultMinimizedAside']} label="默认折叠" valuePropName="checked">
                    <Switch />
                  </Form.Item>
                </Col>
                <Col xs={24} md={6}>
                  <Form.Item name={['menu', 'searchActive']} label="菜单搜索" valuePropName="checked">
                    <Switch />
                  </Form.Item>
                </Col>
              </Row>

              <Divider>页脚</Divider>
              <Row gutter={16}>
                <Col xs={24} md={8}>
                  <Form.Item name={['subHeader', 'fixedSubHeader']} label="固定子页头" valuePropName="checked">
                    <Switch />
                  </Form.Item>
                </Col>
                <Col xs={24} md={8}>
                  <Form.Item name={['footer', 'fixedFooter']} label="固定页脚" valuePropName="checked">
                    <Switch />
                  </Form.Item>
                </Col>
              </Row>
            </Form>
          </Col>
        </Row>
      </Card>
    </div>
  );
}
