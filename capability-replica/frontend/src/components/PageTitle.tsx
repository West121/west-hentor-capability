import { Typography } from 'antd';

// Compact page title matching the original page-header placement.
export default function PageTitle({ title, description }: { title: string; description?: string }) {
  return (
    <div style={{ marginBottom: 16 }}>
      <Typography.Title level={3} style={{ margin: 0 }}>
        {title}
      </Typography.Title>
      {description ? <Typography.Text className="muted">{description}</Typography.Text> : null}
    </div>
  );
}
