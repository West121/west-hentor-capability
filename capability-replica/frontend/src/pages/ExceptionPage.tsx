import { Result } from 'antd';

type ExceptionPageProps = {
  status: '403' | '404' | '500';
  title: string;
  subTitle: string;
};

// Mirrors the original Angular exception route pages.
export default function ExceptionPage({ status, title, subTitle }: ExceptionPageProps) {
  return <Result status={status} title={title} subTitle={subTitle} />;
}
