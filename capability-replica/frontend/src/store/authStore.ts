import { create } from 'zustand';

// Stores local demo auth state and permission names.
interface AuthState {
  token?: string;
  refreshToken?: string;
  userName: string;
  permissions: string[];
  login: (token: string, permissions: string[], userName: string, refreshToken?: string) => void;
  logout: () => void;
  can: (permission?: string) => boolean;
}

const savedToken = localStorage.getItem('capability-token') ?? undefined;
const savedRefreshToken = localStorage.getItem('capability-refresh-token') ?? undefined;

export const useAuthStore = create<AuthState>((set, get) => ({
  token: savedToken,
  refreshToken: savedRefreshToken,
  userName: localStorage.getItem('capability-user') ?? 'Admin',
  permissions: JSON.parse(localStorage.getItem('capability-permissions') ?? '[]'),
  login: (token, permissions, userName, refreshToken) => {
    localStorage.setItem('capability-token', token);
    localStorage.setItem('capability-user', userName);
    localStorage.setItem('capability-permissions', JSON.stringify(permissions));
    if (refreshToken) {
      localStorage.setItem('capability-refresh-token', refreshToken);
    }
    set({ token, permissions, userName, refreshToken: refreshToken ?? get().refreshToken });
  },
  logout: () => {
    localStorage.removeItem('capability-token');
    localStorage.removeItem('capability-refresh-token');
    localStorage.removeItem('capability-user');
    localStorage.removeItem('capability-permissions');
    set({ token: undefined, refreshToken: undefined, permissions: [], userName: 'Admin' });
  },
  can: (permission) => !permission || get().permissions.includes(permission),
}));
