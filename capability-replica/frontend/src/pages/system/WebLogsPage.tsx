import { DownloadOutlined, ReloadOutlined } from '@ant-design/icons';
import { App as AntdApp, Button, Card, Col, Row, Space, Tag, Typography } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import PageTitle from '../../components/PageTitle';
import { api } from '../../services/api';

const LEVELS = ['ERROR', 'FATAL', 'WARN', 'INFO', 'DEBUG'];

// Web log maintenance mirrors WebLogAppService.
export default function WebLogsPage() {
  const { message } = AntdApp.useApp();
  const [lines, setLines] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);

  async function load() {
    setLoading(true);
    try {
      const data = await api.latestWebLogs();
      setLines(data.latestWebLogLines ?? []);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  async function download() {
    await api.downloadFile(await api.downloadWebLogs());
    message.success('Web日志已打包下载');
  }

  const counts = useMemo(
    () =>
      LEVELS.map((level) => ({
        level,
        count: lines.filter((line) => line.startsWith(level)).length,
      })),
    [lines],
  );

  return (
    <>
      <PageTitle title="Web日志" description="查看最近运行日志并下载日志包" />
      <Space style={{ marginBottom: 16 }}>
        <Button icon={<ReloadOutlined />} onClick={() => void load()} loading={loading}>
          刷新
        </Button>
        <Button type="primary" icon={<DownloadOutlined />} onClick={() => void download()}>
          下载日志
        </Button>
      </Space>
      <Row gutter={[16, 16]}>
        {counts.map((item) => (
          <Col key={item.level} xs={12} md={4}>
            <Card>
              <Typography.Text type="secondary">{item.level}</Typography.Text>
              <Typography.Title level={3} style={{ margin: 0 }}>
                {item.count}
              </Typography.Title>
            </Card>
          </Col>
        ))}
      </Row>
      <Card style={{ marginTop: 16 }}>
        <div
          style={{
            background: '#111827',
            borderRadius: 6,
            color: '#d1d5db',
            fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace',
            fontSize: 13,
            lineHeight: 1.7,
            maxHeight: 560,
            overflow: 'auto',
            padding: 16,
            whiteSpace: 'pre-wrap',
          }}
        >
          {lines.length ? lines.map((line, index) => <LogLine key={`${index}-${line}`} line={line} />) : <span>暂无日志</span>}
        </div>
      </Card>
    </>
  );
}

function LogLine({ line }: { line: string }) {
  const level = LEVELS.find((item) => line.startsWith(item));
  return (
    <div>
      {level ? <Tag color={levelColor(level)}>{level}</Tag> : null}
      <span>{level ? line.slice(level.length).trimStart() : line}</span>
    </div>
  );
}

function levelColor(level: string) {
  if (level === 'ERROR' || level === 'FATAL') return 'red';
  if (level === 'WARN') return 'orange';
  if (level === 'DEBUG') return 'purple';
  return 'blue';
}
