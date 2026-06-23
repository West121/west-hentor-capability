import { CheckOutlined, CloseOutlined } from '@ant-design/icons';
import { App as AntdApp, Button, Checkbox, Form, Input, Modal, Switch, Table, Tabs } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { api } from '../../services/api';
import type { Laboratory, OrganizationUnit, RoleItem, UserItem } from '../../types/domain';

type UserQuery = {
  filter?: string;
};

type UserForm = UserItem & {
  assignedRoleNames?: string[];
  organizationUnits?: number[];
  labs?: string[];
  setRandomPassword?: boolean;
};

type UserPagination = {
  current: number;
  pageSize: number;
};

const productionLabCodeOrder = [
  'TJ',
  'GZ',
  'CZ',
  'YK',
  'QHD',
  'TS (JTG)',
  'RZ',
  'NJ',
  'JGZ',
  'CQ',
  'FCG',
  'NC',
  'QD',
  'JN',
  'UR',
  'YL',
  'BJ',
  'JZ',
  'GZO',
  'CJO',
  'STO',
  'SOO',
  'GQO',
  'TJO',
  'QDO',
  'TS (CFD)',
  'XZ',
  'XM',
];

// Production user management: one keyword search, server-side paging, and tabbed user editor.
export default function UsersPage() {
  const { message, modal } = AntdApp.useApp();
  const [items, setItems] = useState<UserItem[]>([]);
  const [roles, setRoles] = useState<RoleItem[]>([]);
  const [orgs, setOrgs] = useState<OrganizationUnit[]>([]);
  const [labs, setLabs] = useState<Laboratory[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [editing, setEditing] = useState<UserItem | undefined>();
  const [open, setOpen] = useState(false);
  const [activeTab, setActiveTab] = useState('info');
  const [query, setQuery] = useState<UserQuery>({});
  const [pagination, setPagination] = useState<UserPagination>({ current: 1, pageSize: 10 });
  const [form] = Form.useForm<UserForm>();
  const [queryForm] = Form.useForm<UserQuery>();
  const watchedAssignedRoleNames = Form.useWatch('assignedRoleNames', form);

  const roleNameMap = useMemo(() => {
    const next = new Map<string, string>();
    roles.forEach((role) => {
      const name = role.roleName ?? role.name;
      if (name) {
        next.set(name, role.roleDisplayName ?? role.displayName ?? name);
      }
    });
    return next;
  }, [roles]);

  const orgNameMap = useMemo(() => {
    const next = new Map<number, string>();
    orgs.forEach((org) => {
      if (org.id !== undefined) {
        next.set(Number(org.id), org.displayName);
      }
    });
    return next;
  }, [orgs]);

  const sortedLabs = useMemo(() => {
    return [...labs].sort((left, right) => labOrder(left) - labOrder(right) || left.name.localeCompare(right.name, 'zh-Hans-CN'));
  }, [labs]);

  async function load(nextQuery = query, nextPagination = pagination) {
    setLoading(true);
    try {
      const skipCount = (nextPagination.current - 1) * nextPagination.pageSize;
      const [userData, roleData, orgData] = await Promise.all([
        api.users({
          filter: nextQuery.filter,
          skipCount,
          maxResultCount: nextPagination.pageSize,
          sorting: 'ProductionDefault',
        }),
        api.roles({ maxResultCount: 100 }),
        api.orgUnits(),
      ]);
      setItems(userData.items);
      setTotal(userData.totalCount);
      setRoles(roleData.items);
      setOrgs(orgData.items);
      setQuery(nextQuery);
      setPagination(nextPagination);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load({}, { current: 1, pageSize: 10 });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function search(values: UserQuery) {
    await load({ filter: values.filter?.trim() }, { current: 1, pageSize: pagination.pageSize });
  }

  async function resetSearch() {
    queryForm.resetFields();
    await load({}, { current: 1, pageSize: pagination.pageSize });
  }

  async function edit(row?: UserItem) {
    setActiveTab('info');
    setEditing(row);
    const data = await api.userForEdit(row?.id);
    const user = data.user ?? {
      name: '',
      surname: '-',
      userName: '',
      emailAddress: '',
      password: 'qazwsxEDCRFV',
      isActive: true,
      isLockoutEnabled: false,
      shouldChangePasswordOnNextLogin: false,
    };
    const assignedRoles = data.roles
      .filter((role) => role.isAssigned)
      .map((role) => role.roleName ?? role.name)
      .filter((name): name is string => Boolean(name));
    setRoles(data.roles);
    setOrgs(data.allOrganizationUnits);
    setLabs(data.allLabs);
    form.setFieldsValue({
      ...user,
      name: user.name ?? '',
      surname: user.surname ?? '-',
      userName: user.userName ?? '',
      emailAddress: user.emailAddress ?? '',
      phoneNumber: user.phoneNumber ?? '',
      engName: user.engName ?? '',
      password: user.password ?? 'qazwsxEDCRFV',
      isActive: user.isActive ?? true,
      assignedRoleNames: assignedRoles.length ? assignedRoles : data.assignedRoleNames ?? [],
      organizationUnits: (data.memberedOrganizationUnits ?? []).map(Number),
      labs: (data.memberedLabs ?? []).map(String),
      setRandomPassword: false,
    });
    setOpen(true);
  }

  async function save() {
    const values = await form.validateFields();
    await api.saveUser({
      user: {
        ...editing,
        name: values.name,
        surname: values.surname ?? '-',
        userName: values.userName,
        emailAddress: values.emailAddress,
        phoneNumber: values.phoneNumber,
        password: values.password,
        engName: values.engName,
        isActive: values.isActive,
        isLockoutEnabled: values.isLockoutEnabled ?? false,
        shouldChangePasswordOnNextLogin: values.shouldChangePasswordOnNextLogin ?? false,
      },
      assignedRoleNames: values.assignedRoleNames ?? [],
      organizationUnits: values.organizationUnits ?? [],
      labs: values.labs ?? [],
      setRandomPassword: values.setRandomPassword ?? false,
    });
    message.success('保存成功');
    setOpen(false);
    await load(query, pagination);
  }

  async function remove(row: UserItem) {
    if (!row.id) {
      return;
    }
    const userId = row.id;
    modal.confirm({
      title: `删除用户: ${row.userName}`,
      content: '删除后该用户将不能登录系统。',
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        await api.deleteUser(userId);
        message.warning('删除成功');
        await load(query, pagination);
      },
    });
  }

  async function resetPassword(row: UserItem) {
    if (!row.id) {
      return;
    }
    modal.confirm({
      title: `重置 ${row.userName} 的密码`,
      content: '密码会重置为 qazwsxEDCRFV。',
      okText: '确定',
      cancelText: '取消',
      onOk: async () => {
        await api.resetUserPassword(row.id);
        message.success('密码已重置为 qazwsxEDCRFV');
      },
    });
  }

  const selectedRoleNames = Array.isArray(watchedAssignedRoleNames) ? watchedAssignedRoleNames : form.getFieldValue('assignedRoleNames');
  const roleCount = Array.isArray(selectedRoleNames) ? selectedRoleNames.length : 0;

  return (
    <div className="user-management-page">
      <div className="user-management-titlebar">
        <h1>用户管理</h1>
        <Button type="primary" onClick={() => void edit()}>
          <span>新建</span>
        </Button>
      </div>

      <div className="user-management-search">
        <Form form={queryForm} layout="inline" onFinish={(values) => void search(values)}>
          <Form.Item label="关键字" name="filter">
            <Input maxLength={256} />
          </Form.Item>
          <Button type="primary" htmlType="submit">
            <span>搜索</span>
          </Button>
          <Button onClick={() => void resetSearch()}>
            <span>重置</span>
          </Button>
        </Form>
      </div>

      <div className="user-management-table-panel">
        <Table<UserItem>
          rowKey={(row) => String(row.id ?? row.userName)}
          loading={loading}
          dataSource={items}
          scroll={{ x: 1485 }}
          pagination={{
            current: pagination.current,
            pageSize: pagination.pageSize,
            total,
            placement: ['bottomEnd'],
            showSizeChanger: true,
            showTotal: (count) => `共 ${count} 条`,
            onChange: (current, pageSize) => void load(query, { current, pageSize }),
          }}
          columns={[
            { title: '工号', dataIndex: 'userName', width: 115 },
            { title: '姓名', dataIndex: 'name', width: 110 },
            {
              title: '角色',
              dataIndex: 'assignedRoleNames',
              width: 335,
              render: (value?: string[]) => formatRoleNames(value, roleNameMap),
            },
            {
              title: '业务线',
              dataIndex: 'organizationUnits',
              width: 315,
              render: (value?: number[]) => formatOrgNames(value, orgNameMap),
            },
            { title: '邮箱', dataIndex: 'emailAddress', width: 220 },
            {
              title: '是否启用',
              dataIndex: 'isActive',
              width: 90,
              align: 'center',
              render: (value?: boolean) => <BooleanMark value={Boolean(value)} />,
            },
            {
              title: '创建时间',
              dataIndex: 'creationTime',
              width: 145,
              render: (value?: string) => formatDateTime(value),
            },
            {
              title: '',
              width: 155,
              render: (_, row) => (
                <div className="user-management-actions">
                  <button type="button" onClick={() => void resetPassword(row)}>
                    重置密码
                  </button>
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
        className="user-edit-modal"
        destroyOnHidden
        forceRender
        title={editing ? `编辑用户: ${editing.userName}` : '创建用户'}
        open={open}
        onCancel={() => setOpen(false)}
        width={900}
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
        <Form form={form} className="user-edit-form" colon={false} labelCol={{ flex: '170px' }} wrapperCol={{ flex: '1 1 auto' }}>
          <Tabs
            className="user-edit-tabs"
            activeKey={activeTab}
            onChange={setActiveTab}
            items={[
              {
                key: 'info',
                label: '用户信息',
                children: (
                  <div className="user-edit-tab-body user-edit-info">
                    <Form.Item name="userName" label="工号" rules={[{ required: true, max: 256 }]}>
                      <Input disabled={Boolean(editing)} maxLength={256} />
                    </Form.Item>
                    <Form.Item name="name" label="姓名" rules={[{ required: true, max: 64 }]}>
                      <Input maxLength={64} />
                    </Form.Item>
                    <Form.Item name="engName" label="英文名">
                      <Input maxLength={128} />
                    </Form.Item>
                    <Form.Item name="emailAddress" label="邮箱地址" rules={[{ required: true, type: 'email', max: 256 }]}>
                      <Input maxLength={256} />
                    </Form.Item>
                    <Form.Item name="phoneNumber" label="电话" rules={[{ max: 24 }]}>
                      <Input maxLength={24} />
                    </Form.Item>
                    <Form.Item name="isActive" label="是否启用" valuePropName="checked">
                      <Switch />
                    </Form.Item>
                    {!editing ? (
                      <Form.Item
                        name="password"
                        label="密码"
                        dependencies={['setRandomPassword']}
                        rules={[
                          ({ getFieldValue }) => ({
                            validator(_, value) {
                              if (getFieldValue('setRandomPassword') || value) {
                                return Promise.resolve();
                              }
                              return Promise.reject(new Error('请输入密码'));
                            },
                          }),
                          { max: 32 },
                        ]}
                      >
                        <Input.Password maxLength={32} />
                      </Form.Item>
                    ) : null}
                    <Form.Item name="surname" label="姓氏" rules={[{ max: 64 }]} hidden>
                      <Input maxLength={64} />
                    </Form.Item>
                    <Form.Item name="isLockoutEnabled" hidden valuePropName="checked">
                      <Switch />
                    </Form.Item>
                    <Form.Item name="shouldChangePasswordOnNextLogin" hidden valuePropName="checked">
                      <Switch />
                    </Form.Item>
                    <Form.Item name="setRandomPassword" label="随机密码" hidden valuePropName="checked">
                      <Switch />
                    </Form.Item>
                  </div>
                ),
              },
              {
                key: 'roles',
                label: (
                  <span className="user-edit-tab-label">
                    角色 <span className="user-edit-tab-badge">{roleCount}</span>
                  </span>
                ),
                children: (
                  <div className="user-edit-tab-body user-edit-checkbox-grid user-edit-role-grid">
                    <Form.Item name="assignedRoleNames" noStyle>
                      <Checkbox.Group
                        options={roles
                          .map((role) => ({
                            label: role.roleDisplayName ?? role.displayName,
                            value: role.roleName ?? role.name,
                          }))
                          .filter((option): option is { label: string; value: string } => Boolean(option.label && option.value))}
                      />
                    </Form.Item>
                  </div>
                ),
              },
              {
                key: 'orgs',
                label: '业务线',
                children: (
                  <div className="user-edit-tab-body user-edit-business-list">
                    <Form.Item name="organizationUnits" noStyle>
                      <Checkbox.Group
                        options={orgs
                          .filter((org) => org.id !== undefined)
                          .map((org) => ({ label: org.displayName, value: Number(org.id) }))}
                      />
                    </Form.Item>
                  </div>
                ),
              },
              {
                key: 'labs',
                label: '实验室',
                children: (
                  <div className="user-edit-tab-body user-edit-checkbox-grid user-edit-lab-grid">
                    <Form.Item name="labs" noStyle>
                      <Checkbox.Group options={sortedLabs.filter((lab) => lab.id).map((lab) => ({ label: lab.name, value: String(lab.id) }))} />
                    </Form.Item>
                  </div>
                ),
              },
            ]}
          />
        </Form>
      </Modal>
    </div>
  );
}

function BooleanMark({ value }: { value: boolean }) {
  return value ? <CheckOutlined className="user-management-check" /> : <CloseOutlined className="user-management-close" />;
}

function formatRoleNames(value: string[] | undefined, roleNameMap: Map<string, string>) {
  if (!value?.length) {
    return '-';
  }
  return value.map((name) => roleNameMap.get(name) ?? name).join(', ');
}

function formatOrgNames(value: number[] | undefined, orgNameMap: Map<number, string>) {
  if (!value?.length) {
    return '-';
  }
  return value.map((id) => orgNameMap.get(Number(id)) ?? String(id)).join(',');
}

function formatDateTime(value?: string) {
  if (!value) {
    return '-';
  }
  const normalized = value.replace('T', ' ');
  return normalized.slice(0, 16);
}

function labOrder(lab: Laboratory) {
  const index = productionLabCodeOrder.findIndex((code) => code === lab.code);
  return index >= 0 ? index : Number.MAX_SAFE_INTEGER;
}
