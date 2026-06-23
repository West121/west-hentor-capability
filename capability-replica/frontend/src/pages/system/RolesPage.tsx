import { App as AntdApp, Button, Checkbox, Form, Input, Modal, Table, Tabs, Tree } from 'antd';
import { CheckOutlined, CloseOutlined } from '@ant-design/icons';
import type { Key } from 'react';
import { useEffect, useMemo, useState } from 'react';
import { api } from '../../services/api';
import type { PermissionItem, RoleItem } from '../../types/domain';

type PermissionTreeNode = { key: string; title: string; children?: PermissionTreeNode[] };
type PermissionTreeSpec = { key: string; title: string; children?: PermissionTreeSpec[] };

const expandedPermissionKeys = ['Pages', 'Pages.AbilityManagement', 'Pages.Log', 'Pages.Administration'];

const productionPermissionTree: PermissionTreeSpec[] = [
  {
    key: 'Pages',
    title: '页面',
    children: [
      { key: 'Pages.Tenant.Dashboard', title: '工作台' },
      { key: 'Pages.AbilityQuery', title: '能力表查询' },
      {
        key: 'Pages.AbilityManagement',
        title: '能力表管理',
        children: [
          { key: 'Pages.AbilityManagement.EditDesc', title: '编辑说明' },
          { key: 'Pages.AbilityManagement.Sample', title: '能力表类型管理' },
          { key: 'Pages.AbilityManagement.AbilitySetting', title: '能力表设置' },
          {
            key: 'Pages.AbilityManagement.Ability',
            title: '能力表数据管理',
            children: [
              { key: 'Pages.AbilityManagement.Ability.Create', title: '新建' },
              { key: 'Pages.AbilityManagement.Ability.Edit', title: '编辑' },
              { key: 'Pages.AbilityManagement.Ability.PublicEdit', title: '公开能力编辑' },
              { key: 'Pages.AbilityManagement.Ability.Delete', title: '删除' },
              { key: 'Pages.AbilityManagement.Ability.DeleteAll', title: '全部删除' },
              { key: 'Pages.AbilityManagement.Ability.ImportExcel', title: '导入Excel' },
              { key: 'Pages.AbilityManagement.Ability.History', title: '历史' },
            ],
          },
        ],
      },
      {
        key: 'Pages.Log',
        title: '日志',
        children: [
          { key: 'Pages.Administration.AuditLogs', title: '操作日志' },
          { key: 'Pages.Log.AbilityHistory', title: '能力表历史记录' },
        ],
      },
      {
        key: 'Pages.Administration',
        title: '系统管理',
        children: [
          { key: 'Pages.Administration.StandardUpdate', title: '标准号更新' },
          { key: 'Pages.Administration.OrganizationUnits', title: '部门管理' },
          {
            key: 'Pages.Administration.Roles',
            title: '角色',
            children: [{ key: 'Pages.Administration.Roles.Edit', title: '编辑' }],
          },
          { key: 'Pages.Administration.Tenant.Settings', title: '设置' },
          { key: 'Pages.Administration.Laboratory', title: '实验室管理' },
          {
            key: 'Pages.Administration.Users',
            title: '用户',
            children: [
              { key: 'Pages.Administration.Users.Create', title: '新建' },
              { key: 'Pages.Administration.Users.Edit', title: '编辑' },
              { key: 'Pages.Administration.Users.Delete', title: '删除' },
              { key: 'Pages.Administration.Users.ChangePermissions', title: '权限' },
              { key: 'Pages.Administration.Users.Impersonation', title: '模拟登录' },
            ],
          },
        ],
      },
    ],
  },
];

