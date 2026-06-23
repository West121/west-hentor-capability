export type AbilityDescriptionBlockFormat = 'paragraph' | 'heading1' | 'heading2' | 'heading3';
export type AbilityDescriptionAlignment = 'left' | 'center' | 'right' | 'justify';
export type AbilityDescriptionFontSize = '' | '12px' | '14px' | '16px' | '18px' | '20px' | '24px' | '28px' | '32px';
export type AbilityDescriptionFontFamily = '' | 'Arial' | 'Microsoft YaHei' | 'SimSun' | 'SimHei' | 'Times New Roman';
export type AbilityDescriptionLineHeight = '' | '1.2' | '1.5' | '1.8' | '2' | '2.4';

export interface AbilityDescriptionColorOption {
  value: string;
  label: string;
  color?: string;
}

export const abilityDescriptionBlockFormats: Array<{ value: AbilityDescriptionBlockFormat; label: string }> = [
  { value: 'paragraph', label: '正文' },
  { value: 'heading1', label: '标题1' },
  { value: 'heading2', label: '标题2' },
  { value: 'heading3', label: '标题3' },
];

export const abilityDescriptionFontSizes: Array<{ value: AbilityDescriptionFontSize; label: string }> = [
  { value: '', label: '默认字号' },
  { value: '12px', label: '12px' },
  { value: '14px', label: '14px' },
  { value: '16px', label: '16px' },
  { value: '18px', label: '18px' },
  { value: '20px', label: '20px' },
  { value: '24px', label: '24px' },
  { value: '28px', label: '28px' },
  { value: '32px', label: '32px' },
];

export const abilityDescriptionFontFamilies: Array<{ value: AbilityDescriptionFontFamily; label: string }> = [
  { value: '', label: '默认字体' },
  { value: 'Arial', label: 'Arial' },
  { value: 'Microsoft YaHei', label: '微软雅黑' },
  { value: 'SimSun', label: '宋体' },
  { value: 'SimHei', label: '黑体' },
  { value: 'Times New Roman', label: 'Times New Roman' },
];

export const abilityDescriptionLineHeights: Array<{ value: AbilityDescriptionLineHeight; label: string }> = [
  { value: '', label: '默认行高' },
  { value: '1.2', label: '1.2' },
  { value: '1.5', label: '1.5' },
  { value: '1.8', label: '1.8' },
  { value: '2', label: '2.0' },
  { value: '2.4', label: '2.4' },
];

export const abilityDescriptionAlignments: Array<{ value: AbilityDescriptionAlignment; label: string }> = [
  { value: 'left', label: '左对齐' },
  { value: 'center', label: '居中' },
  { value: 'right', label: '右对齐' },
  { value: 'justify', label: '两端对齐' },
];

export const abilityDescriptionTextColors: AbilityDescriptionColorOption[] = [
  { value: '', label: '默认' },
  { value: '#c00000', label: '深红', color: '#c00000' },
  { value: '#ff0000', label: '红色', color: '#ff0000' },
  { value: '#ffc000', label: '金色', color: '#ffc000' },
  { value: '#00b050', label: '绿色', color: '#00b050' },
  { value: '#0070c0', label: '蓝色', color: '#0070c0' },
  { value: '#7030a0', label: '紫色', color: '#7030a0' },
  { value: '#000000', label: '黑色', color: '#000000' },
];

export const abilityDescriptionBackgroundColors: AbilityDescriptionColorOption[] = [
  { value: '', label: '默认' },
  { value: 'transparent', label: '透明' },
  { value: '#fff2cc', label: '浅黄', color: '#fff2cc' },
  { value: '#d9ead3', label: '浅绿', color: '#d9ead3' },
  { value: '#ddebf7', label: '浅蓝', color: '#ddebf7' },
  { value: '#eadcf8', label: '浅紫', color: '#eadcf8' },
];
