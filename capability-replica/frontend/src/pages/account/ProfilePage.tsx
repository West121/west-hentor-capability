import { useEffect, useState } from 'react';
import { App as AntdApp, Avatar, Button, Card, DatePicker, Divider, Form, Input, Select, Space, Table, Tabs, Tag, Upload } from 'antd';
import { GlobalOutlined, MobileOutlined, SafetyCertificateOutlined, UploadOutlined, UserOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { Dayjs } from 'dayjs';
import PageTitle from '../../components/PageTitle';
import { api } from '../../services/api';
import { useAuthStore } from '../../store/authStore';
import type { CurrentUserProfile, UserDelegation, UserItem } from '../../types/domain';
import { profilePictureUpdateInput } from './profilePictureUploadInput';

const { RangePicker } = DatePicker;

type DelegationFormValues = {
  targetUserId: number;
  range: [Dayjs, Dayjs];
};

type SmsFormValues = {
  phoneNumber?: string;
  code?: string;
};

// Account center copied from the original profile and user delegation services.
export default function ProfilePage() {
  const { message } = AntdApp.useApp();
  const [profileForm] = Form.useForm<CurrentUserProfile>();
  const [passwordForm] = Form.useForm<{ currentPassword: string; newPassword: string; confirmPassword: string }>();
  const [smsForm] = Form.useForm<SmsFormValues>();
  const [delegationForm] = Form.useForm<DelegationFormValues>();
  const token = useAuthStore((state) => state.token);
  const refreshLogin = useAuthStore((state) => state.login);
  const [profile, setProfile] = useState<CurrentUserProfile>();
  const [profilePicture, setProfilePicture] = useState<string>();
  const [googleQr, setGoogleQr] = useState<string>();
  const [languageName, setLanguageName] = useState('zh-Hans');
  const [languageOptions, setLanguageOptions] = useState<{ label: string; value: string }[]>([]);
  const [delegations, setDelegations] = useState<UserDelegation[]>([]);
  const [users, setUsers] = useState<UserItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [requiredLength, setRequiredLength] = useState(6);

  async function load() {
    setLoading(true);
    try {
      const [profileData, passwordRules, delegationData, userData, pictureData, languageData] = await Promise.all([
        api.currentProfile(),
        api.passwordComplexity(),
        api.delegatedUsers({ maxResultCount: 100 }),
        api.users({ maxResultCount: 100 }),
        api.profilePicture(),
        api.languages(),
      ]);
      setProfile(profileData);
      setProfilePicture(pictureData.profilePicture);
      setGoogleQr(profileData.qrCodeSetupImageUrl);
      setLanguageName(profileData.preferredLanguageName ?? languageData.defaultLanguageName ?? 'zh-Hans');
      setLanguageOptions(languageData.languages.map((item) => ({ label: item.displayName, value: item.name })));
      setRequiredLength(passwordRules.setting.requiredLength);
      setDelegations(delegationData.items);
      setUsers(userData.items);
      profileForm.setFieldsValue(profileData);
      smsForm.setFieldsValue({ phoneNumber: profileData.phoneNumber });
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  async function saveProfile(values: CurrentUserProfile) {
    await api.updateProfile({ ...profile, ...values });
    const saved = await api.currentProfile();
    setProfile(saved);
    profileForm.setFieldsValue(saved);
    const session = await api.session();
    if (token) {
      refreshLogin(token, session.permissions, session.user.name);
    }
    message.success('个人资料已保存');
  }

  async function changePassword(values: { currentPassword: string; newPassword: string }) {
    await api.changePassword(values.currentPassword, values.newPassword);
    passwordForm.resetFields();
    message.success('密码已更新');
  }

  async function uploadProfilePicture(file: File) {
    const uploaded = await api.uploadProfilePicture(file);
    await api.updateProfilePicture(profilePictureUpdateInput(uploaded));
    const picture = await api.profilePicture();
    setProfilePicture(picture.profilePicture);
    message.success('头像已更新');
    return false;
  }

  async function changeLanguage() {
    await api.changeUserLanguage(languageName);
    message.success('语言已切换');
  }

  async function updateGoogleAuthenticator() {
    const output = await api.updateGoogleAuthenticatorKey();
    setGoogleQr(output.qrCodeSetupImageUrl);
    setProfile((current) => (current ? { ...current, isGoogleAuthenticatorEnabled: true, qrCodeSetupImageUrl: output.qrCodeSetupImageUrl } : current));
    message.success('Google验证器密钥已更新');
  }

  async function disableGoogleAuthenticator() {
    await api.disableGoogleAuthenticator();
    setGoogleQr(undefined);
    setProfile((current) => (current ? { ...current, isGoogleAuthenticatorEnabled: false, qrCodeSetupImageUrl: '' } : current));
    message.success('Google验证器已关闭');
  }

  async function sendSmsCode() {
    const phoneNumber = smsForm.getFieldValue('phoneNumber') ?? profile?.phoneNumber;
    await api.sendVerificationSms(phoneNumber);
    message.success('短信验证码已发送');
  }

  async function verifySms(values: SmsFormValues) {
    await api.verifySmsCode(values.phoneNumber, values.code);
    await load();
    message.success('手机号已确认');
  }

  async function prepareCollectedData() {
    await api.prepareCollectedData();
    message.success('个人数据已准备');
  }

  async function createDelegation(values: DelegationFormValues) {
    const [start, end] = values.range;
    await api.delegateNewUser({
      targetUserId: values.targetUserId,
      startTime: start.format('YYYY-MM-DDTHH:mm:ss'),
      endTime: end.format('YYYY-MM-DDTHH:mm:ss'),
    });
    delegationForm.resetFields();
    await load();
    message.success('用户委托已保存');
  }

  async function removeDelegation(row: UserDelegation) {
    if (!row.id) {
      return;
    }
    await api.removeDelegation(row.id);
    await load();
    message.success('用户委托已删除');
  }

  const delegationColumns: ColumnsType<UserDelegation> = [
    { title: '被委托用户', dataIndex: 'targetUserName', width: 160 },
    { title: '姓名', dataIndex: 'targetName', width: 180 },
    { title: '开始时间', dataIndex: 'startTime', width: 210 },
    { title: '结束时间', dataIndex: 'endTime', width: 210 },
    {
      title: '状态',
      dataIndex: 'active',
      width: 100,
      render: (active: boolean) => <Tag color={active ? 'green' : 'default'}>{active ? '生效中' : '未生效'}</Tag>,
    },
    {
      title: '操作',
      width: 120,
      render: (_, row) => (
        <Button danger size="small" onClick={() => void removeDelegation(row)}>
          删除
        </Button>
      ),
    },
  ];

  const userOptions = users
    .filter((user) => user.id !== profile?.id)
    .map((user) => ({ label: `${user.userName} / ${user.name}`, value: user.id }));

  const avatarSrc = imageSource(profilePicture);

  return (
    <>
      <PageTitle title="个人中心" description="维护当前用户资料、密码、安全验证和用户委托" />
      <Tabs
        items={[
          {
            key: 'profile',
            label: '个人资料',
            forceRender: true,
            children: (
              <Card>
                <Space size={24} align="start" wrap>
                  <Space orientation="vertical" align="center" style={{ width: 150 }}>
                    <Avatar size={96} src={avatarSrc} icon={<UserOutlined />} />
                    <Upload
                      showUploadList={false}
                      accept="image/*"
                      beforeUpload={(file) => {
                        void uploadProfilePicture(file);
                        return false;
                      }}
                    >
                      <Button icon={<UploadOutlined />}>上传头像</Button>
                    </Upload>
                  </Space>
                  <Form form={profileForm} layout="vertical" onFinish={saveProfile} style={{ width: 'min(760px, 100%)' }}>
                    <Space size={16} align="start" style={{ display: 'flex' }}>
                      <Form.Item name="userName" label="用户名" style={{ flex: 1 }}>
                        <Input disabled maxLength={256} />
                      </Form.Item>
                      <Form.Item name="engName" label="英文名" style={{ flex: 1 }}>
                        <Input />
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
                    <Space size={16} align="start" style={{ display: 'flex' }}>
                      <Form.Item name="emailAddress" label="邮箱" rules={[{ required: true, type: 'email', max: 256 }]} style={{ flex: 1 }}>
                        <Input maxLength={256} />
                      </Form.Item>
                      <Form.Item name="phoneNumber" label="手机号" style={{ flex: 1 }}>
                        <Input maxLength={24} />
                      </Form.Item>
                    </Space>
                    <Button type="primary" htmlType="submit" loading={loading}>
                      保存资料
                    </Button>
                  </Form>
                </Space>
                <Divider />
                <Space size={12} wrap>
                  <Select value={languageName} options={languageOptions} style={{ width: 180 }} onChange={setLanguageName} />
                  <Button icon={<GlobalOutlined />} onClick={() => void changeLanguage()}>
                    切换语言
                  </Button>
                  <Button onClick={() => void prepareCollectedData()}>准备个人数据</Button>
                </Space>
              </Card>
            ),
          },
          {
            key: 'password',
            label: '修改密码',
            forceRender: true,
            children: (
              <Card>
                <Form form={passwordForm} layout="vertical" onFinish={changePassword} style={{ maxWidth: 520 }}>
                  <Form.Item name="currentPassword" label="当前密码" rules={[{ required: true }]}>
                    <Input.Password autoComplete="current-password" />
                  </Form.Item>
                  <Form.Item
                    name="newPassword"
                    label="新密码"
                    rules={[{ required: true, min: requiredLength, message: `至少${requiredLength}位` }]}
                  >
                    <Input.Password autoComplete="new-password" />
                  </Form.Item>
                  <Form.Item
                    name="confirmPassword"
                    label="确认新密码"
                    dependencies={['newPassword']}
                    rules={[
                      { required: true },
                      ({ getFieldValue }) => ({
                        validator(_, value) {
                          return !value || getFieldValue('newPassword') === value
                            ? Promise.resolve()
                            : Promise.reject(new Error('两次输入的密码不一致'));
                        },
                      }),
                    ]}
                  >
                    <Input.Password autoComplete="new-password" />
                  </Form.Item>
                  <Button type="primary" htmlType="submit">
                    更新密码
                  </Button>
                </Form>
              </Card>
            ),
          },
          {
            key: 'verification',
            label: '安全验证',
            forceRender: true,
            children: (
              <Card>
                <Space orientation="vertical" size={18} style={{ width: '100%' }}>
                  <Space size={12} wrap>
                    <Tag color={profile?.isGoogleAuthenticatorEnabled ? 'green' : 'default'}>
                      {profile?.isGoogleAuthenticatorEnabled ? 'Google验证器已启用' : 'Google验证器未启用'}
                    </Tag>
                    <Button icon={<SafetyCertificateOutlined />} onClick={() => void updateGoogleAuthenticator()}>
                      更新密钥
                    </Button>
                    <Button danger disabled={!profile?.isGoogleAuthenticatorEnabled} onClick={() => void disableGoogleAuthenticator()}>
                      关闭验证器
                    </Button>
                  </Space>
                  {googleQr ? <img src={googleQr} alt="Google Authenticator" style={{ width: 180, height: 180 }} /> : null}
                  <Divider style={{ margin: 0 }} />
                  <Form form={smsForm} layout="inline" onFinish={verifySms}>
                    <Form.Item name="phoneNumber" label="手机号" rules={[{ required: true }]}>
                      <Input style={{ width: 180 }} />
                    </Form.Item>
                    <Form.Item name="code" label="验证码" rules={[{ required: true }]}>
                      <Input style={{ width: 120 }} />
                    </Form.Item>
                    <Button icon={<MobileOutlined />} onClick={() => void sendSmsCode()}>
                      发送验证码
                    </Button>
                    <Button type="primary" htmlType="submit">
                      确认手机号
                    </Button>
                    <Tag color={profile?.isPhoneNumberConfirmed ? 'green' : 'orange'}>
                      {profile?.isPhoneNumberConfirmed ? '手机号已确认' : '手机号未确认'}
                    </Tag>
                  </Form>
                </Space>
              </Card>
            ),
          },
          {
            key: 'delegations',
            label: '用户委托',
            forceRender: true,
            children: (
              <Space orientation="vertical" size={16} style={{ width: '100%' }}>
                <Card>
                  <Form form={delegationForm} layout="inline" onFinish={createDelegation}>
                    <Form.Item name="targetUserId" label="被委托用户" rules={[{ required: true }]}>
                      <Select showSearch options={userOptions} style={{ width: 240 }} optionFilterProp="label" />
                    </Form.Item>
                    <Form.Item name="range" label="委托时间" rules={[{ required: true }]}>
                      <RangePicker showTime style={{ width: 390 }} />
                    </Form.Item>
                    <Button type="primary" htmlType="submit">
                      新增委托
                    </Button>
                  </Form>
                </Card>
                <Table
                  rowKey={(row) => String(row.id)}
                  columns={delegationColumns}
                  dataSource={delegations}
                  loading={loading}
                  pagination={false}
                />
              </Space>
            ),
          },
        ]}
      />
    </>
  );
}

function imageSource(value?: string) {
  if (!value) {
    return undefined;
  }
  return value.startsWith('data:image') ? value : `data:image/svg+xml;base64,${value}`;
}
