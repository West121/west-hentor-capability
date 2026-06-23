import { Button, Card, Col, Row, Switch, Table, Tree, App as AntdApp } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import PageTitle from '../../components/PageTitle';
import { api } from '../../services/api';
import type { AbilityProperty, OrganizationUnit } from '../../types/domain';
import { safeAbilityDescriptionHtml } from '../ability/abilityDescriptionHtml';

interface OrgTreeNode {
  key: number;
  title: string;
  children?: OrgTreeNode[];
}

// Org setting page copies the tree + property switch layout.
export default function OrgAbilitySettingPage() {
  const { message } = AntdApp.useApp();
  const [orgs, setOrgs] = useState<OrganizationUnit[]>([]);
  const [currentOrg, setCurrentOrg] = useState<number>();
  const [properties, setProperties] = useState<AbilityProperty[]>([]);
  const [isPublic, setIsPublic] = useState(false);
  const [description, setDescription] = useState('');

  async function loadOrgs() {
    const data = await api.orgUnits();
    setOrgs(data.items);
    const firstOrg = data.items.find((item) => item.id !== undefined)?.id;
    setCurrentOrg((value) => (value && data.items.some((item) => item.id === value) ? value : firstOrg));
  }

  async function loadProperties(orgId: number) {
    const data = await api.orgAbilityProperties(orgId);
    setProperties(data.propertyList);
    setIsPublic(data.isPublic);
    setDescription(data.description);
  }

  useEffect(() => {
    void loadOrgs();
  }, []);

  useEffect(() => {
    if (currentOrg !== undefined) {
      void loadProperties(currentOrg);
    }
  }, [currentOrg]);

  async function save() {
    if (currentOrg === undefined) return;
    await api.saveOrgSetting({
      orgId: currentOrg,
      propertyName: properties.filter((item) => item.enabled).map((item) => item.name),
      lab: [],
      isPublic,
      description,
    });
    message.success('保存成功');
  }

  const treeData = useMemo(() => buildOrgTree(orgs), [orgs]);

  return (
    <div className="page-body">
      <PageTitle title="能力表设置" description="按业务线控制能力表字段和公开状态" />
      <Row gutter={16}>
        <Col xs={24} md={7}>
          <Card title="业务部门">
            <Tree
              defaultExpandAll
              selectedKeys={currentOrg === undefined ? [] : [currentOrg]}
              treeData={treeData}
              fieldNames={{ key: 'key', title: 'title' }}
              onSelect={(keys) => keys[0] && setCurrentOrg(Number(keys[0]))}
            />
          </Card>
        </Col>
        <Col xs={24} md={17}>
          <Card
            title="字段设置"
            extra={
              <Button type="primary" onClick={save}>
                保存
              </Button>
            }
          >
            <div style={{ marginBottom: 16 }}>
              <Switch checked={isPublic} checkedChildren="公开" unCheckedChildren="非公开" onChange={setIsPublic} />
              <div
                className="ability-description-content org-ability-setting-description"
                dangerouslySetInnerHTML={{ __html: safeAbilityDescriptionHtml(description || '当前业务线能力字段') }}
              />
            </div>
            <Table
              rowKey="name"
              pagination={false}
              dataSource={properties}
              columns={[
                { title: '属性名称', dataIndex: 'title' },
                {
                  title: '启用',
                  dataIndex: 'enabled',
                  render: (_, row) => (
                    <Switch
                      checked={row.enabled}
                      onChange={(checked) =>
                        setProperties((prev) =>
                          prev.map((item) => (item.name === row.name ? { ...item, enabled: checked } : item)),
                        )
                      }
                    />
                  ),
                },
              ]}
            />
          </Card>
        </Col>
      </Row>
    </div>
  );
}

function buildOrgTree(orgs: OrganizationUnit[]): OrgTreeNode[] {
  const nodes = new Map<number, OrgTreeNode>();
  orgs.forEach((org) => {
    if (org.id !== undefined) {
      nodes.set(org.id, { key: org.id, title: org.displayName, children: [] });
    }
  });

  const roots: OrgTreeNode[] = [];
  orgs.forEach((org) => {
    if (org.id === undefined) return;
    const node = nodes.get(org.id);
    if (!node) return;
    const parent = org.parentId === undefined || org.parentId === null ? undefined : nodes.get(org.parentId);
    if (parent) {
      parent.children = [...(parent.children ?? []), node];
    } else {
      roots.push(node);
    }
  });

  nodes.forEach((node) => {
    if (node.children?.length === 0) {
      delete node.children;
    }
  });
  return roots;
}
