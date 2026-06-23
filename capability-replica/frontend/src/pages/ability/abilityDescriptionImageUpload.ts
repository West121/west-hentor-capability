import type { UeditorUploadOutput } from '../../types/domain';

export const defaultUeditorImageBaseUrl = import.meta.env?.VITE_API_BASE_URL
  ?? globalThis.location?.origin
  ?? '';

export function uploadedImageAttributes(uploaded: UeditorUploadOutput, apiBaseUrl = defaultUeditorImageBaseUrl) {
  if (uploaded.state !== 'SUCCESS' || !uploaded.url) {
    throw new Error(uploaded.state || 'ImageUploadFailed');
  }
  const title = uploaded.title || uploaded.original || 'image';
  return {
    src: absoluteImageUrl(uploaded.url, apiBaseUrl),
    alt: title,
    title,
  };
}

function absoluteImageUrl(url: string, apiBaseUrl: string) {
  if (/^(https?:|data:|blob:)/i.test(url)) {
    return url;
  }
  if (!apiBaseUrl) {
    return url;
  }
  return new URL(url, apiBaseUrl).toString();
}
