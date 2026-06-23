import { safeAbilityDescriptionHtml } from './abilityDescriptionHtml.ts';

// The editor needs an empty paragraph, while the read-only display uses "暂无说明".
export function abilityDescriptionEditorContent(value?: string) {
  const source = (value ?? '').trim();
  if (!source) return '<p></p>';
  return safeAbilityDescriptionHtml(source);
}

export function abilityDescriptionEditorOutput(value?: string) {
  const safeHtml = safeAbilityDescriptionHtml(value);
  if (safeHtml === '暂无说明') return '';
  if (/<img\b/i.test(safeHtml)) return safeHtml;
  const text = safeHtml.replace(/<[^>]*>/g, '').replace(/&nbsp;/g, ' ').trim();
  return text ? safeHtml : '';
}
