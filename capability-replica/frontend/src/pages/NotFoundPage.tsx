import { Result } from 'antd';

// Generic 404 page for routes not yet copied.
export default function NotFoundPage() {
  return <Result status="404" title="404" subTitle="页面不存在或尚未复刻" />;
}
