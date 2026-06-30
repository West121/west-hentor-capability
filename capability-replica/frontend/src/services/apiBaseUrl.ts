const productionApiBaseUrl = 'https://west-hentor-capability-api.west-hentor-capability-api.workers.dev';

export const baseURL = import.meta.env?.VITE_API_BASE_URL
  || (import.meta.env?.PROD ? productionApiBaseUrl : '');
