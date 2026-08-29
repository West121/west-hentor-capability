import {
  Alert,
  App as AntdApp,
  Button,
  Dropdown,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
  Upload,
} from 'antd';
import {
  DeleteOutlined,
  DownOutlined,
  DownloadOutlined,
  EditOutlined,
  HeartFilled,
  HeartOutlined,
  HistoryOutlined,
  SearchOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import type { MenuProps } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useEffect, useMemo, useState } from 'react';
import { api } from '../../services/api';
import { useAuthStore } from '../../store/authStore';
import AbilityDescriptionEditor from './AbilityDescriptionEditor';
import SubcontractAbilityPage from './SubcontractAbilityPage';
import { safeAbilityDescriptionHtml } from './abilityDescriptionHtml';
import { abilityTemplateInput } from './abilityTemplateRequest';
import {
  activeLabAbilities,
  buildAbilityExportPayload,
  buildAbilitySearchPayload,
  defaultAbilityPageSize,
  qualificationOptions,
} from './abilitySearch';
import type {
  Ability,
  AbilityHistoryItem,
  AbilityTableUploadOutput,
  ImportAbilityTableDto,
  LabAbility,
  Laboratory,
  MyOrgSetting,
  NameValue,
  OrganizationUnit,
} from '../../types/domain';

const abilityPropertyColumns: Record<string, ColumnsType<Ability>[number]> = {
  orgName: { title: '业务部门', dataIndex: 'orgName', width: 140 },
  typeName: { title: '类型', dataIndex: 'typeName', width: 135 },
  samplingName: { title: '样品名称', dataIndex: 'samplingName', width: 120 },
  productCode: { title: '产品代码', dataIndex: 'productCode', width: 120 },
  testItem: { title: '测试项目', dataIndex: 'testItem', width: 80 },
  testItemRemark: { title: '测试项目说明', dataIndex: 'testItemRemark', width: 160 },
  standardNo: { title: '标准号', dataIndex: 'standardNo', width: 135 },
  methodName: { title: '方法中文描述', dataIndex: 'methodName', width: 120 },
  methodRemark: { title: '方法说明', dataIndex: 'methodRemark', width: 160 },
  methodEngName: { title: '方法英文描述', dataIndex: 'methodEngName', width: 190 },
  cycleWorkingDay: { title: '检测周期/工作日', dataIndex: 'cycleWorkingDay', width: 108 },
  testTime: { title: '测试时间', dataIndex: 'testTime', width: 120 },
  massRequired: { title: '所需样品量(g)', dataIndex: 'massRequired', width: 112 },
  massRequiredRemark: { title: '样品量说明', dataIndex: 'massRequiredRemark', width: 160 },
  sizeRequired: { title: '样品粒度要求/mm', dataIndex: 'sizeRequired', width: 126 },
  sizeRequiredRemark: { title: '粒度说明', dataIndex: 'sizeRequiredRemark', width: 160 },
  detectionLimit: { title: '适用范围', dataIndex: 'detectionLimit', width: 155 },
  price: { title: '价格', dataIndex: 'price', width: 74 },
  priceRemark: { title: '价格说明', dataIndex: 'priceRemark', width: 160 },
  remark: { title: '备注', dataIndex: 'remark', width: 170 },
  standardNoSgs: { title: '标准编号SGS', dataIndex: 'standardNoSgs', width: 160 },
  standardNoSop: { title: '标准编号SOP', dataIndex: 'standardNoSop', width: 160 },
  standardNoOthers: { title: '标准编号OTHERS', dataIndex: 'standardNoOthers', width: 180 },
  standardNoDz: { title: '标准编号DZ', dataIndex: 'standardNoDz', width: 160 },
};

const defaultAbilityPropertyOrder = [
  'typeName',
  'samplingName',
  'productCode',
  'testItem',
  'standardNo',
  'methodName',
  'cycleWorkingDay',
  'massRequired',
  'detectionLimit',
];

const productionBusinessLineOrder = ['NF', 'SIR', 'CHEM', 'EMS', 'OGC', 'General & XRD', 'Lab Group'];

const abilityManagementColumnOrder = [
  'samplingName',
  'testItem',
  'price',
  'standardNo',
  'remark',
  'methodName',
  'standardNoSgs',
  'standardNoSop',
  'standardNoOthers',
  'standardNoDz',
  'methodEngName',
  'cycleWorkingDay',
  'massRequired',
  'sizeRequired',
];

const labGroupOnlyProperties = new Set([
  'standardNoSgs',
  'standardNoSop',
  'standardNoOthers',
  'standardNoDz',
]);

const defaultAbilityEditorPropertyOrder: Array<keyof Ability> = [
  'typeName',
  'samplingName',
  'productCode',
  'testItem',
  'testItemRemark',
  'standardNo',
  'methodEngName',
  'methodName',
  'methodRemark',
  'gbNo',
  'gbRemark',
  'isoNo',
  'isoRemark',
  'gbtNo',
  'gbtRemark',
  'astmNo',
  'astmRemark',
  'industryStandardNo',
  'industryStandardRemark',
  'otherNo',
  'otherRemark',
  'cycleWorkingDay',
  'testTime',
  'testTimeRemark',
  'massRequired',
  'massRequiredRemark',
  'sizeRequired',
  'sizeRequiredRemark',
  'detectionLimit',
  'price',
  'priceRemark',
  'remark',
];

