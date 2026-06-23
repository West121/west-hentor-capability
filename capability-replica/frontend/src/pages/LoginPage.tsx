import { useState } from 'react';
import { GlobalOutlined, LockOutlined, MailOutlined, UserOutlined } from '@ant-design/icons';
import { Alert, App as AntdApp, Button, Checkbox, Form, Input } from 'antd';
import { useNavigate, useSearchParams } from 'react-router-dom';
import logo from '../assets/sgslogo.png';
import { api } from '../services/api';
import { useAuthStore } from '../store/authStore';

// Login layout mirrors the original public SGS capability query landing page.
export default function LoginPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { message } = AntdApp.useApp();
  const login = useAuthStore((state) => state.login);
  const [requiresTwoFactor, setRequiresTwoFactor] = useState(false);
  const [twoFactorUserId, setTwoFactorUserId] = useState<number>();
  const [sendingTwoFactorCode, setSendingTwoFactorCode] = useState(false);
  const returnUrl = searchParams.get('returnUrl') ?? '';
  const nextUrl = returnUrl.startsWith('/') && !returnUrl.startsWith('//') ? returnUrl : '/dashboard/v1';

  async function onFinish(values: {
    userName: string;
    password: string;
    rememberClient?: boolean;
    twoFactorVerificationCode?: string;
  }) {
    try {
      const auth = await api.login(values);
      if (auth.requiresTwoFactorVerification) {
        setRequiresTwoFactor(true);
        setTwoFactorUserId(auth.userId);
        message.info('请发送二次验证码后输入收到的安全码');
        return;
      }
      if (auth.shouldResetPassword) {
        message.warning('需要先重置密码');
        return;
      }
      login(auth.accessToken, [], values.userName, auth.refreshToken);
      const session = await api.session();
      login(auth.accessToken, session.permissions, session.user.name, auth.refreshToken);
      message.success('登录成功');
      navigate(nextUrl);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '登录失败');
    }
  }

  async function sendTwoFactorCode() {
    try {
      setSendingTwoFactorCode(true);
      await api.sendTwoFactorAuthCode(twoFactorUserId, 'Email');
      message.success('二次验证码已发送');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '发送二次验证码失败');
    } finally {
      setSendingTwoFactorCode(false);
    }
  }

  return (
    <div className="login-shell">
      <button className="login-language" aria-label="切换语言">
        <GlobalOutlined />
      </button>
      <div className="login-constellation login-constellation-left" aria-hidden="true" />
      <div className="login-constellation login-constellation-top-right" aria-hidden="true" />
      <div className="login-constellation login-constellation-right" aria-hidden="true" />
      <div className="login-constellation login-constellation-bottom-left" aria-hidden="true" />
      <main className="login-panel">
        <div className="login-brand">
          <img src={logo} alt="SGS" className="login-logo" />
          <h1>SGS NR实验室检测能力查询系统</h1>
        </div>
        <div className="login-tab">账户密码登录</div>
        <Form
          className="login-form"
          initialValues={{ userName: 'admin', password: '123qwe', rememberClient: true }}
          onFinish={onFinish}
        >
          {requiresTwoFactor ? (
            <Alert
              type="info"
              showIcon
              style={{ marginBottom: 18 }}
              message="需要二次验证"
              description={
                <Button
                  icon={<MailOutlined />}
                  loading={sendingTwoFactorCode}
                  onClick={sendTwoFactorCode}
                  disabled={!twoFactorUserId}
                >
                  发送邮箱验证码
                </Button>
              }
            />
          ) : null}
          <Form.Item name="userName" rules={[{ required: true, message: '请输入账号' }]}>
            <Input prefix={<UserOutlined />} autoComplete="username" />
          </Form.Item>
          <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password prefix={<LockOutlined />} autoComplete="current-password" />
          </Form.Item>
          {requiresTwoFactor ? (
            <Form.Item name="twoFactorVerificationCode" rules={[{ required: true, message: '请输入二次验证码' }]}>
              <Input autoComplete="one-time-code" />
            </Form.Item>
          ) : null}
          <Form.Item name="rememberClient" valuePropName="checked" className="login-remember">
            <Checkbox>自动登录</Checkbox>
          </Form.Item>
          <Button type="primary" htmlType="submit" block size="large" className="login-submit">
            登录
          </Button>
        </Form>
        <footer className="login-footer">
          Copyright © 2020 <span>SGS MIN</span>
        </footer>
      </main>
    </div>
  );
}
