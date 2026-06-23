import { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Descriptions,
  Form,
  Input,
  Select,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { ApiOutlined, KeyOutlined, LinkOutlined, LoginOutlined, ReloadOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import PageTitle from '../../components/PageTitle';
import { api } from '../../services/api';
import { useAuthStore } from '../../store/authStore';
import type {
  CurrentUserProfile,
  ExternalAuthenticateInput,
  ExternalAuthenticateResult,
  ExternalLoginProviderInfo,
  LinkedUserItem,
  UpdateUserSignInTokenOutput,
  UserItem,
  UserLoginAttemptItem,
} from '../../types/domain';
import { defaultExternalLoginForm, externalProviderOptions } from './externalLoginForm';

type TenantStatus = {
  state: number;
  tenantId?: number;
  serverRootAddress?: string;
};

type RegisterFormValues = {
  name: string;
  surname: string;
  userName: string;
  emailAddress: string;
  password: string;
};

type PasswordResetFormValues = {
  userId: number;
  emailAddress: string;
  resetCode: string;
  password: string;
};

type EmailActivationFormValues = {
  userId: number;
  emailAddress: string;
  confirmationCode: string;
};

type AccountSwitchResult = {
  action: string;
  sourceToken?: string;
  accessToken?: string;
  tenancyName?: string;
};

function newRegisterDefaults(): RegisterFormValues {
  const suffix = Date.now().toString().slice(-6);
  return {
    name: 'Local',
    surname: 'User',
    userName: `local${suffix}`,
    emailAddress: `local${suffix}@example.local`,
    password: '123qwe',
  };
}

// Account security console copied from TokenAuth, Account, UserLink, and UserLogin services.
export default function AccountSecurityPage() {
  const { message } = AntdApp.useApp();
  const [tenantForm] = Form.useForm<{ tenancyName: string }>();
  const [registerForm] = Form.useForm<RegisterFormValues>();
  const [resetForm] = Form.useForm<PasswordResetFormValues>();
  const [activationForm] = Form.useForm<EmailActivationFormValues>();
  const [switchForm] = Form.useForm<{ targetUserId: number }>();
  const [linkForm] = Form.useForm<{ usernameOrEmailAddress: string; password: string }>();
  const [externalForm] = Form.useForm<ExternalAuthenticateInput>();
  const token = useAuthStore((state) => state.token);
  const refreshToken = useAuthStore((state) => state.refreshToken);
  const permissions = useAuthStore((state) => state.permissions);
  const userName = useAuthStore((state) => state.userName);
  const login = useAuthStore((state) => state.login);
  const logout = useAuthStore((state) => state.logout);
  const [profile, setProfile] = useState<CurrentUserProfile>();
  const [users, setUsers] = useState<UserItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [tenantStatus, setTenantStatus] = useState<TenantStatus>();
  const [registerResult, setRegisterResult] = useState<string>();
  const [resetResult, setResetResult] = useState<string>();
  const [activationResult, setActivationResult] = useState<string>();
  const [signInToken, setSignInToken] = useState<UpdateUserSignInTokenOutput>();
  const [externalProviders, setExternalProviders] = useState<ExternalLoginProviderInfo[]>([]);
  const [externalResult, setExternalResult] = useState<ExternalAuthenticateResult>();
  const [switchResult, setSwitchResult] = useState<AccountSwitchResult>();
  const [linkedUsers, setLinkedUsers] = useState<LinkedUserItem[]>([]);
  const [recentLinkedUsers, setRecentLinkedUsers] = useState<LinkedUserItem[]>([]);
  const [loginAttempts, setLoginAttempts] = useState<UserLoginAttemptItem[]>([]);

  const userOptions = useMemo(
    () => users.map((user) => ({ label: `${user.userName} / ${user.emailAddress}`, value: user.id })),
    [users],
  );
  const externalOptions = useMemo(() => externalProviderOptions(externalProviders), [externalProviders]);

  async function load() {
    setLoading(true);
    try {
      const [profileData, userData, linkedData, recentLinkedData, loginAttemptData, providerData] = await Promise.all([
        api.currentProfile(),
        api.users({ maxResultCount: 100 }),
        api.linkedUsers({ maxResultCount: 100 }),
        api.recentlyUsedLinkedUsers(),
        api.recentUserLoginAttempts(),
        api.externalAuthenticationProviders(),
      ]);
      setProfile(profileData);
      setUsers(userData.items);
      setLinkedUsers(linkedData.items);
      setRecentLinkedUsers(recentLinkedData.items);
      setLoginAttempts(loginAttemptData.items);
      setExternalProviders(providerData);
      externalForm.setFieldsValue(defaultExternalLoginForm(providerData));
      const defaultUser = userData.items.find((item) => item.id !== profileData.id) ?? userData.items[0];
      if (defaultUser?.id) {
        resetForm.setFieldsValue({
          userId: defaultUser.id,
          emailAddress: defaultUser.emailAddress,
          password: '123qwe',
        });
        activationForm.setFieldsValue({
          userId: defaultUser.id,
          emailAddress: defaultUser.emailAddress,
        });
        switchForm.setFieldsValue({ targetUserId: defaultUser.id });
        linkForm.setFieldsValue({ usernameOrEmailAddress: defaultUser.userName, password: '123qwe' });
      }
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    tenantForm.setFieldsValue({ tenancyName: 'default' });
    registerForm.setFieldsValue(newRegisterDefaults());
    void load();
  }, []);

  async function refreshAccessToken() {
    if (!refreshToken) {
      message.warning('当前没有 refreshToken');
      return;
    }
    const auth = await api.refreshToken(refreshToken);
    login(auth.accessToken, permissions, userName, refreshToken);
    const session = await api.session();
    login(auth.accessToken, session.permissions, session.user.name, refreshToken);
    message.success('Access Token 已刷新');
  }

  async function updateSignInToken() {
    const result = await api.updateUserSignInToken();
    setSignInToken(result);
    message.success('登录签名令牌已更新');
  }

  async function logoutToken() {
    await api.logoutToken();
    logout();
    message.success('Token 已注销');
  }

  async function sendTwoFactorCode() {
    await api.sendTwoFactorAuthCode(profile?.id, 'Email');
    message.success('二次验证码已发送');
  }

  async function checkTenant(values: { tenancyName: string }) {
    const result = await api.isTenantAvailable(values.tenancyName);
    setTenantStatus(result);
    message.success('租户状态已查询');
  }

  async function registerAccount(values: RegisterFormValues) {
    const result = await api.registerAccount(values);
    setRegisterResult(result.canLogin ? '注册成功，可直接登录' : '注册成功，需完成激活后登录');
    registerForm.setFieldsValue(newRegisterDefaults());
    await load();
  }

  async function sendPasswordReset(values: PasswordResetFormValues) {
    await api.sendPasswordResetCode(values.emailAddress);
    setResetResult('重置码已生成，请使用邮件链接中的重置码');
  }

  async function resetPassword(values: PasswordResetFormValues) {
    const result = await api.resetPasswordByCode(values.userId, values.resetCode, values.password);
    setResetResult(result.canLogin ? `${result.userName} 密码已重置，可登录` : `${result.userName} 密码已重置，仍需激活`);
    await load();
  }

  async function sendActivation(values: EmailActivationFormValues) {
    await api.sendEmailActivationLink(values.emailAddress);
    setActivationResult('激活码已生成，请使用邮件链接中的激活码');
  }

  async function activateEmail(values: EmailActivationFormValues) {
    await api.activateEmail(values.userId, values.confirmationCode);
    setActivationResult('邮箱已激活');
    await load();
  }

  async function createImpersonation(values: { targetUserId: number }) {
    const tokenResult = await api.impersonate(values.targetUserId, null);
    const auth = await api.impersonatedAuthenticate(tokenResult.impersonationToken);
    setSwitchResult({
      action: '模拟登录',
      sourceToken: tokenResult.impersonationToken,
      accessToken: auth.accessToken,
      tenancyName: tokenResult.tenancyName,
    });
    message.success('模拟登录 Token 已验证');
  }

  async function createLinkedSwitch(values: { targetUserId: number }) {
    const target = users.find((user) => user.id === values.targetUserId);
    if (target?.userName) {
      await api.linkToUser({ usernameOrEmailAddress: target.userName, password: '123qwe' });
    }
    const tokenResult = await api.switchToLinkedAccount(values.targetUserId, null);
    const auth = await api.linkedAccountAuthenticate(tokenResult.switchAccountToken);
    setSwitchResult({
      action: '账号切换',
      sourceToken: tokenResult.switchAccountToken,
      accessToken: auth.accessToken,
      tenancyName: tokenResult.tenancyName,
    });
    await load();
    message.success('账号切换 Token 已验证');
  }

  async function linkAccount(values: { usernameOrEmailAddress: string; password: string }) {
    await api.linkToUser(values);
    await load();
    message.success('关联账号已保存');
  }

  async function externalAuthenticate(values: ExternalAuthenticateInput) {
    const result = await api.externalAuthenticate(values);
    setExternalResult(result);
    message.success('外部登录 Token 已生成');
  }

  async function unlinkAccount(row: LinkedUserItem) {
    await api.unlinkUser(row.id, row.tenantId ?? null);
    await load();
    message.success('关联账号已解除');
  }

  const userColumns: ColumnsType<UserItem> = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '用户名', dataIndex: 'userName', width: 140 },
    { title: '邮箱', dataIndex: 'emailAddress' },
    {
      title: '激活',
      dataIndex: 'isEmailConfirmed',
      width: 100,
      render: (value: boolean) => <Tag color={value ? 'green' : 'orange'}>{value ? '已激活' : '未激活'}</Tag>,
    },
    {
      title: '状态',
      dataIndex: 'isActive',
      width: 100,
      render: (value: boolean) => <Tag color={value ? 'green' : 'default'}>{value ? '启用' : '停用'}</Tag>,
    },
  ];

  const linkedUserColumns: ColumnsType<LinkedUserItem> = [
    { title: '用户ID', dataIndex: 'id', width: 100 },
    { title: '租户', dataIndex: 'tenancyName', width: 160 },
    { title: '用户名', dataIndex: 'username' },
    {
      title: '显示登录名',
      width: 220,
      render: (_, row) => `${row.tenancyName ?? '.'}\\${row.username ?? ''}`,
    },
    {
      title: '操作',
      width: 120,
      render: (_, row) => (
        <Button danger size="small" onClick={() => void unlinkAccount(row)}>
          解除
        </Button>
      ),
    },
  ];

  const recentLinkedColumns: ColumnsType<LinkedUserItem> = linkedUserColumns.filter((column) => column.title !== '操作');

  const loginAttemptColumns: ColumnsType<UserLoginAttemptItem> = [
    { title: '时间', dataIndex: 'creationTime', width: 220 },
    { title: '租户', dataIndex: 'tenancyName', width: 120 },
    { title: '账号', dataIndex: 'userNameOrEmail', width: 180 },
    { title: 'IP', dataIndex: 'clientIpAddress', width: 140 },
    { title: '客户端', dataIndex: 'clientName', width: 150 },
    {
      title: '结果',
      dataIndex: 'result',
      width: 150,
      render: (value: string) => <Tag color={value === 'Success' ? 'green' : value === 'Failed' ? 'red' : 'orange'}>{value}</Tag>,
    },
    {
      title: '浏览器',
      dataIndex: 'browserInfo',
      ellipsis: true,
    },
  ];

  return (
    <>
      <PageTitle title="账号安全" description="复刻 TokenAuth、Account、UserLink 与 UserLogin 的本地账号安全接口" />
      <Tabs
        items={[
          {
            key: 'token',
            label: 'Token',
            forceRender: true,
            children: (
              <div style={{ display: 'grid', gap: 16 }}>
                <Card>
                  <Descriptions column={1} size="small" bordered>
                    <Descriptions.Item label="当前用户">{profile?.userName ?? userName}</Descriptions.Item>
                    <Descriptions.Item label="Access Token">
                      <Typography.Text code copyable={Boolean(token)}>
                        {shortToken(token)}
                      </Typography.Text>
                    </Descriptions.Item>
                    <Descriptions.Item label="Refresh Token">
                      <Typography.Text code copyable={Boolean(refreshToken)}>
                        {shortToken(refreshToken)}
                      </Typography.Text>
                    </Descriptions.Item>
                  </Descriptions>
                  <Space wrap style={{ marginTop: 16 }}>
                    <Button type="primary" icon={<ReloadOutlined />} onClick={() => void refreshAccessToken()}>
                      刷新 Access Token
                    </Button>
                    <Button icon={<KeyOutlined />} onClick={() => void sendTwoFactorCode()}>
                      发送二次验证码
                    </Button>
                    <Button icon={<SafetyCertificateOutlined />} onClick={() => void updateSignInToken()}>
                      刷新登录签名令牌
                    </Button>
                    <Button danger icon={<LoginOutlined />} onClick={() => void logoutToken()}>
                      注销 Token
                    </Button>
                  </Space>
                </Card>
                <Card title="外部登录">
                  <Form form={externalForm} layout="vertical" onFinish={externalAuthenticate} style={{ maxWidth: 900 }}>
                    <Space size={16} align="start" style={{ display: 'flex' }}>
                      <Form.Item name="authProvider" label="Provider" rules={[{ required: true }]} style={{ flex: 1 }}>
                        <Select options={externalOptions} />
                      </Form.Item>
                      <Form.Item name="providerKey" label="ProviderKey" rules={[{ required: true }]} style={{ flex: 1 }}>
                        <Input />
                      </Form.Item>
                    </Space>
                    <Space size={16} align="start" style={{ display: 'flex' }}>
                      <Form.Item name="providerAccessCode" label="AccessCode" rules={[{ required: true }]} style={{ flex: 1 }}>
                        <Input.Password autoComplete="off" />
                      </Form.Item>
                      <Form.Item name="returnUrl" label="ReturnUrl" style={{ flex: 1 }}>
                        <Input />
                      </Form.Item>
                    </Space>
                    <Space wrap>
                      <Form.Item name="singleSignIn" valuePropName="checked" style={{ marginBottom: 0 }}>
                        <Switch checkedChildren="SSO" unCheckedChildren="Local" />
                      </Form.Item>
                      <Button type="primary" htmlType="submit" icon={<ApiOutlined />}>
                        验证外部登录
                      </Button>
                      {externalProviders.map((provider) => (
                        <Tag key={provider.name} color={provider.name === externalForm.getFieldValue('authProvider') ? 'blue' : 'default'}>
                          {provider.name}
                        </Tag>
                      ))}
                    </Space>
                  </Form>
                  {externalResult ? (
                    <Descriptions column={1} size="small" bordered style={{ marginTop: 16 }}>
                      <Descriptions.Item label="ReturnUrl">{externalResult.returnUrl ?? '-'}</Descriptions.Item>
                      <Descriptions.Item label="Access Token">
                        <Typography.Text code copyable>
                          {shortToken(externalResult.accessToken)}
                        </Typography.Text>
                      </Descriptions.Item>
                      <Descriptions.Item label="Refresh Token">
                        <Typography.Text code copyable={Boolean(externalResult.refreshToken)}>
                          {shortToken(externalResult.refreshToken)}
                        </Typography.Text>
                      </Descriptions.Item>
                    </Descriptions>
                  ) : null}
                </Card>
                {signInToken ? (
                  <Card title="SessionAppService.UpdateUserSignInToken">
                    <Descriptions column={1} size="small" bordered>
                      <Descriptions.Item label="SignInToken">
                        <Typography.Text code copyable>
                          {signInToken.signInToken}
                        </Typography.Text>
                      </Descriptions.Item>
                      <Descriptions.Item label="EncodedUserId">
                        <Typography.Text code copyable>
                          {signInToken.encodedUserId}
                        </Typography.Text>
                      </Descriptions.Item>
                      <Descriptions.Item label="EncodedTenantId">
                        <Typography.Text code copyable>
                          {signInToken.encodedTenantId}
                        </Typography.Text>
                      </Descriptions.Item>
                    </Descriptions>
                  </Card>
                ) : null}
              </div>
            ),
          },
          {
            key: 'register',
            label: '注册与租户',
            forceRender: true,
            children: (
              <div style={{ display: 'grid', gap: 16 }}>
                <Card title="租户可用性">
                  <Form form={tenantForm} layout="inline" onFinish={checkTenant}>
                    <Form.Item name="tenancyName" label="租户名" rules={[{ required: true, max: 64 }]}>
                      <Input maxLength={64} style={{ width: 220 }} />
                    </Form.Item>
                    <Button type="primary" htmlType="submit">
                      查询
                    </Button>
                  </Form>
                  {tenantStatus ? (
                    <Alert
                      style={{ marginTop: 16 }}
                      type={tenantStatus.state === 1 ? 'success' : tenantStatus.state === 2 ? 'warning' : 'error'}
                      showIcon
                      title={tenantStatusLabel(tenantStatus)}
                    />
                  ) : null}
                </Card>
                <Card title="本地注册">
                  <Form form={registerForm} layout="vertical" onFinish={registerAccount} style={{ maxWidth: 820 }}>
                    <Space size={16} align="start" style={{ display: 'flex' }}>
                      <Form.Item name="userName" label="用户名" rules={[{ required: true, max: 256 }]} style={{ flex: 1 }}>
                        <Input maxLength={256} />
                      </Form.Item>
                      <Form.Item name="emailAddress" label="邮箱" rules={[{ required: true, type: 'email', max: 256 }]} style={{ flex: 1 }}>
                        <Input maxLength={256} />
                      </Form.Item>
                    </Space>
                    <Space size={16} align="start" style={{ display: 'flex' }}>
                      <Form.Item name="name" label="名" rules={[{ required: true, max: 64 }]} style={{ flex: 1 }}>
                        <Input maxLength={64} />
                      </Form.Item>
                      <Form.Item name="surname" label="姓" rules={[{ required: true, max: 64 }]} style={{ flex: 1 }}>
                        <Input maxLength={64} />
                      </Form.Item>
                    </Space>
                    <Form.Item name="password" label="初始密码" rules={[{ required: true, min: 6, max: 32 }]}>
                      <Input.Password autoComplete="new-password" maxLength={32} />
                    </Form.Item>
                    <Button type="primary" htmlType="submit">
                      注册账号
                    </Button>
                  </Form>
                  {registerResult ? <Alert style={{ marginTop: 16 }} type="success" showIcon title={registerResult} /> : null}
                </Card>
              </div>
            ),
          },
          {
            key: 'password',
            label: '密码与邮箱',
            forceRender: true,
            children: (
              <div style={{ display: 'grid', gap: 16 }}>
                <Card title="密码重置">
                  <Form form={resetForm} layout="vertical" onFinish={resetPassword} style={{ maxWidth: 820 }}>
                    <Space size={16} align="start" style={{ display: 'flex' }}>
                      <Form.Item name="userId" label="用户" rules={[{ required: true }]} style={{ flex: 1 }}>
                        <Select
                          showSearch
                          options={userOptions}
                          optionFilterProp="label"
                          onChange={(value) => syncUserEmail(value, (emailAddress) => resetForm.setFieldsValue({ emailAddress }))}
                        />
                      </Form.Item>
                      <Form.Item name="emailAddress" label="邮箱" rules={[{ required: true, type: 'email', max: 256 }]} style={{ flex: 1 }}>
                        <Input maxLength={256} />
                      </Form.Item>
                    </Space>
                    <Space size={16} align="start" style={{ display: 'flex' }}>
                      <Form.Item name="resetCode" label="重置码" rules={[{ required: true }]} style={{ flex: 1 }}>
                        <Input />
                      </Form.Item>
                      <Form.Item name="password" label="新密码" rules={[{ required: true, min: 6 }]} style={{ flex: 1 }}>
                        <Input.Password autoComplete="new-password" />
                      </Form.Item>
                    </Space>
                    <Space wrap>
                      <Button onClick={() => void sendPasswordReset(resetForm.getFieldsValue())}>发送重置码</Button>
                      <Button type="primary" htmlType="submit">
                        重置密码
                      </Button>
                    </Space>
                  </Form>
                  {resetResult ? <Alert style={{ marginTop: 16 }} type="success" showIcon title={resetResult} /> : null}
                </Card>
                <Card title="邮箱激活">
                  <Form form={activationForm} layout="vertical" onFinish={activateEmail} style={{ maxWidth: 820 }}>
                    <Space size={16} align="start" style={{ display: 'flex' }}>
                      <Form.Item name="userId" label="用户" rules={[{ required: true }]} style={{ flex: 1 }}>
                        <Select
                          showSearch
                          options={userOptions}
                          optionFilterProp="label"
                          onChange={(value) => syncUserEmail(value, (emailAddress) => activationForm.setFieldsValue({ emailAddress }))}
                        />
                      </Form.Item>
                      <Form.Item name="emailAddress" label="邮箱" rules={[{ required: true, type: 'email' }]} style={{ flex: 1 }}>
                        <Input />
                      </Form.Item>
                    </Space>
                    <Form.Item name="confirmationCode" label="激活码" rules={[{ required: true }]}>
                      <Input />
                    </Form.Item>
                    <Space wrap>
                      <Button onClick={() => void sendActivation(activationForm.getFieldsValue())}>发送激活码</Button>
                      <Button type="primary" htmlType="submit">
                        激活邮箱
                      </Button>
                    </Space>
                  </Form>
                  {activationResult ? <Alert style={{ marginTop: 16 }} type="success" showIcon title={activationResult} /> : null}
                </Card>
              </div>
            ),
          },
          {
            key: 'switch',
            label: '模拟登录/切换',
            forceRender: true,
            children: (
              <div style={{ display: 'grid', gap: 16 }}>
                <Card title="模拟登录与账号切换">
                  <Form form={switchForm} layout="inline" onFinish={createImpersonation}>
                    <Form.Item name="targetUserId" label="目标用户" rules={[{ required: true }]}>
                      <Select showSearch options={userOptions} optionFilterProp="label" style={{ width: 280 }} />
                    </Form.Item>
                    <Button type="primary" htmlType="submit" icon={<SafetyCertificateOutlined />}>
                      生成模拟登录
                    </Button>
                    <Button icon={<LinkOutlined />} onClick={() => void createLinkedSwitch(switchForm.getFieldsValue())}>
                      验证账号切换
                    </Button>
                  </Form>
                </Card>
                <Card title="关联账号">
                  <Form form={linkForm} layout="inline" onFinish={linkAccount} style={{ marginBottom: 16 }}>
                    <Form.Item name="usernameOrEmailAddress" label="账号" rules={[{ required: true }]}>
                      <Input style={{ width: 240 }} />
                    </Form.Item>
                    <Form.Item name="password" label="密码" rules={[{ required: true }]}>
                      <Input.Password autoComplete="current-password" style={{ width: 180 }} />
                    </Form.Item>
                    <Button type="primary" htmlType="submit" icon={<LinkOutlined />}>
                      关联账号
                    </Button>
                  </Form>
                  <Table
                    rowKey={(row) => `${row.tenantId ?? 'host'}-${row.id}`}
                    columns={linkedUserColumns}
                    dataSource={linkedUsers}
                    loading={loading}
                    pagination={false}
                  />
                </Card>
                {switchResult ? (
                  <Card title={switchResult.action}>
                    <Descriptions column={1} size="small" bordered>
                      <Descriptions.Item label="租户">{switchResult.tenancyName ?? 'default'}</Descriptions.Item>
                      <Descriptions.Item label="中转 Token">
                        <Typography.Text code copyable={Boolean(switchResult.sourceToken)}>
                          {shortToken(switchResult.sourceToken)}
                        </Typography.Text>
                      </Descriptions.Item>
                      <Descriptions.Item label="Access Token">
                        <Typography.Text code copyable={Boolean(switchResult.accessToken)}>
                          {shortToken(switchResult.accessToken)}
                        </Typography.Text>
                      </Descriptions.Item>
                    </Descriptions>
                  </Card>
                ) : null}
                <Card title="最近使用关联账号">
                  <Table
                    rowKey={(row) => `${row.tenantId ?? 'host'}-${row.id}`}
                    columns={recentLinkedColumns}
                    dataSource={recentLinkedUsers}
                    loading={loading}
                    pagination={false}
                  />
                </Card>
                <Card title="最近登录尝试">
                  <Table
                    rowKey={(row) => String(row.id)}
                    columns={loginAttemptColumns}
                    dataSource={loginAttempts}
                    loading={loading}
                    pagination={false}
                  />
                </Card>
                <Card title="本地用户">
                  <Table
                    rowKey={(row) => String(row.id)}
                    columns={userColumns}
                    dataSource={users}
                    loading={loading}
                    pagination={{ pageSize: 8 }}
                  />
                </Card>
              </div>
            ),
          },
        ]}
      />
    </>
  );

  function syncUserEmail(userId: number, setEmailAddress: (emailAddress: string) => void) {
    const user = users.find((item) => item.id === userId);
    if (user) {
      setEmailAddress(user.emailAddress);
    }
  }
}

function shortToken(value?: string) {
  if (!value) {
    return '未生成';
  }
  if (value.length <= 36) {
    return value;
  }
  return `${value.slice(0, 20)}...${value.slice(-12)}`;
}

function tenantStatusLabel(status: TenantStatus) {
  if (status.state === 1) {
    return `租户可用，tenantId=${status.tenantId ?? '-'}，root=${status.serverRootAddress ?? '-'}`;
  }
  if (status.state === 2) {
    return '租户存在但未启用';
  }
  return '租户不存在';
}
