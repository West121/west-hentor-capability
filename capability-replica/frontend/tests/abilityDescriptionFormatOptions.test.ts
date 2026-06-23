import assert from 'node:assert/strict';
import {
  abilityDescriptionAlignments,
  abilityDescriptionBackgroundColors,
  abilityDescriptionBlockFormats,
  abilityDescriptionFontFamilies,
  abilityDescriptionFontSizes,
  abilityDescriptionLineHeights,
  abilityDescriptionTextColors,
} from '../src/pages/ability/abilityDescriptionFormatOptions.ts';

assert.deepEqual(abilityDescriptionBlockFormats, [
  { value: 'paragraph', label: '正文' },
  { value: 'heading1', label: '标题1' },
  { value: 'heading2', label: '标题2' },
  { value: 'heading3', label: '标题3' },
]);

assert.deepEqual(abilityDescriptionAlignments, [
  { value: 'left', label: '左对齐' },
  { value: 'center', label: '居中' },
  { value: 'right', label: '右对齐' },
  { value: 'justify', label: '两端对齐' },
]);

assert.deepEqual(abilityDescriptionFontSizes.map((item) => item.value), ['', '12px', '14px', '16px', '18px', '20px', '24px', '28px', '32px']);

assert.deepEqual(abilityDescriptionFontFamilies.map((item) => item.value), ['', 'Arial', 'Microsoft YaHei', 'SimSun', 'SimHei', 'Times New Roman']);

assert.deepEqual(abilityDescriptionLineHeights.map((item) => item.value), ['', '1.2', '1.5', '1.8', '2', '2.4']);

assert.deepEqual(abilityDescriptionTextColors.map((item) => item.value), [
  '',
  '#c00000',
  '#ff0000',
  '#ffc000',
  '#00b050',
  '#0070c0',
  '#7030a0',
  '#000000',
]);

assert.deepEqual(abilityDescriptionBackgroundColors.map((item) => item.value), [
  '',
  'transparent',
  '#fff2cc',
  '#d9ead3',
  '#ddebf7',
  '#eadcf8',
]);