const requiredAbilityProperties = new Set<keyof Ability>([
  'typeName',
  'samplingName',
  'testItem',
  'methodName',
  'methodEngName',
  'standardNo',
  'cycleWorkingDay',
  'massRequired',
  'sizeRequired',
  'detectionLimit',
  'price',
]);

function visibleAbilityProperties(setting: MyOrgSetting | undefined, fallback: readonly string[]) {
  const properties = setting?.propertyList?.length ? setting.propertyList : fallback;
  return setting?.orgName === 'Lab Group'
    ? properties
    : properties.filter((property) => !labGroupOnlyProperties.has(property));
}

// Ability management keeps the original ABP routes and grouped edit workflow.
export default function AbilityListPage() {
  const { message, modal } = AntdApp.useApp();
  const can = useAuthStore((state) => state.can);
  const [items, setItems] = useState<Ability[]>([]);
  const [total, setTotal] = useState(0);
  const [searchValues, setSearchValues] = useState<Record<string, unknown>>({});
  const [pageState, setPageState] = useState({ current: 1, pageSize: defaultAbilityPageSize });
  const [loading, setLoading] = useState(false);
  const [editing, setEditing] = useState<Ability | undefined>();
  const [open, setOpen] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [importRows, setImportRows] = useState<ImportAbilityTableDto[]>([]);
  const [importOutput, setImportOutput] = useState<AbilityTableUploadOutput>();
  const [labCodes, setLabCodes] = useState<string[]>([]);
  const [importSaving, setImportSaving] = useState(false);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyRows, setHistoryRows] = useState<AbilityHistoryItem[]>([]);
  const [historyAbility, setHistoryAbility] = useState<Ability>();
  const [abilityDescription, setAbilityDescription] = useState('');
  const [abilityDescriptionDraft, setAbilityDescriptionDraft] = useState('');
  const [abilityDescriptionEditing, setAbilityDescriptionEditing] = useState(false);
  const [labEditorVisible, setLabEditorVisible] = useState(false);
  const [orgs, setOrgs] = useState<OrganizationUnit[]>([]);
  const [labs, setLabs] = useState<Laboratory[]>([]);
  const [labAbilities, setLabAbilities] = useState<LabAbility[]>([]);
  const [orgSettings, setOrgSettings] = useState<MyOrgSetting[]>([]);
  const [currentSetting, setCurrentSetting] = useState<MyOrgSetting>();
  const [activeTab, setActiveTab] = useState<string>();
  const [modalTypeOptions, setModalTypeOptions] = useState<NameValue[]>([]);
  const [form] = Form.useForm<Ability>();
  const [searchForm] = Form.useForm();
  const modalOrgId = Form.useWatch('orgId', form);
  const canDeleteAll = can('Pages.AbilityManagement.Ability.DeleteAll');
  const canCreateAbility = can('Pages.AbilityManagement.Ability.Create');
  const canEditAbility = currentSetting?.isPublic
    ? can('Pages.AbilityManagement.Ability.PublicEdit')
    : can('Pages.AbilityManagement.Ability.Edit');
  const canDeleteAbility = can('Pages.AbilityManagement.Ability.Delete');
  const canHistoryAbility = can('Pages.AbilityManagement.Ability.History');
  const canEditDescription = can('Pages.AbilityManagement.EditDesc');
  const modalSetting = useMemo(() => {
    const selected = orgSettings.find((item) => String(item.orgId) === String(modalOrgId));
    return selected ?? currentSetting;
  }, [currentSetting, modalOrgId, orgSettings]);
  const editableProperties = useMemo(() => {
    return new Set<string>(visibleAbilityProperties(modalSetting, defaultAbilityEditorPropertyOrder));
  }, [modalSetting]);
  const modalTypeSelectOptions = useMemo(() => {
    const names = new Map<string, string>();
    modalTypeOptions.forEach((item) => {
      const name = String(item.name ?? '').trim();
      const key = name.toLowerCase();
      if (name && !names.has(key)) names.set(key, name);
    });
    return Array.from(names.values()).map((value) => ({ label: value, value }));
  }, [modalTypeOptions]);
  const tableColumns = useMemo<ColumnsType<Ability>>(() => {
    // Original UI uses GetMyOrgSetting.propertyList to decide visible business columns.
    const configuredPropertySet = new Set(
      visibleAbilityProperties(currentSetting, defaultAbilityPropertyOrder).filter(
        (property) => property !== 'orgName' && property !== 'labAbility',
      ),
    );
    const orderedProperties = [
      ...abilityManagementColumnOrder.filter((property) => configuredPropertySet.has(property)),
    ];
    const propertyColumns = orderedProperties
      .map((property) => abilityPropertyColumns[property])
      .filter((column): column is ColumnsType<Ability>[number] => Boolean(column));
    const trailingPropertyColumns = ['detectionLimit', 'typeName']
      .filter((property) => configuredPropertySet.has(property))
      .map((property) => abilityPropertyColumns[property])
      .filter((column): column is ColumnsType<Ability>[number] => Boolean(column));

    return [
      {
        title: '操作',
        fixed: 'left',
        width: 70,
        render: (_, row) => (
          <span className="ability-management-action-cell">
            <button
              aria-label={row.isCollected ? '取消收藏' : '收藏'}
              className="ability-management-favorite-button"
              title={row.isCollected ? '取消收藏' : '收藏'}
              type="button"
              onClick={(event) => {
                event.stopPropagation();
                void toggleFavorite(row);
              }}
            >
              {row.isCollected ? <HeartFilled /> : <HeartOutlined />}
            </button>
            <Dropdown
              menu={{
                items: abilityActionItems(),
                onClick: ({ key }) => void handleAbilityAction(String(key), row),
              }}
              trigger={['click']}
            >
              <button className="ability-management-action" type="button">
                操作 <DownOutlined />
              </button>
            </Dropdown>
          </span>
        ),
      },
      ...propertyColumns,
      {
        title: '实验室能力',
        dataIndex: 'labAbilities',
        width: 280,
        render: (values: Ability['labAbilities']) =>
          activeLabAbilities(values)
            .map((lab) => `${lab.code};`)
            .join(''),
      },
      ...trailingPropertyColumns,
    ];
  }, [canDeleteAbility, canEditAbility, canHistoryAbility, currentSetting]);

  const tableScrollX = useMemo(
    () =>
      tableColumns.reduce((total, column) => {
        const width = typeof column.width === 'number' ? column.width : 120;
        return total + width;
      }, 0),
    [tableColumns],
  );
  const importColumns = useMemo<ColumnsType<ImportAbilityTableDto>>(
    () => [
      {
        title: '状态',
        dataIndex: 'isExist',
        width: 90,
        render: (value: boolean, row) =>
          row.exception ? (
            <Tag color="red">错误</Tag>
          ) : (
            <Tag color={value ? 'orange' : 'green'}>{value ? '重复' : '新增'}</Tag>
          ),
      },
      { title: '行号', dataIndex: 'rowNumber', width: 70 },
      { title: '业务线', dataIndex: 'orgName', width: 120 },
      { title: '类型', dataIndex: 'typeName', width: 100 },
      { title: '样品名称', dataIndex: 'samplingName', width: 120 },
      { title: '测试项目', dataIndex: 'testItem', width: 160 },
      { title: '标准号', dataIndex: 'standardNo', width: 140 },
      ...labCodes.map<ColumnsType<ImportAbilityTableDto>[number]>((code) => ({
        title: `实验室:${code}`,
        width: 110,
        render: (_, row) => row.labData?.[code] || '-',
      })),
      { title: '异常', dataIndex: 'exception', width: 220 },
    ],
    [labCodes],
  );
  const importScrollX = useMemo(
    () =>
      importColumns.reduce((total, column) => {
        const width = typeof column.width === 'number' ? column.width : 120;
        return total + width;
      }, 0),
    [importColumns],
  );
  const canSaveImport = importRows.length > 0 && importRows.some((row) => !row.exception);
  const importNewCount = importRows.filter((row) => !row.exception && !row.isExist).length;
  const importDuplicateCount = importRows.filter((row) => !row.exception && row.isExist).length;

  async function load(
    values: Record<string, unknown> = searchValues,
    orgId = currentSetting?.orgId,
    current = pageState.current,
    pageSize = pageState.pageSize,
  ) {
    setLoading(true);
    setSearchValues(values);
    setPageState({ current, pageSize });
    try {
      const data = await api.abilities(buildAbilitySearchPayload(values, orgId, pageSize, (current - 1) * pageSize));
      setItems(data.items);
      setTotal(data.totalCount);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void bootstrap();
  }, []);

  useEffect(() => {
    if (!open) return;
    const orgId = typeof modalOrgId === 'number' ? modalOrgId : currentSetting?.orgId;
    if (!orgId) {
      setModalTypeOptions([]);
      return;
    }
    void loadModalTypeOptions(orgId);
  }, [currentSetting?.orgId, modalOrgId, open]);

  useEffect(() => {
    if (!open || !labEditorVisible || labAbilities.length === 0) return;
    window.requestAnimationFrame(() => {
      const body = document.querySelector<HTMLElement>('.ability-edit-modal .ant-modal-body');
      body?.scrollTo({ top: body.scrollHeight, behavior: 'smooth' });
    });
  }, [labAbilities.length, labEditorVisible, open]);

  async function bootstrap() {
    await Promise.all([loadLookups(), loadAbilityDescription()]);
    const settings = await api.myOrgSettings();
    const sortedSettings = sortOrgSettings(settings.items);
    setOrgSettings(sortedSettings);
    const first = sortedSettings[0];
    setCurrentSetting(first);
    setActiveTab(first ? String(first.orgId) : undefined);
    if (first?.orgId) {
      await loadModalTypeOptions(first.orgId);
    }
    await load({}, first?.orgId, 1, defaultAbilityPageSize);
  }

  async function loadAbilityDescription() {
    const description = canEditDescription
      ? (await api.hostAbilitySettings()).description ?? ''
      : (await api.session()).application?.settings?.['Ability.Description'] ?? '';
    setAbilityDescription(description);
    setAbilityDescriptionDraft(description);
  }

  async function loadLookups() {
    const [orgData, labData] = await Promise.all([api.orgUnits(), api.labs()]);
    setOrgs(orgData.items);
    setLabs(labData.list);
  }

  async function loadModalTypeOptions(orgId: number) {
    const data = await api.orgTypeList(orgId);
    setModalTypeOptions(data.items);
  }

  async function switchOrg(orgId: string) {
    const next = orgSettings.find((item) => String(item.orgId) === orgId);
    setCurrentSetting(next);
    setActiveTab(orgId);
    searchForm.resetFields();
    if (next?.orgId) {
      await loadModalTypeOptions(next.orgId);
    } else {
      setModalTypeOptions([]);
    }
    await load({}, next?.orgId, 1, pageState.pageSize);
  }

  async function edit(row?: Ability) {
    const data = await api.abilityForEdit(row?.id);
    const ability = data.abilityDto ?? undefined;
    setEditing(ability);
    setOrgs(data.orgList);
    setLabs(data.labList);
    setLabAbilities(labAbilityCards(ability?.labAbilities, data.labList));
    setLabEditorVisible(false);
    form.resetFields();
    form.setFieldsValue(ability ?? { orgId: currentSetting?.orgId, orgName: currentSetting?.orgName });
    if (ability?.orgId ?? currentSetting?.orgId) {
      await loadModalTypeOptions((ability?.orgId ?? currentSetting?.orgId)!);
    }
    setOpen(true);
  }

  async function save() {
    let values: Ability;
    try {
      values = await form.validateFields();
    } catch (error) {
      if (isFormValidationError(error)) return;
      message.error(error instanceof Error ? error.message : '表单校验失败');
      return;
    }

    try {
      const org = orgs.find((item) => item.id === values.orgId);
      const payload: Ability = {
        ...editing,
        ...values,
        orgName: org?.displayName ?? values.orgName,
        labAbilities: labAbilities.filter((item) => item.code.trim()),
      };
      if (editing?.id) {
        await api.updateAbility(payload);
      } else {
        await api.createAbility(payload);
      }
      message.success('保存成功');
      setOpen(false);
      await load(searchValues, currentSetting?.orgId, pageState.current, pageState.pageSize);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败');
    }
  }

  async function remove(row: Ability) {
    if (!row.id) return;
    await api.deleteAbility(row.id);
    message.warning('删除成功');
    const nextPage = items.length === 1 && pageState.current > 1 ? pageState.current - 1 : pageState.current;
    await load(searchValues, currentSetting?.orgId, nextPage, pageState.pageSize);
  }

  function confirmRemove(row: Ability) {
    modal.confirm({
      title: '确定删除吗?',
      content: `${row.samplingName ?? ''}${row.testItem ? ` / ${row.testItem}` : ''}`,
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: () => remove(row),
    });
  }

  function deleteAllByOrg() {
    if (!currentSetting?.orgName) return;
    modal.confirm({
      title: '删除全部',
      content: `${currentSetting.orgName}下数据将会被全部删除，请确认是否删除`,
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
        onOk: async () => {
          await api.deleteAllAbilities(currentSetting.orgName);
          message.success('删除全部成功');
          await load({}, currentSetting.orgId, 1, pageState.pageSize);
        },
      });
  }

  async function favorite(row: Ability) {
    if (!row.id) return;
    await api.addFavoriteItem(undefined, row.id);
    setRowCollected(row.id, true);
    message.success('收藏成功');
  }

  async function removeFavorite(row: Ability) {
    if (!row.id) return;
    await api.removeFavoriteItem(row.id);
    setRowCollected(row.id, false);
    message.warning('已取消收藏');
  }

  function setRowCollected(id: string, isCollected: boolean) {
    setItems((current) => current.map((item) => (item.id === id ? { ...item, isCollected } : item)));
  }

  async function showHistory(row: Ability) {
    if (!row.id) return;
    setHistoryAbility(row);
    setHistoryOpen(true);
    setHistoryLoading(true);
    try {
      setHistoryRows(await api.queryAbilityHistory(row.id));
    } finally {
      setHistoryLoading(false);
    }
  }

  async function toggleFavorite(row: Ability) {
    if (row.isCollected) {
      await removeFavorite(row);
    } else {
      await favorite(row);
    }
  }

  function abilityActionItems(): MenuProps['items'] {
    const actions: MenuProps['items'] = [];
    if (canEditAbility) {
      actions.push({ key: 'edit', icon: <EditOutlined />, label: '编辑' });
    }
    if (canHistoryAbility) {
      actions.push({ key: 'history', icon: <HistoryOutlined />, label: '历史' });
    }
    if (canDeleteAbility) {
      actions.push({ type: 'divider' }, { key: 'delete', danger: true, icon: <DeleteOutlined />, label: '删除' });
    }
    return actions;
  }

  async function handleAbilityAction(key: string, row: Ability) {
    if (key === 'edit') {
      await edit(row);
      return;
    }
    if (key === 'history') {
      await showHistory(row);
      return;
    }
    if (key === 'delete') {
      confirmRemove(row);
    }
  }

  async function downloadTemplate() {
    await api.downloadFile(await api.abilityTemplate(abilityTemplateInput(currentSetting?.orgId)));
  }

  async function exportExcel() {
    await api.downloadFile(await api.exportAbilities(buildAbilityExportPayload(searchValues, currentSetting?.orgId)));
  }

  async function importExcel(file: File) {
    const output = await api.uploadAbilityTable(file, currentSetting?.orgId);
    setImportOutput(output);
    setImportRows(output.abilityTableList);
    setLabCodes(output.labCodeList);
    setImportOpen(true);
    if (output.errorCount) {
      message.warning(`已解析 ${output.totalCount ?? output.abilityTableList.length} 行，发现 ${output.errorCount} 行异常`);
    } else {
      message.success(`已解析 ${output.totalCount ?? output.abilityTableList.length} 行`);
    }
  }

  async function saveImportRows(onlySaveNew: boolean) {
    setImportSaving(true);
    try {
      await api.saveAbilityExcel(importRows, onlySaveNew);
      message.success('保存成功');
      setImportOpen(false);
      await load(searchValues, currentSetting?.orgId, pageState.current, pageState.pageSize);
    } finally {
      setImportSaving(false);
    }
  }

  async function saveAbilityDescription() {
    if (!canEditDescription) return;
    await api.updateHostAbilitySettings({ description: abilityDescriptionDraft });
    const description = abilityDescriptionDraft;
    setAbilityDescription(description);
    setAbilityDescriptionDraft(description);
    setAbilityDescriptionEditing(false);
    message.success('保存成功');
  }

  function cancelAbilityDescriptionEdit() {
    setAbilityDescriptionDraft(abilityDescription);
    setAbilityDescriptionEditing(false);
  }

  function updateLab(index: number, field: keyof LabAbility, value: boolean) {
    setLabAbilities((current) =>
      current.map((item, itemIndex) => (itemIndex === index ? { ...item, [field]: value } : item)),
    );
  }

  function addLabAbility() {
    setLabEditorVisible(true);
    setLabAbilities((current) => [...current, { code: '', hasCnas: false, hasCma: false, isAbility: false }]);
  }

  function removeLabAbility(index: number) {
    setLabAbilities((current) => current.filter((_, itemIndex) => itemIndex !== index));
  }

  function updateLabSelection(index: number, value?: string) {
    const lab = labs.find((item) => (item.id ?? item.code) === value || item.code === value);
    setLabAbilities((current) =>
      current.map((item, itemIndex) =>
        itemIndex === index
          ? {
              ...item,
              labId: lab?.id,
              code: lab?.code ?? '',
              hasCnas: item.hasCnas || lab?.hasCnas || false,
              hasCma: item.hasCma || lab?.hasCms || false,
            }
          : item,
      ),
    );
  }

  function isEditableProperty(name: keyof Ability) {
    return editableProperties.has(name);
  }

  function abilityFieldRules(name: keyof Ability) {
    return requiredAbilityProperties.has(name) ? [{ required: true, message: '不能为空' }] : undefined;
  }

  const isInstructionTab = activeTab === 'instruction';
  const isOutsourcingTab = activeTab === 'outsourcing';

  return (
    <div className="ability-management-page">
      <Tabs
        className="ability-management-tabs"
        activeKey={activeTab ?? (currentSetting ? String(currentSetting.orgId) : undefined)}
        onChange={(key) => {
          if (key === 'instruction') {
            setActiveTab(key);
            setAbilityDescriptionEditing(false);
            return;
          }
          if (key === 'outsourcing') {
            setActiveTab(key);
            setAbilityDescriptionEditing(false);
            return;
          }
          void switchOrg(key);
        }}
        items={[
          ...orgSettings.map((item) => ({ key: String(item.orgId), label: item.orgName })),
          { key: 'instruction', label: 'Instruction' },
          { key: 'outsourcing', label: 'Outsourcing' },
        ]}
      />
      {isInstructionTab ? (
        <section className="ability-management-instruction">
          {canEditDescription ? (
            <div className="ability-management-instruction-actions">
              <Space size={8}>
                {abilityDescriptionEditing ? (
                  <>
                    <Button type="primary" size="small" onClick={() => void saveAbilityDescription()}>
                      保存
                    </Button>
                    <Button size="small" onClick={cancelAbilityDescriptionEdit}>
                      取消
                    </Button>
                  </>
                ) : (
                  <Button size="small" onClick={() => setAbilityDescriptionEditing(true)}>
                    编辑
                  </Button>
                )}
              </Space>
            </div>
          ) : null}
          {abilityDescriptionEditing ? (
            <AbilityDescriptionEditor value={abilityDescriptionDraft} onChange={setAbilityDescriptionDraft} />
          ) : (
            <div
              className="ability-description-content ability-management-instruction-content"
              dangerouslySetInnerHTML={{ __html: safeAbilityDescriptionHtml(abilityDescription) }}
            />
          )}
        </section>
      ) : isOutsourcingTab ? (
        <SubcontractAbilityPage embedded />
      ) : (
        <>
          <div className="ability-management-toolbar">
            <Space size={4} wrap>
              {canCreateAbility ? (
                <Button type="primary" size="small" onClick={() => void edit()}>
                  新建
                </Button>
              ) : null}
              <Upload
                accept=".xlsx"
                showUploadList={false}
                beforeUpload={(file) => {
                  void importExcel(file);
                  return false;
                }}
              >
                <Button size="small" icon={<UploadOutlined />}>
                  导入
                </Button>
              </Upload>
              <Button size="small" icon={<DownloadOutlined />} onClick={exportExcel}>
                导出Excel
              </Button>
              <Button size="small" icon={<DownloadOutlined />} onClick={downloadTemplate}>
                导出Excel模板
              </Button>
              {canDeleteAll ? (
                <Button size="small" danger disabled={!currentSetting?.orgName} onClick={deleteAllByOrg}>
                  全部删除
                </Button>
              ) : null}
            </Space>
          </div>
          <Form
            form={searchForm}
            className="ability-management-search"
            layout="inline"
            initialValues={{ ability: '无' }}
            onFinish={(values) => void load(values, currentSetting?.orgId, 1, pageState.pageSize)}
          >
            <Form.Item name="typeName">
              <Select
                allowClear
                showSearch
                placeholder="样品类型"
                optionFilterProp="label"
                options={modalTypeSelectOptions}
              />
            </Form.Item>
            <Form.Item name="samplingName">
              <Input allowClear placeholder="样品名称" />
            </Form.Item>
            <Form.Item name="testItem">
              <Input allowClear placeholder="测试项目" />
            </Form.Item>
            <Form.Item name="standardNo">
              <Input allowClear placeholder="标准号" />
            </Form.Item>
            <Form.Item name="methodName">
              <Input allowClear placeholder="方法中文描述" />
            </Form.Item>
            <Form.Item name="methodEngName">
              <Input allowClear placeholder="方法英文描述" />
            </Form.Item>
            <Form.Item name="labAbility">
              <Select
                allowClear
                showSearch
                placeholder="实验室"
                options={labs.map((lab) => ({ label: lab.code ?? lab.name, value: lab.code }))}
              />
            </Form.Item>
            <Form.Item name="ability">
              <Select options={qualificationOptions} />
            </Form.Item>
            <Button htmlType="submit" type="primary" className="ability-management-search-button">
              搜索
            </Button>
            <Button
              className="ability-management-reset-button"
              onClick={() => {
                searchForm.resetFields();
                void load({}, currentSetting?.orgId, 1, pageState.pageSize);
              }}
            >
              重置
            </Button>
          </Form>
          <Table
            className="ability-management-table"
            rowKey="id"
            loading={loading}
            dataSource={items}
            pagination={{
              current: pageState.current,
              pageSize: pageState.pageSize,
              total,
              showSizeChanger: true,
              showTotal: (count) => `共 ${count} 项`,
              onChange: (current, pageSize) => void load(searchValues, currentSetting?.orgId, current, pageSize),
            }}
            scroll={{ x: tableScrollX }}
            columns={tableColumns}
          />
        </>
      )}
      <Modal
        forceRender
        className="ability-edit-modal"
        title={editing ? '编辑 信息' : '创建 信息'}
        open={open}
        onOk={save}
        onCancel={() => setOpen(false)}
        okText="保存"
        cancelText="关闭"
        width={900}
        style={{ top: 2 }}
        styles={{
          body: {
            maxHeight: 'calc(100vh - 176px)',
            overflowX: 'hidden',
            overflowY: 'auto',
          },
        }}
      >
        <Form
          form={form}
          className="ability-edit-form"
          layout="horizontal"
          labelCol={{ flex: '152px' }}
          wrapperCol={{ flex: '1 1 auto' }}
        >
          <Form.Item name="orgId" hidden>
            <Input />
          </Form.Item>
          <Form.Item name="orgName" hidden>
            <Input />
          </Form.Item>
          {isEditableProperty('typeName') ? (
            <Form.Item name="typeName" label="样品类型" rules={abilityFieldRules('typeName')}>
              <Select
                allowClear
                showSearch
                placeholder="请选择样品类型"
                optionFilterProp="label"
                options={modalTypeSelectOptions}
                suffixIcon={<SearchOutlined />}
              />
            </Form.Item>
          ) : null}
          {isEditableProperty('samplingName') ? (
            <Form.Item name="samplingName" label="样品名称" rules={abilityFieldRules('samplingName')}>
              <Input />
            </Form.Item>
          ) : null}
          {isEditableProperty('testItem') ? (
            <Form.Item name="testItem" label="测试项目" rules={abilityFieldRules('testItem')}>
              <Input />
            </Form.Item>
          ) : null}
          {isEditableProperty('price') ? (
            <Form.Item name="price" label="价格" rules={abilityFieldRules('price')}>
              <Input />
            </Form.Item>
          ) : null}
          {isEditableProperty('standardNo') ? (
            <Form.Item name="standardNo" label="标准号" rules={abilityFieldRules('standardNo')}>
              <Input />
            </Form.Item>
          ) : null}
          {isEditableProperty('methodName') ? (
            <Form.Item name="methodName" label="方法中文描述" rules={abilityFieldRules('methodName')}>
              <Input />
            </Form.Item>
          ) : null}
          {isEditableProperty('standardNoSgs') ? (
            <Form.Item name="standardNoSgs" label="标准编号SGS">
              <Input />
            </Form.Item>
          ) : null}
          {isEditableProperty('standardNoSop') ? (
            <Form.Item name="standardNoSop" label="标准编号SOP">
              <Input />
            </Form.Item>
          ) : null}
          {isEditableProperty('standardNoOthers') ? (
            <Form.Item name="standardNoOthers" label="标准编号OTHERS">
              <Input />
            </Form.Item>
          ) : null}
          {isEditableProperty('standardNoDz') ? (
            <Form.Item name="standardNoDz" label="标准编号DZ">
              <Input />
            </Form.Item>
          ) : null}
          {isEditableProperty('methodEngName') ? (
            <Form.Item name="methodEngName" label="方法英文描述" rules={abilityFieldRules('methodEngName')}>
              <Input />
            </Form.Item>
          ) : null}
          {isEditableProperty('cycleWorkingDay') ? (
            <Form.Item name="cycleWorkingDay" label="检测周期/工作日" rules={abilityFieldRules('cycleWorkingDay')}>
              <Input />
            </Form.Item>
          ) : null}
          {isEditableProperty('massRequired') ? (
            <Form.Item name="massRequired" label="所需样品量(g)" rules={abilityFieldRules('massRequired')}>
              <Input />
            </Form.Item>
          ) : null}
          {isEditableProperty('sizeRequired') ? (
            <Form.Item name="sizeRequired" label="样品粒度要求/mm" rules={abilityFieldRules('sizeRequired')}>
              <Input />
            </Form.Item>
          ) : null}
          {isEditableProperty('detectionLimit') ? (
            <Form.Item name="detectionLimit" label="适用范围" rules={abilityFieldRules('detectionLimit')}>
              <Input.TextArea rows={2} />
            </Form.Item>
          ) : null}
          {isEditableProperty('remark') ? (
            <Form.Item name="remark" label="备注">
              <Input.TextArea rows={2} />
            </Form.Item>
          ) : null}
          <Form.Item label="实验室能力" className="ability-edit-lab-item">
            <Button size="small" onClick={addLabAbility}>
              添加
            </Button>
            {labEditorVisible ? (
              <div className="ability-edit-lab-cards">
                {labAbilities.map((labAbility, index) => (
                  <div className="ability-edit-lab-card" key={`${(labAbility.labId ?? labAbility.code) || 'new'}-${index}`}>
                    <button
                      aria-label="删除实验室能力"
                      className="ability-edit-lab-delete"
                      type="button"
                      onClick={() => removeLabAbility(index)}
                    >
                      <DeleteOutlined />
                    </button>
                    <div className="ability-edit-lab-row">
                      <span className="ability-edit-lab-label">
                        <span className="ability-edit-required">*</span> 实验... :
                      </span>
                      <Select
                        allowClear
                        showSearch
                        value={(labAbility.labId ?? labAbility.code) || undefined}
                        optionFilterProp="label"
                        options={labs.map((lab) => ({
                          label: lab.name ? `${lab.code} ${lab.name}` : lab.code,
                          value: lab.id ?? lab.code,
                        }))}
                        onChange={(value) => updateLabSelection(index, value)}
                      />
                    </div>
                    <div className="ability-edit-lab-row">
                      <span className="ability-edit-lab-label">检测... :</span>
                      <Switch checked={labAbility.isAbility} onChange={(value) => updateLab(index, 'isAbility', value)} />
                    </div>
                    <div className="ability-edit-lab-row">
                      <span className="ability-edit-lab-label">CNAS:</span>
                      <Switch checked={labAbility.hasCnas} onChange={(value) => updateLab(index, 'hasCnas', value)} />
                    </div>
                    <div className="ability-edit-lab-row">
                      <span className="ability-edit-lab-label">CMA:</span>
                      <Switch checked={labAbility.hasCma} onChange={(value) => updateLab(index, 'hasCma', value)} />
                    </div>
                  </div>
                ))}
              </div>
            ) : null}
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        title={`${historyAbility?.samplingName ?? ''}${historyAbility?.testItem ? ` / ${historyAbility.testItem}` : ''} 变更历史`}
        open={historyOpen}
        onCancel={() => setHistoryOpen(false)}
        footer={<Button onClick={() => setHistoryOpen(false)}>关闭</Button>}
        width={900}
      >
        <Table
          size="small"
          loading={historyLoading}
          rowKey={(row) => String(row.id ?? `${row.changeTime}-${row.displayName}`)}
          dataSource={historyRows}
          pagination={false}
          columns={[
            {
              title: '类型',
              dataIndex: 'changeType',
              width: 90,
              render: (value: string) => <Tag color={historyTypeColor(value)}>{historyTypeLabel(value)}</Tag>,
            },
            { title: '时间', dataIndex: 'changeTime', width: 180 },
            { title: '操作人', dataIndex: 'user', width: 120 },
            { title: '属性', dataIndex: 'displayName', width: 120 },
            { title: '更新前', dataIndex: 'originalValue', ellipsis: true },
            { title: '更新后', dataIndex: 'newValue', ellipsis: true },
          ]}
        />
      </Modal>
      <Modal
        title="Excel 导入预览"
        open={importOpen}
        onCancel={() => setImportOpen(false)}
        footer={
          <Space wrap>
            <Button onClick={() => setImportOpen(false)}>关闭</Button>
            <Popconfirm
              title="只会保存新数据，重复数据不更新，确认保存吗？"
              placement="top"
              onConfirm={() => void saveImportRows(true)}
            >
              <Button type="primary" loading={importSaving} disabled={!canSaveImport}>
                保存新数据
              </Button>
            </Popconfirm>
            <Popconfirm
              title="新数据和重复数据都会保存，确认保存吗？"
              placement="top"
              onConfirm={() => void saveImportRows(false)}
            >
              <Button type="primary" loading={importSaving} disabled={!canSaveImport}>
                保存新数据和重复数据
              </Button>
            </Popconfirm>
          </Space>
        }
        width={1000}
      >
        <Space orientation="vertical" style={{ width: '100%' }} size={12}>
          <Alert
            showIcon
            type="warning"
            title="说明"
            description={
              <ol className="ability-import-help">
                <li>没有 EXCEL 模板时，先导出 EXCEL 模板。</li>
                <li>导入预览数据后，表格中会出现 EXCEL 中的数据。</li>
                <li>没有上传 EXCEL 数据时，保存按钮禁用。</li>
              </ol>
            }
          />
          <Alert
            showIcon
            type={importOutput?.errorCount ? 'error' : importOutput?.duplicateCount ? 'warning' : 'success'}
            title={`解析 ${importOutput?.totalCount ?? importRows.length} 行，异常 ${importOutput?.errorCount ?? 0} 行，重复 ${
              importOutput?.duplicateCount ?? 0
            } 行`}
            action={
              importOutput?.errorFile ? (
                <Button size="small" danger onClick={() => void api.downloadFile(importOutput.errorFile!)}>
                  下载错误报告
                </Button>
              ) : undefined
            }
          />
          <span className="muted">识别实验室列：{labCodes.length ? labCodes.join('、') : '无'}</span>
          <Table
            size="small"
            pagination={{ pageSize: 5 }}
            rowKey={(row) => String(row.rowNumber ?? `${row.standardNo}-${row.samplingName}-${row.testItem}`)}
            dataSource={importRows}
            columns={importColumns}
            scroll={{ x: importScrollX }}
          />
          <span className="muted">
            本次导入数据共：{importRows.length} 条；新数据 {importNewCount} 条；重复数据：{importDuplicateCount} 条；
          </span>
        </Space>
      </Modal>
    </div>
  );
}