// Role management mirrors the production SGS role list and two-tab edit modal.
export default function RolesPage() {
  const { message } = AntdApp.useApp();
  const [roles, setRoles] = useState<RoleItem[]>([]);
  const [permissions, setPermissions] = useState<PermissionItem[]>([]);
  const [checked, setChecked] = useState<string[]>([]);
  const [editing, setEditing] = useState<RoleItem | undefined>();
  const [open, setOpen] = useState(false);
  const [activeTab, setActiveTab] = useState('role');
  const [form] = Form.useForm<RoleItem>();

  const permissionTree = useMemo(() => toProductionPermissionTree(permissions), [permissions]);
  const visiblePermissionNames = useMemo(() => new Set(flattenTreeKeys(permissionTree)), [permissionTree]);
  const visibleCheckedKeys = useMemo(() => checked.filter((name) => visiblePermissionNames.has(name)), [checked, visiblePermissionNames]);

  async function load(values?: unknown) {
    const [roleData, permissionData] = await Promise.all([api.roles(values), api.permissions()]);
    setRoles(roleData.items);
    setPermissions(permissionData.items);
  }

  useEffect(() => {
    void load();
  }, []);

  async function edit(row?: RoleItem) {
    setActiveTab('role');
    const data = await api.roleForEdit(row?.id);
    const role = data.role ?? row;
    setEditing(role);
    form.setFieldsValue({
      displayName: role?.displayName ?? '',
      isDefault: role?.isDefault ?? false,
    });
    setChecked(data.grantedPermissionNames ?? []);
    setOpen(true);
  }

  async function save() {
    const values = await form.validateFields();
    await api.saveRole({ ...editing, displayName: values.displayName, isDefault: values.isDefault ?? false }, checked);
    message.success('保存成功');
    setOpen(false);
    await load();
  }

  async function remove(row: RoleItem) {
    if (!row.id) return;
    await api.deleteRole(row.id);
    message.warning('删除成功');
    await load();
  }

  function handlePermissionCheck(keys: Key[] | { checked: Key[]; halfChecked: Key[] }) {
    const nextVisibleKeys = Array.isArray(keys) ? keys.map(String) : keys.checked.map(String);
    setChecked((current) => [
      ...current.filter((name) => !visiblePermissionNames.has(name)),
      ...nextVisibleKeys.filter((name, index) => nextVisibleKeys.indexOf(name) === index),
    ]);
  }

  return (
    <div className="role-management-page">
      <div className="role-management-titlebar">
        <h1>角色管理</h1>
        <Button type="primary" onClick={() => void edit()}>
          <span>新建</span>
        </Button>
      </div>

      <div className="role-management-table-panel">
        <Table<RoleItem>
          rowKey={(row) => String(row.id ?? row.name)}
          dataSource={roles}
          pagination={{
            hideOnSinglePage: false,
            pageSize: 10,
            placement: ['bottomEnd'],
            showSizeChanger: false,
          }}
          columns={[
            {
              title: '名称',
              dataIndex: 'displayName',
              render: (value: string, row) => value || row.name || '-',
            },
            {
              title: '系统',
              dataIndex: 'isStatic',
              width: 160,
              align: 'center',
              render: (value: boolean) => <BooleanMark value={value} />,
            },
            {
              title: '默认',
              dataIndex: 'isDefault',
              width: 160,
              align: 'center',
              render: (value: boolean) => <BooleanMark value={value} />,
            },
            {
              title: '创建时间',
              dataIndex: 'creationTime',
              width: 240,
              render: (value?: string) => formatRoleTime(value),
            },
            {
              title: '',
              width: 170,
              align: 'center',
              render: (_, row) => (
                <div className="role-management-actions">
                  <button type="button" onClick={() => void edit(row)}>
                    编辑
                  </button>
                  <button type="button" onClick={() => void remove(row)}>
                    删除
                  </button>
                </div>
              ),
            },
          ]}
        />
      </div>

      <Modal
        className="role-edit-modal"
        destroyOnHidden
        forceRender
        title={editing ? `编辑角色: ${editing.displayName || editing.name || ''}` : '新建角色'}
        open={open}
        onCancel={() => setOpen(false)}
        width={884}
        footer={
          <>
            <Button onClick={() => setOpen(false)}>
              <span>取消</span>
            </Button>
            <Button type="primary" onClick={() => void save()}>
              <span>保存</span>
            </Button>
          </>
        }
      >
        <Tabs
          activeKey={activeTab}
          className="role-edit-tabs"
          onChange={setActiveTab}
          items={[
            {
              key: 'role',
              label: '角色信息',
              children: (
                <div className="role-edit-tab-body">
                  <Form form={form} className="role-edit-form" labelCol={{ span: 6 }} wrapperCol={{ span: 16 }}>
                    <Form.Item name="displayName" label="名称" rules={[{ required: true, message: '请输入名称' }]}>
                      <Input disabled={editing?.isStatic} />
                    </Form.Item>
                    <Form.Item name="isDefault" valuePropName="checked" wrapperCol={{ offset: 6, span: 16 }}>
                      <Checkbox>默认</Checkbox>
                    </Form.Item>
                  </Form>
                  <div className="role-edit-warning">修改权限后需要刷新</div>
                </div>
              ),
            },
            {
              key: 'permissions',
              label: '权限',
              children: (
                <div className="role-edit-tab-body role-edit-permissions">
                  <Tree
                    checkable
                    defaultExpandedKeys={expandedPermissionKeys}
                    checkedKeys={visibleCheckedKeys}
                    treeData={permissionTree}
                    onCheck={handlePermissionCheck}
                  />
                  <div className="role-edit-warning">修改权限后需要刷新</div>
                </div>
              ),
            },
          ]}
        />
      </Modal>
    </div>
  );
}

function BooleanMark({ value }: { value?: boolean }) {
  return value ? <CheckOutlined className="role-management-check" /> : <CloseOutlined className="role-management-close" />;
}

function formatRoleTime(value?: string) {
  if (!value) return '-';
  return value.replace('T', ' ').slice(0, 16);
}

function toProductionPermissionTree(items: PermissionItem[]): PermissionTreeNode[] {
  const permissionNames = new Set(items.map((item) => item.name));
  return productionPermissionTree.flatMap((item) => specToNode(item, permissionNames));
}

function specToNode(item: PermissionTreeSpec, permissionNames: Set<string>): PermissionTreeNode[] {
  if (!permissionNames.has(item.key)) {
    return [];
  }
  const children = item.children?.flatMap((child) => specToNode(child, permissionNames)) ?? [];
  return [
    {
      key: item.key,
      title: item.title,
      ...(children.length ? { children } : {}),
    },
  ];
}

function flattenTreeKeys(nodes: PermissionTreeNode[]): string[] {
  return nodes.flatMap((node) => [node.key, ...(node.children ? flattenTreeKeys(node.children) : [])]);
}
