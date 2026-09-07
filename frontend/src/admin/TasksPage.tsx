import { useCallback, useEffect, useState } from 'react';
import { Button, Card, Popconfirm, Progress, Select, Table, Tag, message } from 'antd';
import { RedoOutlined, DeleteOutlined } from '@ant-design/icons';
import type { TableProps } from 'antd';
import { kbApi } from '@/api/client';
import type { IngestTaskItem } from '@/types';
import { formatDateTime } from './format';

/** 任务状态 → 标签 */
const TASK_STATUS: Record<IngestTaskItem['status'], { color: string; label: string }> = {
  QUEUED: { color: 'default', label: '排队中' },
  RUNNING: { color: 'blue', label: '执行中' },
  SUCCESS: { color: 'green', label: '成功' },
  FAILED: { color: 'red', label: '失败' },
};

/** 状态过滤选项 */
const STATUS_FILTERS = [
  { value: '', label: '全部状态' },
  { value: 'QUEUED', label: '排队中' },
  { value: 'RUNNING', label: '执行中' },
  { value: 'SUCCESS', label: '成功' },
  { value: 'FAILED', label: '失败' },
];

/**
 * 入库任务监控页:分页列表 + 状态过滤 + 失败重试 + 删除终态任务
 */
export default function TasksPage() {
  const [tasks, setTasks] = useState<IngestTaskItem[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [status, setStatus] = useState('');
  const [loading, setLoading] = useState(false);
  const [messageApi, contextHolder] = message.useMessage();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await kbApi.listTasks(page, pageSize, status || undefined);
      setTasks(res.list);
      setTotal(res.total);
    } catch (e) {
      messageApi.error(`加载任务失败：${e instanceof Error ? e.message : String(e)}`);
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, status, messageApi]);

  useEffect(() => {
    void load();
  }, [load]);

  const handleRetry = async (taskId: number) => {
    try {
      await kbApi.retryTask(taskId);
      messageApi.success('已重新触发入库');
      void load();
    } catch (e) {
      messageApi.error(`重试失败：${e instanceof Error ? e.message : String(e)}`);
    }
  };

  const handleDelete = async (taskId: number) => {
    try {
      await kbApi.removeTask(taskId);
      messageApi.success('任务已删除');
      void load();
    } catch (e) {
      messageApi.error(`删除失败：${e instanceof Error ? e.message : String(e)}`);
    }
  };

  const columns: TableProps<IngestTaskItem>['columns'] = [
    { title: '任务 ID', dataIndex: 'id', key: 'id', width: 90 },
    { title: '库 ID', dataIndex: 'kbId', key: 'kbId', width: 80 },
    { title: '文档 ID', dataIndex: 'docId', key: 'docId', width: 80 },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (s: IngestTaskItem['status']) => {
        const cfg = TASK_STATUS[s];
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '进度',
      dataIndex: 'progress',
      key: 'progress',
      width: 160,
      render: (v: number, record) => (
        <Progress
          percent={v}
          size="small"
          status={record.status === 'FAILED' ? 'exception' : record.status === 'SUCCESS' ? 'success' : 'active'}
        />
      ),
    },
    {
      title: '当前阶段',
      dataIndex: 'currentStage',
      key: 'currentStage',
      width: 120,
      render: (v?: string) => v || '-',
    },
    { title: '错误信息', dataIndex: 'errorMsg', key: 'errorMsg', ellipsis: true, render: (v?: string) => v || '-' },
    { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 170, render: formatDateTime },
    {
      title: '操作',
      key: 'actions',
      width: 150,
      render: (_, record) => (
        <>
          <Button
            type="text"
            size="small"
            icon={<RedoOutlined />}
            disabled={record.status !== 'FAILED'}
            title={record.status === 'FAILED' ? '重新触发入库' : '仅失败任务可重试'}
            onClick={() => handleRetry(record.id)}
          >
            重试
          </Button>
          <Popconfirm
            title="删除该任务记录？"
            okText="删除"
            okButtonProps={{ danger: true }}
            cancelText="取消"
            onConfirm={() => handleDelete(record.id)}
          >
            <Button
              type="text"
              danger
              size="small"
              icon={<DeleteOutlined />}
              disabled={record.status === 'QUEUED' || record.status === 'RUNNING'}
              title={record.status === 'QUEUED' || record.status === 'RUNNING' ? '执行中任务不可删除' : '删除任务记录'}
            >
              删除
            </Button>
          </Popconfirm>
        </>
      ),
    },
  ];

  return (
    <Card
      title="入库任务"
      extra={
        <Select
          className="tasks-status-select"
          value={status}
          onChange={(v) => {
            setStatus(v);
            setPage(1);
          }}
          options={STATUS_FILTERS}
        />
      }
    >
      {contextHolder}
      <Table
        rowKey="id"
        dataSource={tasks}
        loading={loading}
        columns={columns}
        pagination={{
          current: page,
          pageSize,
          total,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
        }}
        onChange={(pagination) => {
          setPage(pagination.current ?? 1);
          setPageSize(pagination.pageSize ?? 10);
        }}
      />
    </Card>
  );
}
