import { Navigate, Route, Routes } from 'react-router-dom';
import AppLayout from './layouts/AppLayout';
import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/DashboardPage';
import HostDashboardPage from './pages/dashboard/HostDashboardPage';
import TenantDashboardPage from './pages/dashboard/TenantDashboardPage';
import AbilityListPage from './pages/ability/AbilityListPage';
import AbilityQueryPage from './pages/ability/AbilityQueryPage';
import FavoritePage from './pages/ability/FavoritePage';
import SubcontractAbilityPage from './pages/ability/SubcontractAbilityPage';
import OrgAbilitySettingPage from './pages/business/OrgAbilitySettingPage';
import LabPage from './pages/business/LabPage';
import SamplePage from './pages/business/SamplePage';
import OrgUnitsPage from './pages/system/OrgUnitsPage';
import RolesPage from './pages/system/RolesPage';
import UsersPage from './pages/system/UsersPage';
import StandardUpdatePage from './pages/system/StandardUpdatePage';
import LanguagesPage from './pages/system/LanguagesPage';
import CacheManagementPage from './pages/system/CacheManagementPage';
import DynamicParametersPage from './pages/system/DynamicParametersPage';
import SettingsPage from './pages/system/SettingsPage';
import UiCustomizationPage from './pages/system/UiCustomizationPage';
import WebhooksPage from './pages/system/WebhooksPage';
import WebLogsPage from './pages/system/WebLogsPage';
import TenantsEditionsPage from './pages/system/TenantsEditionsPage';
import AbilityHistoryPage from './pages/logs/AbilityHistoryPage';
import AuditLogPage from './pages/logs/AuditLogPage';
import ProfilePage from './pages/account/ProfilePage';
import NotificationsPage from './pages/account/NotificationsPage';
import ChatPage from './pages/account/ChatPage';
import AccountSecurityPage from './pages/account/AccountSecurityPage';
import DemoUiComponentsPage from './pages/demo/DemoUiComponentsPage';
import TemplateDemoPage from './pages/TemplateDemoPage';
import ExceptionPage from './pages/ExceptionPage';
import NotFoundPage from './pages/NotFoundPage';
import { templateDemoRoutes } from './config/templateRoutes';
import { useAuthStore } from './store/authStore';

// Defines the copied route table from the Angular application.
export default function App() {
  const token = useAuthStore((state) => state.token);

  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/passport/login" element={<LoginPage />} />
      <Route element={token ? <AppLayout /> : <Navigate to="/login" replace />}>
        <Route index element={<Navigate to="/dashboard/v1" replace />} />
        <Route path="/dashboard/v1" element={<DashboardPage />} />
        <Route path="/dashboard/host" element={<HostDashboardPage />} />
        <Route path="/dashboard/tenant" element={<TenantDashboardPage />} />
        <Route path="/ablibity/list" element={<AbilityListPage />} />
        <Route path="/ablibity/subcontract" element={<SubcontractAbilityPage />} />
        <Route path="/ablibity/subcontract-ability" element={<SubcontractAbilityPage />} />
        <Route path="/ablibity/query" element={<AbilityQueryPage />} />
        <Route path="/my/list" element={<FavoritePage />} />
        <Route path="/business/org-ability-property-setting" element={<OrgAbilitySettingPage />} />
        <Route path="/business/lab" element={<LabPage />} />
        <Route path="/business/sampleType" element={<SamplePage />} />
        <Route path="/business/sample" element={<SamplePage />} />
        <Route path="/sys/orgunits" element={<OrgUnitsPage />} />
        <Route path="/sys/roles" element={<RolesPage />} />
        <Route path="/sys/users" element={<UsersPage />} />
        <Route path="/sys/languages" element={<LanguagesPage />} />
        <Route path="/sys/settings" element={<SettingsPage />} />
        <Route path="/sys/cache" element={<CacheManagementPage />} />
        <Route path="/sys/web-logs" element={<WebLogsPage />} />
        <Route path="/sys/dynamic-parameters" element={<DynamicParametersPage />} />
        <Route path="/sys/ui-customization" element={<UiCustomizationPage />} />
        <Route path="/sys/webhooks" element={<WebhooksPage />} />
        <Route path="/sys/tenants" element={<TenantsEditionsPage />} />
        <Route path="/sys/standard-update" element={<StandardUpdatePage />} />
        <Route path="/sys/auditlogs" element={<AuditLogPage />} />
        <Route path="/logs/ability_history" element={<AbilityHistoryPage />} />
        <Route path="/logs/audit_log" element={<AuditLogPage />} />
        <Route path="/account/profile" element={<ProfilePage />} />
        <Route path="/account/notifications" element={<NotificationsPage />} />
        <Route path="/account/chat" element={<ChatPage />} />
        <Route path="/account/security" element={<AccountSecurityPage />} />
        <Route path="/demo/ui-components" element={<DemoUiComponentsPage />} />
        {templateDemoRoutes.map((route) => (
          <Route
            key={route.path}
            path={route.path}
            element={route.redirectTo ? <Navigate to={route.redirectTo} replace /> : <TemplateDemoPage route={route} />}
          />
        ))}
        <Route path="/exception/403" element={<ExceptionPage status="403" title="403" subTitle="无权访问该页面" />} />
        <Route path="/exception/404" element={<ExceptionPage status="404" title="404" subTitle="页面不存在" />} />
        <Route path="/exception/500" element={<ExceptionPage status="500" title="500" subTitle="服务器错误" />} />
        <Route
          path="/exception/trigger"
          element={<ExceptionPage status="500" title="500" subTitle="异常触发页" />}
        />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
