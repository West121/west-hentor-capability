const emptyAbilityDescription = '暂无说明';
const wordImagePendingClass = 'word-image-pending';
const wordImagePendingTitle = '本地 Word 图片待上传';
const wordImagePlaceholderSrc = 'data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw==';
const allowedTags = new Set([
  'a',
  'b',
  'blockquote',
  'br',
  'code',
  'div',
  'em',
  'h1',
  'h2',
  'h3',
  'h4',
  'h5',
  'h6',
  'i',
  'img',
  'li',
  'ol',
  'p',
  'pre',
  's',
  'span',
  'strike',
  'strong',
  'table',
  'tbody',
  'td',
  'th',
  'thead',
  'tr',
  'u',
  'ul',
]);
const styleAllowedTags = new Set([
  'a',
  'blockquote',
  'div',
  'h1',
  'h2',
  'h3',
  'h4',
  'h5',
  'h6',
  'li',
  'p',
  'span',
  'td',
  'th',
]);

// Preserves copied UEditor-style content while removing executable HTML.
export function safeAbilityDescriptionHtml(value?: string) {
  const source = normalizeWordPasteArtifacts((value ?? '').trim());
  if (!source) return emptyAbilityDescription;

  return source
    .replace(/<\s*(script|style)\b[^>]*>[\s\S]*?<\s*\/\s*\1\s*>/gi, '')
    .replace(/<\/?([a-z][a-z0-9-]*)\b([^>]*)>/gi, (tag, rawName: string, rawAttributes: string) => {
      const name = rawName.toLowerCase();
      if (!allowedTags.has(name)) return '';
      if (tag.startsWith('</')) return `</${name}>`;
      return `<${name}${safeAttributes(name, rawAttributes)}>`;
    });
}

