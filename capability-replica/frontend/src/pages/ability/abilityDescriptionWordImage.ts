import type { UeditorUploadOutput } from '../../types/domain';
import { safeAbilityDescriptionHtml } from './abilityDescriptionHtml.ts';
import { defaultUeditorImageBaseUrl, uploadedImageAttributes } from './abilityDescriptionImageUpload.ts';

export interface PendingWordImage {
  key: string;
  wordImage: string;
  label: string;
  alt: string;
  title: string;
  width: string;
  height: string;
}

export interface WordImageReplacement {
  wordImage: string;
  uploaded: UeditorUploadOutput;
}

// Mirrors UEditor's wordimage command by reading pending word_img placeholders.
export function pendingWordImagesFromHtml(value?: string): PendingWordImage[] {
  const safeHtml = safeAbilityDescriptionHtml(value);
  if (safeHtml === '暂无说明') return [];

  const seen = new Set<string>();
  const images: PendingWordImage[] = [];
  for (const match of safeHtml.matchAll(/<\s*img\b([^>]*)>/gi)) {
    const attributes = match[1] ?? '';
    const wordImage = attributeValue(attributes, 'word_img');
    if (!isLocalWordImageSrc(wordImage) || seen.has(wordImage)) continue;
    seen.add(wordImage);
    images.push({
      key: wordImage,
      wordImage,
      label: wordImageLabel(wordImage),
      alt: attributeValue(attributes, 'alt'),
      title: attributeValue(attributes, 'title'),
      width: dimensionAttributeValue(attributes, 'width'),
      height: dimensionAttributeValue(attributes, 'height'),
    });
  }
  return images;
}

export function replacePendingWordImages(
  value: string,
  replacements: WordImageReplacement[],
  apiBaseUrl = defaultUeditorImageBaseUrl,
) {
  const replacementMap = new Map(
    replacements.map((item) => [item.wordImage, uploadedImageAttributes(item.uploaded, apiBaseUrl)]),
  );
  if (!replacementMap.size) return safeAbilityDescriptionHtml(value);

  const replaced = value.replace(/<\s*img\b([^>]*)\/?>/gi, (tag, attributes: string) => {
    const wordImage = attributeValue(attributes, 'word_img');
    const uploaded = replacementMap.get(wordImage);
    if (!uploaded) return tag;

    const width = dimensionAttributeValue(attributes, 'width');
    const height = dimensionAttributeValue(attributes, 'height');
    return [
      `<img src="${escapeAttribute(uploaded.src)}"`,
      width ? `width="${escapeAttribute(width)}"` : '',
      height ? `height="${escapeAttribute(height)}"` : '',
      uploaded.alt ? `alt="${escapeAttribute(uploaded.alt)}"` : '',
      uploaded.title ? `title="${escapeAttribute(uploaded.title)}"` : '',
      '>',
    ].filter(Boolean).join(' ');
  });

  return safeAbilityDescriptionHtml(replaced);
}

function attributeValue(rawAttributes: string, name: string) {
  const pattern = new RegExp(`${name}\\s*=\\s*(?:"([^"]*)"|'([^']*)'|([^\\s>]+))`, 'i');
  const match = rawAttributes.match(pattern);
  return decodeAttributeValue(match?.[1] ?? match?.[2] ?? match?.[3] ?? '');
}

function dimensionAttributeValue(rawAttributes: string, name: string) {
  const value = attributeValue(rawAttributes, name).trim();
  return /^\d+(?:\.\d+)?(?:px|pt|cm|mm|in|%)?$/i.test(value) ? value : '';
}

function wordImageLabel(value: string) {
  const clean = value.replace(/^file:\/+/i, '').replace(/\\/g, '/');
  const lastPart = clean.split('/').filter(Boolean).pop() ?? value;
  try {
    return decodeURIComponent(lastPart);
  } catch {
    return lastPart;
  }
}

function isLocalWordImageSrc(value: string) {
  return value.trim().toLowerCase().startsWith('file:/');
}

function escapeAttribute(value: string) {
  return value.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function decodeAttributeValue(value: string) {
  return value
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&amp;/g, '&');
}