function labAbilityCards(current: LabAbility[] | undefined, labs: Laboratory[]): LabAbility[] {
  return (current ?? []).map((item) => {
    const lab = labs.find((candidate) => candidate.id === item.labId || candidate.code === item.code);
    return {
      ...item,
      labId: item.labId ?? lab?.id,
      code: item.code || lab?.code || '',
    };
  });
}

function sortOrgSettings(settings: MyOrgSetting[]) {
  return [...settings].sort((left, right) => {
    const leftIndex = productionBusinessLineOrder.findIndex((name) => name.toLowerCase() === left.orgName.toLowerCase());
    const rightIndex = productionBusinessLineOrder.findIndex((name) => name.toLowerCase() === right.orgName.toLowerCase());
    const normalizedLeftIndex = leftIndex < 0 ? productionBusinessLineOrder.length : leftIndex;
    const normalizedRightIndex = rightIndex < 0 ? productionBusinessLineOrder.length : rightIndex;
    if (normalizedLeftIndex !== normalizedRightIndex) {
      return normalizedLeftIndex - normalizedRightIndex;
    }
    return left.orgName.localeCompare(right.orgName);
  });
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

function isFormValidationError(error: unknown): error is { errorFields: unknown[] } {
  return typeof error === 'object' && error !== null && 'errorFields' in error;
}
