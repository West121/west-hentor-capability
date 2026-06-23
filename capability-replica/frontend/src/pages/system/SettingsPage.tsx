import { useEffect, useState } from 'react';
import {
  App as AntdApp,
  Button,
  Card,
  Col,
  Divider,
  Form,
  Input,
  InputNumber,
  Row,
  Space,
  Switch,
  Tabs,
  Upload,
} from 'antd';
import { ClearOutlined, EyeOutlined, MailOutlined, SaveOutlined, SyncOutlined, UploadOutlined } from '@ant-design/icons';
import PageTitle from '../../components/PageTitle';
import { api } from '../../services/api';
import { baseURL } from '../../services/http';
import { useAuthStore } from '../../store/authStore';
import type { AbilitySettings, HostSettings, TenantSettings } from '../../types/domain';
import { tenantBrandingUrls } from './tenantBrandingUrls';

// Mirrors HostSettingsAppService and TenantSettingsAppService settings screens.
export default function SettingsPage() {
  const { message } = AntdApp.useApp();
  const canEditDescription = useAuthStore((state) => state.can('Pages.AbilityManagement.EditDesc'));
  const [hostForm] = Form.useForm<HostSettings>();
  const [tenantForm] = Form.useForm<TenantSettings>();
  const [abilityForm] = Form.useForm<AbilitySettings>();
  const [loading, setLoading] = useState(false);
  const [hostEmail, setHostEmail] = useState('admin@example.local');
  const [tenantEmail, setTenantEmail] = useState('admin@example.local');
  const [brandingVersion, setBrandingVersion] = useState(Date.now());
  const [tenantId, setTenantId] = useState<number | undefined>();
  const brandingUrls = tenantBrandingUrls(baseURL, tenantId, brandingVersion);

  async function load() {
    setLoading(true);
    try {
      const [host, tenant, ability, session] = await Promise.all([
        api.hostSettings(),
        api.tenantSettings(),
        canEditDescription ? api.hostAbilitySettings() : Promise.resolve<AbilitySettings | undefined>(undefined),
        api.session(),
      ]);
      hostForm.setFieldsValue(host);
      tenantForm.setFieldsValue(tenant);
      setTenantId(session.tenant?.id);
      if (ability) {
        abilityForm.setFieldsValue(ability);
      }
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  async function saveHost() {
    const values = await hostForm.validateFields();
    await api.updateHostSettings(values);
    await load();
    message.success('宿主设置已保存');
  }

  async function saveTenant() {
    const values = await tenantForm.validateFields();
    await api.updateTenantSettings(values);
    await load();
    message.success('租户设置已保存');
  }

  async function saveAbility() {
    if (!canEditDescription) return;
    const values = await abilityForm.validateFields();
    await api.updateHostAbilitySettings(values);
    await load();
    message.success('能力说明已保存');
  }

  async function sendHostEmail() {
    await api.sendHostTestEmail(hostEmail);
    message.success('宿主测试邮件已模拟发送');
  }

  async function sendTenantEmail() {
    await api.sendTenantTestEmail(tenantEmail);
    message.success('租户测试邮件已模拟发送');
  }

  async function clearTenantLogo() {
    await api.clearTenantLogo();
    setBrandingVersion(Date.now());
    message.success('租户Logo已清理');
  }

  async function clearTenantCss() {
    await api.clearTenantCustomCss();
    setBrandingVersion(Date.now());
    message.success('租户自定义CSS已清理');
  }

  async function uploadTenantLogo(file: File) {
    await api.uploadTenantLogo(file);
    setBrandingVersion(Date.now());
    message.success('租户Logo已上传');
  }

  async function uploadTenantCss(file: File) {
    await api.uploadTenantCustomCss(file);
    setBrandingVersion(Date.now());
    message.success('租户自定义CSS已上传');
  }

  return (
    <div className="page-body">
      <PageTitle title="系统设置" description="维护宿主、租户、邮件、安全和能力说明设置" />
      <Card loading={loading}>
        <Tabs
          items={[
            {
              key: 'host',
              label: '宿主设置',
              forceRender: true,
              children: (
                <Form form={hostForm} layout="vertical">
                  <Toolbar onReload={load} onSave={saveHost} />
                  <GeneralFields />
                  <Divider>租户注册</Divider>
                  <Row gutter={16}>
                    <Col xs={24} md={8}>
                      <Form.Item name={['tenantManagement', 'allowSelfRegistration']} label="允许租户自注册" valuePropName="checked">
                        <Switch />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={8}>
                      <Form.Item
                        name={['tenantManagement', 'isNewRegisteredTenantActiveByDefault']}
                        label="新租户默认启用"
                        valuePropName="checked"
                      >
                        <Switch />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={8}>
                      <Form.Item name={['tenantManagement', 'useCaptchaOnRegistration']} label="注册验证码" valuePropName="checked">
                        <Switch />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={8}>
                      <Form.Item name={['tenantManagement', 'defaultEditionId']} label="默认版本ID">
                        <InputNumber min={1} style={{ width: '100%' }} />
                      </Form.Item>
                    </Col>
                  </Row>
                  <HostUserManagementFields />
                  <EmailFields />
                  <SecurityFields />
                  <BillingFields tax={false} />
                  <OtherFields />
                  <ExternalLoginFields />
                  <Divider>测试邮件</Divider>
                  <Space wrap>
                    <Input maxLength={256} style={{ width: 260 }} value={hostEmail} onChange={(event) => setHostEmail(event.target.value)} />
                    <Button icon={<MailOutlined />} onClick={() => void sendHostEmail()}>
                      发送测试邮件
                    </Button>
                  </Space>
                </Form>
              ),
            },
            {
              key: 'tenant',
              label: '租户设置',
              forceRender: true,
              children: (
                <Form form={tenantForm} layout="vertical">
                  <Toolbar onReload={load} onSave={saveTenant} />
                  <GeneralFields />
                  <TenantUserManagementFields />
                  <EmailFields tenant />
                  <LdapFields />
                  <SecurityFields />
                  <BillingFields tax />
                  <OtherFields />
                  <ExternalLoginFields />
                  <Divider>租户资源</Divider>
                  <Space wrap>
                    <Input maxLength={256} style={{ width: 260 }} value={tenantEmail} onChange={(event) => setTenantEmail(event.target.value)} />
                    <Button icon={<MailOutlined />} onClick={() => void sendTenantEmail()}>
                      发送测试邮件
                    </Button>
                    <img
                      alt="Tenant logo"
                      src={brandingUrls.logo}
                      style={{ width: 160, height: 42, objectFit: 'contain', border: '1px solid #d9d9d9', borderRadius: 6 }}
                    />
                    <Upload
                      accept="image/png,image/jpeg,image/gif"
                      showUploadList={false}
                      beforeUpload={(file) => {
                        void uploadTenantLogo(file);
                        return false;
                      }}
                    >
                      <Button icon={<UploadOutlined />}>上传Logo</Button>
                    </Upload>
                    <Upload
                      accept=".css,text/css"
                      showUploadList={false}
                      beforeUpload={(file) => {
                        void uploadTenantCss(file);
                        return false;
                      }}
                    >
                      <Button icon={<UploadOutlined />}>上传CSS</Button>
                    </Upload>
                    <Button
                      icon={<EyeOutlined />}
                      onClick={() => window.open(brandingUrls.customCss, '_blank')}
                    >
                      查看CSS
                    </Button>
                    <Button icon={<ClearOutlined />} onClick={() => void clearTenantLogo()}>
                      清理Logo
                    </Button>
                    <Button icon={<ClearOutlined />} onClick={() => void clearTenantCss()}>
                      清理CSS
                    </Button>
                  </Space>
                </Form>
              ),
            },
            ...(canEditDescription
              ? [
                  {
                    key: 'ability',
                    label: '能力说明',
                    forceRender: true,
                    children: (
                      <Form form={abilityForm} layout="vertical">
                        <Toolbar onReload={load} onSave={saveAbility} />
                        <Form.Item name="description" label="能力表说明">
                          <Input.TextArea rows={8} />
                        </Form.Item>
                      </Form>
                    ),
                  },
                ]
              : []),
          ]}
        />
      </Card>
    </div>
  );
}

function Toolbar({ onReload, onSave }: { onReload: () => Promise<void>; onSave: () => Promise<void> }) {
  return (
    <Space style={{ marginBottom: 16 }} wrap>
      <Button type="primary" icon={<SaveOutlined />} onClick={() => void onSave()}>
        保存
      </Button>
      <Button icon={<SyncOutlined />} onClick={() => void onReload()}>
        刷新
      </Button>
    </Space>
  );
}

function GeneralFields() {
  return (
    <>
      <Divider>通用</Divider>
      <Row gutter={16}>
        <Col xs={24} md={12}>
          <Form.Item name={['general', 'timezone']} label="时区">
            <Input />
          </Form.Item>
        </Col>
        <Col xs={24} md={12}>
          <Form.Item name={['general', 'timezoneForComparison']} label="比较时区">
            <Input />
          </Form.Item>
        </Col>
      </Row>
    </>
  );
}

function HostUserManagementFields() {
  return (
    <>
      <Divider>用户管理</Divider>
      <Row gutter={16}>
        <SwitchItem name={['userManagement', 'isEmailConfirmationRequiredForLogin']} label="登录要求邮箱确认" />
        <SwitchItem name={['userManagement', 'smsVerificationEnabled']} label="启用短信验证" />
        <SwitchItem name={['userManagement', 'isCookieConsentEnabled']} label="Cookie同意提示" />
        <SwitchItem name={['userManagement', 'isQuickThemeSelectEnabled']} label="快速主题选择" />
        <SwitchItem name={['userManagement', 'useCaptchaOnLogin']} label="登录验证码" />
      </Row>
      <SessionFields prefix={['userManagement', 'sessionTimeOutSettings']} />
    </>
  );
}

function TenantUserManagementFields() {
  return (
    <>
      <Divider>用户注册</Divider>
      <Row gutter={16}>
        <SwitchItem name={['userManagement', 'allowSelfRegistration']} label="允许用户自注册" />
        <SwitchItem name={['userManagement', 'isNewRegisteredUserActiveByDefault']} label="新用户默认启用" />
        <SwitchItem name={['userManagement', 'isEmailConfirmationRequiredForLogin']} label="登录要求邮箱确认" />
        <SwitchItem name={['userManagement', 'useCaptchaOnRegistration']} label="注册验证码" />
        <SwitchItem name={['userManagement', 'useCaptchaOnLogin']} label="登录验证码" />
        <SwitchItem name={['userManagement', 'isCookieConsentEnabled']} label="Cookie同意提示" />
        <SwitchItem name={['userManagement', 'isQuickThemeSelectEnabled']} label="快速主题选择" />
      </Row>
      <SessionFields prefix={['userManagement', 'sessionTimeOutSettings']} />
    </>
  );
}

function SessionFields({ prefix }: { prefix: string[] }) {
  return (
    <>
      <Divider>会话超时</Divider>
      <Row gutter={16}>
        <SwitchItem name={[...prefix, 'isEnabled']} label="启用超时" />
        <Col xs={24} md={8}>
          <Form.Item name={[...prefix, 'timeOutSecond']} label="超时秒数">
            <InputNumber min={10} style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col xs={24} md={8}>
          <Form.Item name={[...prefix, 'showTimeOutNotificationSecond']} label="提醒秒数">
            <InputNumber min={10} style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <SwitchItem name={[...prefix, 'showLockScreenWhenTimedOut']} label="超时锁屏" />
      </Row>
    </>
  );
}

function EmailFields({ tenant = false }: { tenant?: boolean }) {
  return (
    <>
      <Divider>邮件</Divider>
      <Row gutter={16}>
        {tenant ? <SwitchItem name={['email', 'useHostDefaultEmailSettings']} label="使用宿主邮件设置" /> : null}
        <Col xs={24} md={12}>
          <Form.Item name={['email', 'defaultFromAddress']} label="默认发件地址">
            <Input />
          </Form.Item>
        </Col>
        <Col xs={24} md={12}>
          <Form.Item name={['email', 'defaultFromDisplayName']} label="默认发件名">
            <Input />
          </Form.Item>
        </Col>
        <Col xs={24} md={8}>
          <Form.Item name={['email', 'smtpHost']} label="SMTP主机">
            <Input />
          </Form.Item>
        </Col>
        <Col xs={24} md={8}>
          <Form.Item name={['email', 'smtpPort']} label="SMTP端口">
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col xs={24} md={8}>
          <Form.Item name={['email', 'smtpDomain']} label="SMTP域">
            <Input />
          </Form.Item>
        </Col>
        <Col xs={24} md={12}>
          <Form.Item name={['email', 'smtpUserName']} label="SMTP用户名">
            <Input />
          </Form.Item>
        </Col>
        <Col xs={24} md={12}>
          <Form.Item name={['email', 'smtpPassword']} label="SMTP密码">
            <Input.Password />
          </Form.Item>
        </Col>
        <SwitchItem name={['email', 'smtpEnableSsl']} label="启用SSL" />
        <SwitchItem name={['email', 'smtpUseDefaultCredentials']} label="使用默认凭据" />
      </Row>
    </>
  );
}

function SecurityFields() {
  return (
    <>
      <Divider>安全</Divider>
      <Row gutter={16}>
        <SwitchItem name={['security', 'allowOneConcurrentLoginPerUser']} label="单用户单会话" />
        <SwitchItem name={['security', 'useDefaultPasswordComplexitySettings']} label="使用默认密码策略" />
        <PasswordComplexityFields prefix={['security', 'passwordComplexity']} title="密码复杂度" />
        <PasswordComplexityFields prefix={['security', 'defaultPasswordComplexity']} title="默认密码复杂度" />
      </Row>
      <Divider>账号锁定</Divider>
      <Row gutter={16}>
        <SwitchItem name={['security', 'userLockOut', 'isEnabled']} label="启用锁定" />
        <Col xs={24} md={8}>
          <Form.Item name={['security', 'userLockOut', 'maxFailedAccessAttemptsBeforeLockout']} label="失败次数">
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col xs={24} md={8}>
          <Form.Item name={['security', 'userLockOut', 'defaultAccountLockoutSeconds']} label="锁定秒数">
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
        </Col>
      </Row>
      <Divider>二次验证</Divider>
      <Row gutter={16}>
        <SwitchItem name={['security', 'twoFactorLogin', 'isEnabledForApplication']} label="应用允许二次验证" />
        <SwitchItem name={['security', 'twoFactorLogin', 'isEnabled']} label="启用二次验证" />
        <SwitchItem name={['security', 'twoFactorLogin', 'isEmailProviderEnabled']} label="邮箱验证" />
        <SwitchItem name={['security', 'twoFactorLogin', 'isSmsProviderEnabled']} label="短信验证" />
        <SwitchItem name={['security', 'twoFactorLogin', 'isRememberBrowserEnabled']} label="记住浏览器" />
        <SwitchItem name={['security', 'twoFactorLogin', 'isGoogleAuthenticatorEnabled']} label="Google验证器" />
      </Row>
    </>
  );
}

function PasswordComplexityFields({ prefix, title }: { prefix: string[]; title: string }) {
  return (
    <Col xs={24}>
      <Divider>{title}</Divider>
      <Row gutter={16}>
        <SwitchItem name={[...prefix, 'requireDigit']} label="要求数字" />
        <SwitchItem name={[...prefix, 'requireLowercase']} label="要求小写" />
        <SwitchItem name={[...prefix, 'requireUppercase']} label="要求大写" />
        <SwitchItem name={[...prefix, 'requireNonAlphanumeric']} label="要求特殊字符" />
        <Col xs={24} md={8}>
          <Form.Item name={[...prefix, 'requiredLength']} label="最小长度">
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
        </Col>
      </Row>
    </Col>
  );
}

function BillingFields({ tax }: { tax: boolean }) {
  return (
    <>
      <Divider>账单</Divider>
      <Row gutter={16}>
        <Col xs={24} md={12}>
          <Form.Item name={['billing', 'legalName']} label="法定名称">
            <Input />
          </Form.Item>
        </Col>
        <Col xs={24} md={12}>
          <Form.Item name={['billing', 'address']} label="地址">
            <Input />
          </Form.Item>
        </Col>
        {tax ? (
          <Col xs={24} md={12}>
            <Form.Item name={['billing', 'taxVatNo']} label="税号">
              <Input />
            </Form.Item>
          </Col>
        ) : null}
      </Row>
    </>
  );
}

function LdapFields() {
  return (
    <>
      <Divider>LDAP</Divider>
      <Row gutter={16}>
        <SwitchItem name={['ldap', 'isModuleEnabled']} label="模块启用" />
        <SwitchItem name={['ldap', 'isEnabled']} label="LDAP登录启用" />
        <Col xs={24} md={8}>
          <Form.Item name={['ldap', 'domain']} label="域">
            <Input />
          </Form.Item>
        </Col>
        <Col xs={24} md={8}>
          <Form.Item name={['ldap', 'userName']} label="用户名">
            <Input />
          </Form.Item>
        </Col>
        <Col xs={24} md={8}>
          <Form.Item name={['ldap', 'password']} label="密码">
            <Input.Password />
          </Form.Item>
        </Col>
      </Row>
    </>
  );
}

function OtherFields() {
  return (
    <>
      <Divider>其他</Divider>
      <Row gutter={16}>
        <SwitchItem name={['otherSettings', 'isQuickThemeSelectEnabled']} label="快速主题选择" />
      </Row>
    </>
  );
}

function ExternalLoginFields() {
  return (
    <>
      <Divider>外部登录</Divider>
      <Row gutter={16}>
        <Col xs={24} md={12}>
          <Form.Item name={['externalLoginProviderSettings', 'facebook', 'appId']} label="Facebook AppId">
            <Input />
          </Form.Item>
        </Col>
        <Col xs={24} md={12}>
          <Form.Item name={['externalLoginProviderSettings', 'facebook', 'appSecret']} label="Facebook AppSecret">
            <Input.Password />
          </Form.Item>
        </Col>
        <Col xs={24} md={8}>
          <Form.Item name={['externalLoginProviderSettings', 'google', 'clientId']} label="Google ClientId">
            <Input />
          </Form.Item>
        </Col>
        <Col xs={24} md={8}>
          <Form.Item name={['externalLoginProviderSettings', 'google', 'clientSecret']} label="Google ClientSecret">
            <Input.Password />
          </Form.Item>
        </Col>
        <Col xs={24} md={8}>
          <Form.Item name={['externalLoginProviderSettings', 'google', 'userInfoEndpoint']} label="Google用户接口">
            <Input />
          </Form.Item>
        </Col>
        <Col xs={24} md={12}>
          <Form.Item name={['externalLoginProviderSettings', 'twitter', 'consumerKey']} label="Twitter ConsumerKey">
            <Input />
          </Form.Item>
        </Col>
        <Col xs={24} md={12}>
          <Form.Item name={['externalLoginProviderSettings', 'twitter', 'consumerSecret']} label="Twitter ConsumerSecret">
            <Input.Password />
          </Form.Item>
        </Col>
        <Col xs={24} md={12}>
          <Form.Item name={['externalLoginProviderSettings', 'microsoft', 'clientId']} label="Microsoft ClientId">
            <Input />
          </Form.Item>
        </Col>
        <Col xs={24} md={12}>
          <Form.Item name={['externalLoginProviderSettings', 'microsoft', 'clientSecret']} label="Microsoft ClientSecret">
            <Input.Password />
          </Form.Item>
        </Col>
      </Row>
    </>
  );
}

function SwitchItem({ name, label }: { name: (string | number)[]; label: string }) {
  return (
    <Col xs={24} md={8}>
      <Form.Item name={name} label={label} valuePropName="checked">
        <Switch />
      </Form.Item>
    </Col>
  );
}