function normalizeWordPasteArtifacts(value: string) {
  return value
    .replace(/<\s*v:shape\b([^>]*)>[\s\S]*?<\s*\/\s*v:shape\s*>/gi, wordShapeToImage)
    .replace(/<\s*p\b[^>]*class\s*=\s*["']?MsoHeading["']?[^>]*>([\s\S]*?)<\s*\/\s*p\s*>/gi, '<p><strong>$1</strong></p>')
    .replace(/<\s*img\b([^>]*)\/?>/gi, localWordImageToPlaceholder)
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/<\s*o:p\b[^>]*>[\s\S]*?<\s*\/\s*o:p\s*>/gi, '')
    .replace(/<\/?\s*[a-z]+:[^>]*>/gi, '');
}

function wordShapeToImage(shapeHtml: string, shapeAttributes: string) {
  if (/Bitmap/i.test(shapeHtml)) return '';
  const src = attributeValue(shapeHtml, 'src');
  if (!src || !isSafeImageSrc(src)) return '';
  const width = cssValue(shapeAttributes, 'width');
  const height = cssValue(shapeAttributes, 'height');
  const title = attributeValue(shapeHtml, 'o:title');
  return `<img src="${escapeAttribute(src)}"${width ? ` width="${escapeAttribute(width)}"` : ''}${height ? ` height="${escapeAttribute(height)}"` : ''}${title ? ` title="${escapeAttribute(title)}"` : ''}>`;
}

function localWordImageToPlaceholder(imgTag: string, rawAttributes: string) {
  const src = attributeValue(rawAttributes, 'src');
  const wordImage = attributeValue(rawAttributes, 'word_img') || (isLocalWordImageSrc(src) ? src : '');
  if (!wordImage || !isLocalWordImageSrc(wordImage)) return imgTag;
  const width = dimensionAttributeValue(rawAttributes, 'width');
  const height = dimensionAttributeValue(rawAttributes, 'height');
  const alt = attributeValue(rawAttributes, 'alt');
  const title = attributeValue(rawAttributes, 'title') || wordImagePendingTitle;
  return [
    `<img src="${wordImagePlaceholderSrc}"`,
    width ? `width="${escapeAttribute(width)}"` : '',
    height ? `height="${escapeAttribute(height)}"` : '',
    alt ? `alt="${escapeAttribute(alt)}"` : '',
    `title="${escapeAttribute(title)}"`,
    `word_img="${escapeAttribute(wordImage)}"`,
    `class="${wordImagePendingClass}">`,
  ].filter(Boolean).join(' ');
}

function safeAttributes(tagName: string, rawAttributes: string) {
  if (tagName === 'img') {
    return safeImageAttributes(rawAttributes);
  }
  const style = styleAllowedTags.has(tagName) ? safeStyleAttribute(rawAttributes) : '';
  if (tagName === 'table') return safeTableAttributes(rawAttributes);
  if (tagName === 'td' || tagName === 'th') return `${safeCellAttributes(rawAttributes)}${style}`;
  if (tagName !== 'a') return style;
  const href = attributeValue(rawAttributes, 'href');
  const hrefAttribute = href && isSafeHref(href) ? ` href="${escapeAttribute(href)}"` : '';
  return `${hrefAttribute}${style}`;
}

function safeTableAttributes(rawAttributes: string) {
  return [
    safeDimensionAttribute(rawAttributes, 'width'),
    safeDimensionAttribute(rawAttributes, 'height'),
    boundedIntegerAttribute(rawAttributes, 'border', 0, 99),
    boundedIntegerAttribute(rawAttributes, 'cellpadding', 0, 99),
    boundedIntegerAttribute(rawAttributes, 'cellspacing', 0, 99),
  ]
    .filter(Boolean)
    .join(' ')
    .replace(/^(.+)$/, ' $1');
}

function safeCellAttributes(rawAttributes: string) {
  return [
    safeDimensionAttribute(rawAttributes, 'width'),
    safeDimensionAttribute(rawAttributes, 'height'),
    boundedIntegerAttribute(rawAttributes, 'colspan', 1, 99),
    boundedIntegerAttribute(rawAttributes, 'rowspan', 1, 99),
  ]
    .filter(Boolean)
    .join(' ')
    .replace(/^(.+)$/, ' $1');
}

function boundedIntegerAttribute(rawAttributes: string, name: string, min: number, max: number) {
  const value = attributeValue(rawAttributes, name).trim();
  if (!/^\d+$/.test(value)) return '';
  const numberValue = Number(value);
  return numberValue >= min && numberValue <= max ? `${name}="${numberValue}"` : '';
}

function safeDimensionAttribute(rawAttributes: string, name: string) {
  const value = dimensionAttributeValue(rawAttributes, name);
  return value ? `${name}="${escapeAttribute(value)}"` : '';
}

function safeImageAttributes(rawAttributes: string) {
  const src = attributeValue(rawAttributes, 'src');
  const alt = attributeValue(rawAttributes, 'alt');
  const wordImage = attributeValue(rawAttributes, 'word_img');
  const safeWordImage = isLocalWordImageSrc(wordImage) ? wordImage : '';
  const title = attributeValue(rawAttributes, 'title') || (safeWordImage ? wordImagePendingTitle : '');
  const width = dimensionAttributeValue(rawAttributes, 'width');
  const height = dimensionAttributeValue(rawAttributes, 'height');
  return [
    src && isSafeImageSrc(src) ? `src="${escapeAttribute(src)}"` : safeWordImage ? `src="${wordImagePlaceholderSrc}"` : '',
    width ? `width="${escapeAttribute(width)}"` : '',
    height ? `height="${escapeAttribute(height)}"` : '',
    alt ? `alt="${escapeAttribute(alt)}"` : '',
    title ? `title="${escapeAttribute(title)}"` : '',
    safeWordImage ? `word_img="${escapeAttribute(safeWordImage)}"` : '',
    safeWordImage ? `class="${wordImagePendingClass}"` : '',
  ]
    .filter(Boolean)
    .join(' ')
    .replace(/^(.+)$/, ' $1');
}

function attributeValue(rawAttributes: string, name: string) {
  const pattern = new RegExp(`${name}\\s*=\\s*(?:"([^"]*)"|'([^']*)'|([^\\s>]+))`, 'i');
  const match = rawAttributes.match(pattern);
  return decodeAttributeValue(match?.[1] ?? match?.[2] ?? match?.[3] ?? '');
}

function cssValue(rawAttributes: string, name: string) {
  const style = attributeValue(rawAttributes, 'style');
  const match = style.match(new RegExp(`${name}\\s*:\\s*([^;]+)`, 'i'));
  return dimensionValue(match?.[1] ?? '');
}

function dimensionAttributeValue(rawAttributes: string, name: string) {
  return dimensionValue(attributeValue(rawAttributes, name));
}

function dimensionValue(value: string) {
  const trimmed = value.trim();
  return /^\d+(?:\.\d+)?(?:px|pt|cm|mm|in|%)?$/i.test(trimmed) ? trimmed : '';
}

function safeStyleAttribute(rawAttributes: string) {
  const declarations = safeStyleDeclarations(rawAttributes);
  return declarations.length ? ` style="${escapeAttribute(declarations.join(';'))}"` : '';
}

function safeStyleDeclarations(rawAttributes: string) {
  const values = new Map<string, string>();
  const align = normalizedTextAlign(attributeValue(rawAttributes, 'align'));
  if (align) values.set('text-align', align);

  for (const declaration of attributeValue(rawAttributes, 'style').split(';')) {
    const separator = declaration.indexOf(':');
    if (separator < 1) continue;
    const property = declaration.slice(0, separator).trim().toLowerCase();
    const value = declaration.slice(separator + 1).trim();
    const safeValue = safeStyleValue(property, value);
    if (safeValue) values.set(property, safeValue);
  }

  return ['text-align', 'color', 'background-color']
    .concat(['font-size', 'font-family', 'line-height'])
    .map((property) => values.has(property) ? `${property}:${values.get(property)}` : '')
    .filter(Boolean);
}

function safeStyleValue(property: string, value: string) {
  if (property === 'text-align') return normalizedTextAlign(value);
  if (property === 'color' || property === 'background-color') return safeCssColor(value);
  if (property === 'font-size') return safeFontSize(value);
  if (property === 'font-family') return safeFontFamily(value);
  if (property === 'line-height') return safeLineHeight(value);
  return '';
}

function normalizedTextAlign(value: string) {
  const normalized = value.trim().toLowerCase();
  return /^(left|right|center|justify)$/.test(normalized) ? normalized : '';
}

function safeCssColor(value: string) {
  const trimmed = value.trim();
  if (/^#[0-9a-f]{3,8}$/i.test(trimmed)) return trimmed;
  if (/^rgba?\(\s*\d{1,3}%?\s*,\s*\d{1,3}%?\s*,\s*\d{1,3}%?(?:\s*,\s*(?:0|1|0?\.\d+|\d{1,3}%))?\s*\)$/i.test(trimmed)) {
    return trimmed;
  }
  return trimmed.toLowerCase() === 'transparent' ? 'transparent' : '';
}

function safeFontSize(value: string) {
  const trimmed = value.trim();
  return /^(?:1[0-9]|2[0-9]|3[0-2])px$/i.test(trimmed) ? trimmed : '';
}

function safeFontFamily(value: string) {
  const normalized = value
    .split(',')
    .map((item) => item.trim().replace(/^['"]|['"]$/g, ''))
    .filter(Boolean)
    .join(', ');
  return /^[\w\s\u4e00-\u9fa5,-]+$/u.test(normalized) ? normalized : '';
}

function safeLineHeight(value: string) {
  const trimmed = value.trim();
  return /^(?:1(?:\.[0-9])?|2(?:\.[0-4])?)$/.test(trimmed) ? trimmed : '';
}

function isSafeHref(value: string) {
  const normalized = value.trim().toLowerCase();
  return normalized.startsWith('http://') || normalized.startsWith('https://') || normalized.startsWith('mailto:') || normalized.startsWith('#');
}

function isSafeImageSrc(value: string) {
  const normalized = value.trim().toLowerCase();
  return normalized.startsWith('http://')
    || normalized.startsWith('https://')
    || normalized.startsWith('/ueditor/getimage')
    || normalized.startsWith('data:image/');
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
