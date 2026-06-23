import { useEffect, useState } from 'react';
import { Avatar, Button, Dropdown, Grid, Layout, Menu } from 'antd';
import type { MenuProps } from 'antd';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import {
  DashboardOutlined,
  DatabaseOutlined,
  HddOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  SearchOutlined,
  SettingOutlined,
  StarOutlined,
  ProfileOutlined,
  SafetyCertificateOutlined,
  UserOutlined,
} from '@ant-design/icons';
import logo from '../assets/sgslogo.png';
import type { AppMenuItem } from '../config/menu';
import { useAuthStore } from '../store/authStore';

const { Header, Sider, Content } = Layout;

const productionMenu: Array<{ title: string; items: AppMenuItem[] }> = [
  {
    title: '主导航',
    items: [
      { key: '/dashboard/v1', label: '工作台', icon: <DashboardOutlined /> },
      { key: '/ablibity/list', label: '能力表管理', icon: <DatabaseOutlined />, permission: 'Pages.AbilityManagement' },
      { key: '/ablibity/query', label: '能力表查询', icon: <SearchOutlined />, permission: 'Pages.AbilityQuery' },
      { key: '/my/list', label: '我的收藏', icon: <StarOutlined /> },
    ],
  },
  {
    title: '系统维护',
    items: [
      {
        key: 'system',
        label: '系统管理',
        icon: <SettingOutlined />,
        permission: 'Pages.Administration',
        children: [
          { key: '/sys/orgunits', label: '业务线管理', permission: 'Pages.Administration.OrganizationUnits' },
          { key: '/sys/roles', label: '角色管理', permission: 'Pages.Administration.Roles' },
          { key: '/sys/users', label: '用户管理', permission: 'Pages.Administration.Users' },
          { key: '/business/org-ability-property-setting', label: '能力表设置', permission: 'Pages.AbilityManagement.AbilitySetting' },
          { key: '/business/lab', label: '实验室管理', permission: 'Pages.Administration.Laboratory' },
          { key: '/sys/standard-update', label: '标准方法更新', permission: 'Pages.Administration.StandardUpdate' },
        ],
      },
      {
        key: 'logs',
        label: '日志',
        icon: <HddOutlined />,
        permission: 'Pages.Log',
        children: [
          { key: '/logs/ability_history', label: '能力表历史记录', permission: 'Pages.Log.AbilityHistory' },
          { key: '/logs/audit_log', label: '操作日志', permission: 'Pages.Administration.AuditLogs' },
        ],
      },
    ],
  },
];

