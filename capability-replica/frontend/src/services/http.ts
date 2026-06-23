import axios from 'axios';
import { useAuthStore } from '../store/authStore';

export const baseURL = import.meta.env?.VITE_API_BASE_URL ?? '';

// Axios client unwraps the ABP envelope used by the copied backend.
export const http = axios.create({ baseURL, withCredentials: false });

http.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

function handleUnauthorized() {
  useAuthStore.getState().logout();
  if (globalThis.location?.pathname !== '/login') {
    globalThis.location.assign('/login');
  }
}

http.interceptors.response.use((response) => {
  if (response.data?.unAuthorizedRequest || response.status === 401) {
    handleUnauthorized();
    throw new Error(response.data?.error?.message ?? '未登录或登录已过期');
  }
  if (response.data?.__abp && response.data.success === false) {
    throw new Error(response.data.error?.message ?? '请求失败');
  }
  return response;
}, (error) => {
  if (error.response?.status === 401 || error.response?.data?.unAuthorizedRequest) {
    handleUnauthorized();
  }
  return Promise.reject(error);
});

export async function apiGet<T>(url: string): Promise<T> {
  const response = await http.get(url);
  return response.data?.result ?? response.data;
}

export async function apiPost<T>(url: string, data?: unknown): Promise<T> {
  const response = data === undefined ? await http.post(url) : await http.post(url, data);
  return response.data?.result ?? response.data;
}

export async function apiPut<T>(url: string, data?: unknown): Promise<T> {
  const response = data === undefined ? await http.put(url) : await http.put(url, data);
  return response.data?.result ?? response.data;
}

export async function apiDelete<T>(url: string): Promise<T> {
  const response = await http.delete(url);
  return response.data?.result ?? response.data;
}

export async function apiUpload<T>(url: string, file: File, fields?: Record<string, string | number | undefined>): Promise<T> {
  const form = new FormData();
  form.append('file', file);
  Object.entries(fields ?? {}).forEach(([key, value]) => {
    if (value !== undefined) {
      form.append(key, String(value));
    }
  });
  const response = await http.post(url, form);
  return response.data?.result ?? response.data;
}

export async function apiDownload(url: string, params: Record<string, string | undefined>): Promise<Blob> {
  const response = await http.get(url, { params, responseType: 'blob' });
  return response.data;
}
