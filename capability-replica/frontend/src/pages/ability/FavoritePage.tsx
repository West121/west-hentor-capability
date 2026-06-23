import { Button, Card, Col, Form, Input, Modal, Row, Space, Table, Tag, App as AntdApp } from 'antd';
import { useEffect, useState } from 'react';
import PageTitle from '../../components/PageTitle';
import { api } from '../../services/api';
import type { Ability, FavoriteGroup } from '../../types/domain';
import {
  canMutateFavoriteGroup,
  favoriteGroupKey,
  favoriteGroupRequestId,
  favoriteGroupSubtitle,
  selectFavoriteGroup,
} from './favoriteGroupState';

// Favorite list mirrors the original left-list and right-table layout.
export default function FavoritePage() {
  const { message } = AntdApp.useApp();
  const [groups, setGroups] = useState<FavoriteGroup[]>([]);
  const [activeKey, setActiveKey] = useState<string>();
  const [abilities, setAbilities] = useState<Ability[]>([]);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<FavoriteGroup | undefined>();
  const [form] = Form.useForm<FavoriteGroup>();

  async function loadGroups() {
    const data = await api.favorites();
    setGroups(data.items);
    const selected = selectFavoriteGroup(data.items, activeKey);
    setActiveKey(favoriteGroupKey(selected));
    if (selected) {
      await loadAbilities(favoriteGroupRequestId(selected));
    } else {
      setAbilities([]);
    }
  }

  async function loadAbilities(id?: string) {
    const data = await api.favoriteAbilities(id);
    setAbilities(data.items);
  }

  useEffect(() => {
    void loadGroups();
  }, []);

  async function changeGroup(group: FavoriteGroup) {
    setActiveKey(favoriteGroupKey(group));
    await loadAbilities(favoriteGroupRequestId(group));
  }

  function edit(group?: FavoriteGroup) {
    if (group && !canMutateFavoriteGroup(group)) return;
    setEditing(group);
    form.setFieldsValue(group ?? { name: '' });
    setOpen(true);
  }

  async function save() {
    await api.saveFavorite({ ...editing, ...(await form.validateFields()) });
    message.success('保存成功');
    setOpen(false);
    await loadGroups();
  }

  async function removeGroup(group: FavoriteGroup) {
    if (!group.id) return;
    await api.deleteFavorite(group.id);
    message.warning('删除成功');
    setActiveKey(undefined);
    await loadGroups();
  }

  async function removeAbility(row: Ability) {
    if (!row.id) return;
    await api.removeFavoriteItem(row.id);
    message.warning('已移出收藏');
    await loadAbilities(favoriteGroupRequestId(selectFavoriteGroup(groups, activeKey)));
  }

  return (
    <div className="page-body">
      <PageTitle title="我的收藏" description="按收藏清单管理常用能力项目" />
      <Row gutter={[16, 16]}>
        <Col xs={24} md={8}>
          <Card title="我的收藏清单" extra={<Button onClick={() => edit()}>新增清单</Button>}>
            <div className="favorite-group-list">
              {groups.map((group) => (
                <FavoriteGroupRow
                  key={favoriteGroupKey(group)}
                  group={group}
                  active={activeKey === favoriteGroupKey(group)}
                  onSelect={changeGroup}
                  onEdit={edit}
                  onRemove={removeGroup}
                />
              ))}
            </div>
          </Card>
        </Col>
        <Col xs={24} md={16}>
          <Card title="收藏能力">
            <Table
              rowKey="id"
              dataSource={abilities}
              scroll={{ x: 1200 }}
              columns={[
                { title: '业务线', dataIndex: 'orgName', width: 120 },
                { title: '类型', dataIndex: 'typeName', width: 120 },
                { title: '样品名称', dataIndex: 'samplingName', width: 140 },
                { title: '测试项目', dataIndex: 'testItem', width: 140 },
                { title: '标准号', dataIndex: 'standardNo', width: 160 },
                { title: '方法中文描述', dataIndex: 'methodName', width: 220 },
                {
                  title: '实验室能力',
                  dataIndex: 'labAbilities',
                  width: 180,
                  render: (labs: Ability['labAbilities']) =>
                    labs?.map((lab) => <Tag key={lab.code}>{lab.code}</Tag>),
                },
                {
                  title: '操作',
                  fixed: 'right',
                  width: 100,
                  render: (_, row) => (
                    <Space>
                      <Button type="link" danger onClick={() => removeAbility(row)}>
                        移出
                      </Button>
                    </Space>
                  ),
                },
              ]}
            />
          </Card>
        </Col>
      </Row>
      <Modal title={editing ? '编辑收藏清单' : '新增收藏清单'} open={open} onOk={save} onCancel={() => setOpen(false)}>
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="名称" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}

function FavoriteGroupRow({
  group,
  active,
  onSelect,
  onEdit,
  onRemove,
}: {
  group: FavoriteGroup;
  active: boolean;
  onSelect: (group: FavoriteGroup) => void;
  onEdit: (group: FavoriteGroup) => void;
  onRemove: (group: FavoriteGroup) => void;
}) {
  const subtitle = favoriteGroupSubtitle(group);
  return (
    <div
      className={active ? 'favorite-group-row active-list-item' : 'favorite-group-row'}
      onClick={() => onSelect(group)}
    >
      <div>
        <div className="favorite-group-name">{group.name}</div>
        {subtitle ? <div className="muted">{subtitle}</div> : null}
      </div>
      {canMutateFavoriteGroup(group) ? (
        <Space size={0}>
          <Button type="link" onClick={(event) => {
            event.stopPropagation();
            onEdit(group);
          }}>
            编辑
          </Button>
          <Button type="link" danger onClick={(event) => {
            event.stopPropagation();
            onRemove(group);
          }}>
            删除
          </Button>
        </Space>
      ) : null}
    </div>
  );
}
