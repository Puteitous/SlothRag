import { useCallback, useEffect, useState } from 'react';
import { Card, Col, Row, Select, Statistic, Table, Tag, Tooltip, message } from 'antd';
import {
  LikeOutlined,
  DislikeOutlined,
  CommentOutlined,
  ExclamationCircleOutlined,
} from '@ant-design/icons';
import type { TableProps } from 'antd';
import { feedbackApi } from '@/api/client';
import type { FeedbackRecord, FeedbackStats } from '@/types';
import { formatDateTime } from './format';

/** 反馈类型 → 标签 */
const FEEDBACK_MAP: Record<string, { color: string; label: string; icon: React.ReactNode }> = {
  thumbs_up: { color: 'green', label: '👍 好评', icon: <LikeOutlined /> },
  thumbs_down: { color: 'red', label: '👎 差评', icon: <DislikeOutlined /> },
};

/** 反馈类型过滤选项 */
const FEEDBACK_FILTERS = [
  { value: '', label: '全部反馈' },
  { value: 'thumbs_up', label: '👍 好评' },
  { value: 'thumbs_down', label: '👎 差评' },
];

/**
 * 反馈看板页面：统计卡片 + 分页列表 + 类型筛选 + 差评 Top 提问
 */
export default function FeedbackPage() {
  const [records, setRecords] = useState<FeedbackRecord[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [feedbackType, setFeedbackType] = useState('');
  const [loading, setLoading] = useState(false);
  const [stats, setStats] = useState<FeedbackStats | null>(null);
  const [statsLoading, setStatsLoading] = useState(false);
  const [messageApi, contextHolder] = message.useMessage();

  // 加载统计
  const loadStats = useCallback(async () => {
    setStatsLoading(true);
    try {
      setStats(await feedbackApi.stats());
    } catch (e) {
      messageApi.error(`加载统计失败：${e instanceof Error ? e.message : String(e)}`);
    } finally {
      setStatsLoading(false);
    }
  }, [messageApi]);

  // 加载列表
  const loadList = useCallback(async () => {
    setLoading(true);
    try {
      const res = await feedbackApi.list(page, pageSize, feedbackType || undefined);
      setRecords(res.list);
      setTotal(res.total);
    } catch (e) {
      messageApi.error(`加载反馈列表失败：${e instanceof Error ? e.message : String(e)}`);
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, feedbackType, messageApi]);

  useEffect(() => {
    void loadStats();
  }, [loadStats]);

  useEffect(() => {
    void loadList();
  }, [loadList]);

  const columns: TableProps<FeedbackRecord>['columns'] = [
    {
      title: '反馈',
      dataIndex: 'feedback',
      key: 'feedback',
      width: 100,
      render: (f: string) => {
        const cfg = FEEDBACK_MAP[f];
        return cfg ? <Tag color={cfg.color}>{cfg.label}</Tag> : <Tag>{f}</Tag>;
      },
    },
    {
      title: '用户提问',
      dataIndex: 'question',
      key: 'question',
      ellipsis: true,
      render: (v?: string) =>
        v ? (
          <Tooltip title={v}>
            <span>{v.length > 60 ? `${v.slice(0, 60)}…` : v}</span>
          </Tooltip>
        ) : (
          <span className="text-muted">-</span>
        ),
    },
    {
      title: '回答预览',
      dataIndex: 'answer',
      key: 'answer',
      ellipsis: true,
      render: (v?: string) =>
        v ? (
          <Tooltip title={v}>
            <span>{v.length > 80 ? `${v.slice(0, 80)}…` : v}</span>
          </Tooltip>
        ) : (
          <span className="text-muted">-</span>
        ),
    },
    {
      title: '评论文本',
      dataIndex: 'comment',
      key: 'comment',
      width: 120,
      ellipsis: true,
      render: (v?: string) =>
        v ? (
          <Tooltip title={v}>
            <span>{v.length > 20 ? `${v.slice(0, 20)}…` : v}</span>
          </Tooltip>
        ) : (
          '-'
        ),
    },
    { title: '反馈时间', dataIndex: 'createdAt', key: 'createdAt', width: 170, render: formatDateTime },
  ];

  return (
    <div className="feedback-page">
      {contextHolder}

      {/* ============ 统计卡片 ============ */}
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={6}>
          <Card loading={statsLoading}>
            <Statistic
              title="总反馈数"
              value={stats?.total ?? 0}
              prefix={<CommentOutlined />}
              suffix="条"
              valueStyle={{ color: '#1a1a2e' }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card loading={statsLoading}>
            <Statistic
              title="好评率"
              value={stats?.likeRate ?? 0}
              precision={2}
              suffix="%"
              prefix={<LikeOutlined />}
              valueStyle={{ color: stats && stats.likeRate >= 80 ? '#52c41a' : stats && stats.likeRate >= 50 ? '#faad14' : '#ff4d4f' }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card loading={statsLoading}>
            <Statistic
              title="👍 好评"
              value={stats?.thumbsUp ?? 0}
              prefix={<LikeOutlined />}
              suffix="条"
              valueStyle={{ color: '#52c41a' }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card loading={statsLoading}>
            <Statistic
              title="👎 差评"
              value={stats?.thumbsDown ?? 0}
              prefix={<DislikeOutlined />}
              suffix="条"
              valueStyle={{ color: '#ff4d4f' }}
            />
          </Card>
        </Col>
      </Row>

      {/* ============ 差评 Top 提问 ============ */}
      {stats && stats.topDownQuestions && stats.topDownQuestions.length > 0 && (
        <Card
          title={
            <span>
              <ExclamationCircleOutlined style={{ color: '#ff4d4f', marginRight: 8 }} />
              差评最多的提问（Top 10）
            </span>
          }
          size="small"
          style={{ marginBottom: 16 }}
        >
          <Row gutter={[16, 8]}>
            {stats.topDownQuestions.map((item, idx) => (
              <Col span={12} key={idx}>
                <div className="feedback-down-rank-item">
                  <span className="feedback-down-rank-index">{idx + 1}</span>
                  <Tooltip title={item.question}>
                    <span className="feedback-down-rank-question">
                      {item.question.length > 50 ? `${item.question.slice(0, 50)}…` : item.question}
                    </span>
                  </Tooltip>
                  <Tag color="red" style={{ marginLeft: 8, flexShrink: 0 }}>
                    {item.count} 次
                  </Tag>
                </div>
              </Col>
            ))}
          </Row>
        </Card>
      )}

      {/* ============ 反馈列表 ============ */}
      <Card
        title="反馈明细"
        extra={
          <Select
            className="feedback-type-select"
            value={feedbackType}
            onChange={(v) => {
              setFeedbackType(v);
              setPage(1);
            }}
            options={FEEDBACK_FILTERS}
            style={{ width: 150 }}
          />
        }
      >
        <Table
          rowKey="id"
          dataSource={records}
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
    </div>
  );
}
