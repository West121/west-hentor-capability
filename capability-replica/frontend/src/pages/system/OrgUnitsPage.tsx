import { App as AntdApp, Button, Form, Input, Modal, Select, Table } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { api } from '../../services/api';
import type { NameValue, OrganizationUnit, UserItem } from '../../types/domain';

const orgManagementOrder = ['NF', 'General & XRD', 'SIR', 'CHEM', 'EMS', 'Lab Group', 'OGC'];

// Production business-line management is a flat business list plus member table.
export default function OrgUnitsPage() {
  const { message } = AntdApp.useApp();
  const [orgs, setOrgs] = useState<OrganizationUnit[]>([]);
  const [active, setActive] = useState<number>();
  const [users, setUsers] = useState<UserItem[]>([]);
  const [userTotal, setUserTotal] = useState(0);
  const [userPage, setUserPage] = useState({ current: 1, pageSize: 10 });
  const [availableUsers, setAvailableUsers] = useState<NameValue[]>([]);
  const [orgOpen, setOrgOpen] = useState(false);
  const [memberOpen, setMemberOpen] = useState(false);
  const [orgForm] = Form.useForm<OrganizationUnit>();
  const [memberForm] = Form.useForm<{ userIds: number[] }>();

  const businessOrgs = useMemo(() => sortBusinessOrgs(orgs), [orgs]);
  const activeOrg = businessOrgs.find((org) => org.id === active);

  async function load() {
    const data = await api.orgUnits();
    const sorted = sortBusinessOrgs(data.items);
    setOrgs(data.items);
    const nextActive = active && sorted.some((item) => item.id === active) ? active : sorted[0]?.id;
    setActive(nextActive);
    if (nextActive) {
      await loadMembers(nextActive, 1, userPage.pageSize);
    }
  }

  async function loadMembers(id: number, current = userPage.current, pageSize = userPage.pageSize) {
    const data = await api.orgUnitUsers(id, {
      sorting: 'id',
      maxResultCount: pageSize,
      skipCount: (current - 1) * pageSize,
    });
    setUsers(data.items);
    setUserTotal(data.totalCount);
    setUserPage({ current, pageSize });
  }

  useEffect(() => {
    void load();
  }, []);

  async function selectOrg(org: OrganizationUnit) {
    if (!org.id) return;
    setActive(org.id);
    await loadMembers(org.id, 1, userPage.pageSize);
  }

  function openOrgModal() {
    orgForm.resetFields();
    orgForm.setFieldsValue({ displayName: '' });
    setOrgOpen(true);
  }

  async function saveOrg() {
    const values = await orgForm.validateFields();
    await api.createOrgUnit({ ...values, parentId: null });
    message.success('保存成功');
    setOrgOpen(false);
    await load();
  }

  async function openMemberModal() {
    if (!active) return;
    const data = await api.findOrgUnitUsers(active, '', 100);
    setAvailableUsers(data.items);
    memberForm.setFieldsValue({ userIds: [] });
    setMemberOpen(true);
  }

  async function addMembers() {
    if (!active) return;
    const values = await memberForm.validateFields();
    await api.addUsersToOrgUnit(active, values.userIds);
    message.success('成员已添加');
    setMemberOpen(false);
    await loadMembers(active, 1, userPage.pageSize);
    await load();
  }

  async function removeMember(row: UserItem) {
    if (!active || !row.id) return;
    await api.removeUserFromOrgUnit(active, row.id);
    message.warning('成员已移除');
    await loadMembers(active, userPage.current, userPage.pageSize);
    await load();
  }

  return (
    <div className="org-management-page">
      <section className="org-management-panel org-management-list-panel">
        <div className="org-management-panel-header">
          <strong>业务线列表</strong>
          <Button type="primary" onClick={openOrgModal}>
            添加业务线
          </Button>
        </div>
        <div className="org-management-list">
          {businessOrgs.map((org) => (
            <button
              key={org.id}
              type="button"
              className={`org-management-list-item${org.id === active ? ' org-management-list-item-active' : ''}`}
              onClick={() => void selectOrg(org)}
            >
              {org.displayName}
            </button>
          ))}
        </div>
      </section>
      <section className="org-management-panel org-management-members-panel">
        <div className="org-management-panel-header">
          <strong>{activeOrg?.displayName ?? ''}</strong>
          <Button type="primary" disabled={!active} onClick={openMemberModal}>
            添加成员
          </Button>
        </div>
        <Table
          className="org-management-table"
          rowKey="id"
          dataSource={users}
          pagination={{
            current: userPage.current,
            pageSize: userPage.pageSize,
            total: userTotal,
            showSizeChanger: true,
            showTotal: (count) => `共 ${count} 条`,
            onChange: (current, pageSize) => active && loadMembers(active, current, pageSize),
          }}
          columns={[
            { title: '用户名', dataIndex: 'userName' },
            { title: '添加时间', dataIndex: 'addedTime', width: 220, render: (value: string) => formatDateTime(value) },
            {
              title: '',
              width: 120,
              render: (_, row) => (
                <Button type="link" danger disabled={row.id === 1} onClick={() => void removeMember(row)}>
                  移除
                </Button>
              ),
            },
          ]}
        />
      </section>
      <Modal forceRender title="添加业务线" open={orgOpen} onOk={saveOrg} onCancel={() => setOrgOpen(false)}>
        <Form form={orgForm} layout="vertical">
          <Form.Item name="displayName" label="名称" rules={[{ required: true, max: 128 }]}>
            <Input maxLength={128} />
          </Form.Item>
        </Form>
      </Modal>
      <Modal forceRender title="添加成员" open={memberOpen} onOk={addMembers} onCancel={() => setMemberOpen(false)}>
        <Form form={memberForm} layout="vertical">
          <Form.Item name="userIds" label="用户" rules={[{ required: true }]}>
            <Select
              mode="multiple"
              options={availableUsers.map((user) => ({ label: user.name, value: Number(user.value) }))}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}

function sortBusinessOrgs(orgs: OrganizationUnit[]) {
  const production = orgs.filter((org) => orgManagementOrder.some((name) => equalsText(name, org.displayName)));
  return production.sort((left, right) => orgIndex(left.displayName) - orgIndex(right.displayName));
}

function orgIndex(name: string) {
  const index = orgManagementOrder.findIndex((item) => equalsText(item, name));
  return index < 0 ? orgManagementOrder.length : index;
}

function equalsText(left?: string, right?: string) {
  return String(left ?? '').trim().toLowerCase() === String(right ?? '').trim().toLowerCase();
}

function formatDateTime(value?: string) {
  if (!value) return '';
  return value.replace('T', ' ').slice(0, 16);
}
