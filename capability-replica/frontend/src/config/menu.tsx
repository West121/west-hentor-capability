import {
  DashboardOutlined,
  DatabaseOutlined,
  DeploymentUnitOutlined,
  ExperimentOutlined,
  AppstoreOutlined,
  HddOutlined,
  GlobalOutlined,
  ApiOutlined,
  MessageOutlined,
  SkinOutlined,
  SlidersOutlined,
  SearchOutlined,
  SettingOutlined,
  StarOutlined,
  ToolOutlined,
} from '@ant-design/icons';
import type { ReactNode } from 'react';

// Menu copied from wwwroot/assets/tmp/app-data.json.
export interface AppMenuItem {
  key: string;
  label: string;
  icon?: ReactNode;
  permission?: string;
  children?: AppMenuItem[];
}

export const appMenu: AppMenuItem[] = [
  { key: '/dashboard/v1', label: '工作台', icon: <DashboardOutlined /> },
  { key: '/dashboard/tenant', label: '租户看板', icon: <DashboardOutlined />, permission: 'Pages.Tenant.Dashboard' },
  { key: '/ablibity/list', label: '能力表管理', icon: <DatabaseOutlined />, permission: 'Pages.AbilityManagement' },
  { key: '/ablibity/subcontract-ability', label: '分包能力', icon: <DeploymentUnitOutlined />, permission: 'Pages.AbilityManagement' },
  { key: '/ablibity/query', label: '能力表查询', icon: <SearchOutlined />, permission: 'Pages.AbilityQuery' },
  { key: '/my/list', label: '我的收藏', icon: <StarOutlined /> },
  { key: '/account/chat', label: '聊天', icon: <MessageOutlined /> },
  { key: '/demo/ui-components', label: '示例组件', icon: <ExperimentOutlined />, permission: 'Pages.DemoUiComponents' },
  {
    key: 'system',
    label: '系统管理',
    icon: <SettingOutlined />,
    permission: 'Pages.Administration',
    children: [
      { key: '/sys/orgunits', label: '业务线管理', permission: 'Pages.Administration.OrganizationUnits' },
      { key: '/sys/roles', label: '角色管理', permission: 'Pages.Administration.Roles' },
      { key: '/sys/users', label: '用户管理', permission: 'Pages.Administration.Users' },
      { key: '/dashboard/host', label: '宿主看板', icon: <DashboardOutlined />, permission: 'Pages.Administration.Host.Dashboard' },
      { key: '/sys/languages', label: '语言管理', icon: <GlobalOutlined />, permission: 'Pages.Administration.Languages' },
      { key: '/sys/settings', label: '系统设置', icon: <SlidersOutlined />, permission: 'Pages.Administration.Host.Settings' },
      { key: '/sys/tenants', label: '租户与订阅', permission: 'Pages.Tenants' },
      { key: '/sys/cache', label: '缓存管理', icon: <ToolOutlined />, permission: 'Pages.Administration.Host.Maintenance' },
      { key: '/sys/web-logs', label: 'Web日志', icon: <HddOutlined />, permission: 'Pages.Administration.Host.Maintenance' },
      {
        key: '/sys/dynamic-parameters',
        label: '动态参数',
        icon: <AppstoreOutlined />,
        permission: 'Pages.Administration.DynamicParameters',
      },
      {
        key: '/sys/ui-customization',
        label: 'UI定制',
        icon: <SkinOutlined />,
        permission: 'Pages.Administration.UiCustomization',
      },
      {
        key: '/sys/webhooks',
        label: 'Webhook订阅',
        icon: <ApiOutlined />,
        permission: 'Pages.Administration.WebhookSubscription',
      },
      {
        key: '/business/org-ability-property-setting',
        label: '能力表设置',
        permission: 'Pages.AbilityManagement.AbilitySetting',
      },
      { key: '/business/lab', label: '实验室管理', permission: 'Pages.Administration.Laboratory' },
      { key: '/business/sample', label: '样品管理', permission: 'Pages.AbilityManagement.Sample' },
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
];
