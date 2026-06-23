import type { Ability } from '../../types/domain';

export type AbilityQuerySearchField = {
  name: 'samplingName' | 'testItem' | 'standardNo' | 'methodName' | 'methodEngName' | 'labAbility' | 'ability';
  placeholder: string;
};

export type AbilityQueryTableField = keyof Ability | 'actions';

// Matches the decompiled Angular query form fields.
export const abilityQuerySearchFields: AbilityQuerySearchField[] = [
  { name: 'samplingName', placeholder: '请输入样品名称' },
  { name: 'testItem', placeholder: '请输入测试项目' },
  { name: 'standardNo', placeholder: '请输入标准号' },
  { name: 'methodName', placeholder: '请输入方法中文描述' },
  { name: 'methodEngName', placeholder: '请输入方法英文描述' },
  { name: 'labAbility', placeholder: '实验室' },
  { name: 'ability', placeholder: '资质' },
];

// Business columns copied from the original ability query table.
export const originalAbilityQueryColumnFields = [
  'samplingName',
  'testItem',
  'price',
  'standardNo',
  'methodName',
  'methodEngName',
  'detectionLimit',
  'cycleWorkingDay',
  'massRequired',
  'sizeRequired',
  'remark',
  'labAbilities',
];

export const abilityQueryTableFields = ['orgName', ...originalAbilityQueryColumnFields, 'actions'];

export const abilityQueryColumnTitles: Record<string, string> = {
  orgName: '业务线',
  samplingName: '样品名称',
  testItem: '测试项目',
  price: '价格',
  standardNo: '标准号',
  methodName: '方法中文描述',
  methodEngName: '方法英文描述',
  detectionLimit: '适用范围',
  cycleWorkingDay: '检测周期/工作日',
  massRequired: '所需样品量(g)',
  sizeRequired: '样品粒度要求/mm',
  remark: '备注',
  labAbilities: '实验室能力',
  actions: '操作',
};

export const abilityQueryColumnWidths: Record<string, number> = {
  orgName: 110,
  samplingName: 140,
  testItem: 140,
  price: 100,
  standardNo: 240,
  methodName: 220,
  methodEngName: 180,
  detectionLimit: 180,
  cycleWorkingDay: 120,
  massRequired: 120,
  sizeRequired: 130,
  remark: 180,
  labAbilities: 220,
  actions: 100,
};
