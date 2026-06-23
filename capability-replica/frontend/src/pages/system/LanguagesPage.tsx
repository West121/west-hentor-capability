import { useEffect, useState } from 'react';
import { App as AntdApp, Button, Drawer, Form, Input, Modal, Select, Space, Switch, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { EditOutlined, FileTextOutlined, PlusOutlined } from '@ant-design/icons';
import PageTitle from '../../components/PageTitle';
import { api } from '../../services/api';
import type { ComboboxItem, LanguageItem, LanguageTextItem } from '../../types/domain';

function comboboxOption(item: ComboboxItem) {
  return { label: item.displayText, value: item.value };
}

// Language management copies AspNet Zero language and localization text workflows.
export default function LanguagesPage() {
  const { message, modal } = AntdApp.useApp();
  const [languageForm] = Form.useForm<LanguageItem>();
  const [textForm] = Form.useForm<LanguageTextItem>();
  const [languages, setLanguages] = useState<LanguageItem[]>([]);
  const [defaultLanguageName, setDefaultLanguageName] = useState('zh-Hans');
  const [texts, setTexts] = useState<LanguageTextItem[]>([]);
  const [selectedLanguage, setSelectedLanguage] = useState<LanguageItem>();
  const [editingLanguage, setEditingLanguage] = useState<LanguageItem>();
  const [editingText, setEditingText] = useState<LanguageTextItem>();
  const [languageNameItems, setLanguageNameItems] = useState<ComboboxItem[]>([]);
  const [flagItems, setFlagItems] = useState<ComboboxItem[]>([]);
  const [languageModalOpen, setLanguageModalOpen] = useState(false);
  const [textDrawerOpen, setTextDrawerOpen] = useState(false);
  const [textModalOpen, setTextModalOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [textFilter, setTextFilter] = useState('');

  async function loadLanguages() {
    setLoading(true);
    try {
      const data = await api.languages();
      setLanguages(data.languages);
      setDefaultLanguageName(data.defaultLanguageName);
    } finally {
      setLoading(false);
    }
  }

  async function loadTexts(languageName = selectedLanguage?.name, filter = textFilter) {
    if (!languageName) {
      return;
    }
    const data = await api.languageTexts({ languageName, filter, maxResultCount: 100 });
    setTexts(data.items);
  }

  useEffect(() => {
    void loadLanguages();
  }, []);

  async function openLanguageModal(row?: LanguageItem) {
    const edit = await api.languageForEdit(row?.id);
    const language = row?.id ? edit.language ?? row : undefined;
    setLanguageNameItems(edit.languageNames ?? []);
    setFlagItems(edit.flags ?? []);
    setEditingLanguage(language);
    languageForm.setFieldsValue(language ?? { name: '', displayName: '', icon: 'famfamfam-flags cn', isEnabled: true });
    setLanguageModalOpen(true);
  }

  async function saveLanguage(values: LanguageItem) {
    await api.saveLanguage({ ...editingLanguage, ...values });
    setLanguageModalOpen(false);
    await loadLanguages();
    message.success('语言已保存');
  }

  async function deleteLanguage(row: LanguageItem) {
    modal.confirm({
      title: `删除语言 ${row.displayName}`,
      content: '默认语言不能删除，删除后对应文本也会移除。',
      onOk: async () => {
        if (row.id) {
          await api.deleteLanguage(row.id);
          await loadLanguages();
          message.success('语言已删除');
        }
      },
    });
  }

  async function setDefault(row: LanguageItem) {
    await api.setDefaultLanguage(row.name);
    await loadLanguages();
    message.success('默认语言已更新');
  }

  async function openTexts(row: LanguageItem) {
    setSelectedLanguage(row);
    setTextFilter('');
    setTextDrawerOpen(true);
    await loadTexts(row.name, '');
  }

  function openTextModal(row: LanguageTextItem) {
    setEditingText(row);
    textForm.setFieldsValue(row);
    setTextModalOpen(true);
  }

  async function saveText(values: LanguageTextItem) {
    if (!editingText) {
      return;
    }
    await api.updateLanguageText({ ...editingText, ...values });
    setTextModalOpen(false);
    await loadTexts();
    message.success('语言文本已保存');
  }

  const languageColumns: ColumnsType<LanguageItem> = [
    { title: '语言代码', dataIndex: 'name', width: 140 },
    { title: '显示名称', dataIndex: 'displayName', width: 180 },
    { title: '图标', dataIndex: 'icon', width: 180 },
    {
      title: '默认',
      dataIndex: 'isDefault',
      width: 100,
      render: (_, row) =>
        row.name === defaultLanguageName || row.isDefault ? <Tag color="green">默认</Tag> : <Tag>普通</Tag>,
    },
    {
      title: '状态',
      dataIndex: 'isDisabled',
      width: 100,
      render: (disabled: boolean) => <Tag color={disabled ? 'red' : 'blue'}>{disabled ? '禁用' : '启用'}</Tag>,
    },
    { title: '创建时间', dataIndex: 'creationTime', width: 220 },
    {
      title: '操作',
      width: 320,
      render: (_, row) => (
        <Space>
          <Button size="small" icon={<FileTextOutlined />} onClick={() => void openTexts(row)}>
            文本
          </Button>
          <Button size="small" disabled={row.name === defaultLanguageName || row.isDefault} onClick={() => void setDefault(row)}>
            设为默认
          </Button>
          <Button size="small" icon={<EditOutlined />} onClick={() => void openLanguageModal(row)}>
            编辑
          </Button>
          <Button danger size="small" disabled={row.name === defaultLanguageName || row.isDefault} onClick={() => void deleteLanguage(row)}>
            删除
          </Button>
        </Space>
      ),
    },
  ];

  const textColumns: ColumnsType<LanguageTextItem> = [
    { title: '键', dataIndex: 'key', width: 180 },
    { title: '来源', dataIndex: 'sourceName', width: 160 },
    { title: '基准文本', dataIndex: 'baseValue', width: 240 },
    { title: '目标文本', dataIndex: 'targetValue' },
    {
      title: '操作',
      width: 100,
      render: (_, row) => (
        <Button size="small" onClick={() => openTextModal(row)}>
          编辑
        </Button>
      ),
    },
  ];

  const languageNameOptions = languageNameItems.map(comboboxOption);
  const flagOptions = flagItems.map(comboboxOption);

  return (
    <>
      <PageTitle title="语言管理" description="维护系统语言、默认语言和本地化文本" />
      <Space style={{ marginBottom: 16 }}>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => void openLanguageModal()}>
          新增语言
        </Button>
      </Space>
      <Table rowKey={(row) => String(row.id)} columns={languageColumns} dataSource={languages} loading={loading} />

      <Modal
        title={editingLanguage ? '编辑语言' : '新增语言'}
        open={languageModalOpen}
        onCancel={() => setLanguageModalOpen(false)}
        onOk={() => languageForm.submit()}
        destroyOnHidden
      >
        <Form form={languageForm} layout="vertical" onFinish={saveLanguage}>
          <Form.Item name="name" label="语言代码" rules={[{ required: true, max: 128 }]}>
            <Select showSearch optionFilterProp="label" options={languageNameOptions} placeholder="选择语言代码" />
          </Form.Item>
          <Form.Item name="icon" label="图标" rules={[{ max: 128 }]}>
            <Select showSearch optionFilterProp="label" options={flagOptions} placeholder="选择图标" />
          </Form.Item>
          <Form.Item name="isEnabled" label="是否启用" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title={`${selectedLanguage?.displayName ?? ''} 文本`}
        size="large"
        open={textDrawerOpen}
        onClose={() => setTextDrawerOpen(false)}
      >
        <Space style={{ marginBottom: 16 }}>
          <Input.Search
            allowClear
            placeholder="搜索键或文本"
            style={{ width: 320 }}
            value={textFilter}
            onChange={(event) => setTextFilter(event.target.value)}
            onSearch={(value) => void loadTexts(selectedLanguage?.name, value)}
          />
          <Button onClick={() => void loadTexts()}>刷新</Button>
        </Space>
        <Table rowKey={(row) => String(row.id)} columns={textColumns} dataSource={texts} pagination={false} />
      </Drawer>

      <Modal
        title="编辑语言文本"
        open={textModalOpen}
        onCancel={() => setTextModalOpen(false)}
        onOk={() => textForm.submit()}
        destroyOnHidden
      >
        <Form form={textForm} layout="vertical" onFinish={saveText}>
          <Form.Item name="key" label="键">
            <Input disabled maxLength={256} />
          </Form.Item>
          <Form.Item name="baseValue" label="基准文本">
            <Input disabled />
          </Form.Item>
          <Form.Item name="targetValue" label="目标文本" rules={[{ required: true }]}>
            <Input.TextArea autoSize={{ minRows: 3 }} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