// Main admin shell copied from the Alain-style Angular layout.
export default function AppLayout() {
  const location = useLocation();
  const navigate = useNavigate();
  const userName = useAuthStore((state) => state.userName);
  const can = useAuthStore((state) => state.can);
  const logout = useAuthStore((state) => state.logout);
  const screens = Grid.useBreakpoint();
  const compact = screens.md === false;
  const isWorkbench = location.pathname === '/dashboard/v1';
  const showBreadcrumb = !isWorkbench && location.pathname !== '/ablibity/list';
  const [collapsed, setCollapsed] = useState(() => compact);

  const visibleMenu = groupedMenuItems(can);
  const routeOpenKeys = openKeysForPath(flatMenu(), location.pathname);
  const [openKeys, setOpenKeys] = useState<string[]>(routeOpenKeys);
  const breadcrumbs = breadcrumbLabels(location.pathname);
  const accountMenu: MenuProps['items'] = [
    { key: 'profile', label: '个人中心', icon: <ProfileOutlined /> },
    { key: 'security', label: '账号安全', icon: <SafetyCertificateOutlined /> },
    { key: 'logout', label: '退出', icon: <LogoutOutlined /> },
  ];

  useEffect(() => {
    setOpenKeys((current) => Array.from(new Set([...current, ...openKeysForPath(flatMenu(), location.pathname)])));
  }, [location.pathname]);

  useEffect(() => {
    if (compact) {
      setCollapsed(true);
    }
  }, [compact]);

  return (
    <Layout className="sgs-admin-shell">
      <Sider
        width={212}
        collapsedWidth={64}
        collapsed={collapsed}
        trigger={null}
        theme="light"
        className="sgs-admin-sider"
      >
        <div className="sgs-admin-logo">
          <img src={logo} alt="SGS" />
        </div>
        <Menu
          theme="light"
          mode="inline"
          selectedKeys={[location.pathname]}
          openKeys={openKeys}
          items={visibleMenu}
          onOpenChange={(keys) => setOpenKeys(keys as string[])}
          onClick={({ key }) => key.startsWith('/') && navigate(key)}
        />
      </Sider>
      <Layout style={{ minWidth: 0 }}>
        <Header className="sgs-admin-header">
          <div className="sgs-admin-header-left">
            <Button
              type="text"
              className="sgs-admin-fold"
              icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
              onClick={() => setCollapsed((value) => !value)}
            />
            {showBreadcrumb ? (
              <div className="sgs-admin-breadcrumb">
                <span>home</span>
                {breadcrumbs.map((label) => (
                  <span key={label} className="sgs-admin-breadcrumb-item">
                    {label}
                  </span>
                ))}
              </div>
            ) : null}
          </div>
          <Dropdown
            menu={{
              items: accountMenu,
              onClick: ({ key }) => {
                if (key === 'profile') {
                  navigate('/account/profile');
                }
                if (key === 'security') {
                  navigate('/account/security');
                }
                if (key === 'logout') {
                  logout();
                  navigate('/login');
                }
              },
            }}
          >
            <Button type="text" className="sgs-admin-user" aria-label={userName || '账号菜单'}>
              <Avatar icon={<UserOutlined />} />
            </Button>
          </Dropdown>
        </Header>
        <Content className={`sgs-admin-content${isWorkbench ? ' sgs-admin-content-workbench' : ''}`}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}

function filterMenu(items: AppMenuItem[], can: (permission?: string) => boolean): AppMenuItem[] {
  return items
    .filter((item) => can(item.permission))
    .map((item) => ({ ...item, children: item.children ? filterMenu(item.children, can) : undefined }));
}

function groupedMenuItems(can: (permission?: string) => boolean): MenuProps['items'] {
  return productionMenu
    .map((group) => {
      const children = toMenuItems(filterMenu(group.items, can));
      return children?.length ? { type: 'group' as const, label: group.title, children } : null;
    })
    .filter(Boolean) as MenuProps['items'];
}

function toMenuItems(items: AppMenuItem[]): MenuProps['items'] {
  return items.map((item) => ({
    key: item.key,
    label: item.label,
    icon: item.icon,
    ...(item.children?.length ? { children: toMenuItems(item.children) } : {}),
  }));
}

function flatMenu() {
  return productionMenu.flatMap((group) => group.items);
}

function openKeysForPath(items: AppMenuItem[], pathname: string, parents: string[] = []): string[] {
  for (const item of items) {
    if (item.key === pathname) {
      return parents;
    }
    if (item.children?.length) {
      const found = openKeysForPath(item.children, pathname, [...parents, item.key]);
      if (found.length) {
        return found;
      }
    }
  }
  return [];
}

function breadcrumbLabels(pathname: string) {
  const labels = findBreadcrumb(flatMenu(), pathname);
  return labels.length ? labels : [];
}

function findBreadcrumb(items: AppMenuItem[], pathname: string, parents: string[] = []): string[] {
  for (const item of items) {
    const labels = [...parents, item.label];
    if (item.key === pathname) {
      return labels;
    }
    if (item.children?.length) {
      const found = findBreadcrumb(item.children, pathname, labels);
      if (found.length) {
        return found;
      }
    }
  }
  return [];
}
