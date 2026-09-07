import { useCallback, useEffect, useState } from 'react';
import { Button, Card, Popconfirm, Select, Table, Tag, Upload, message } from 'antd';
import { UploadOutlined, DeleteOutlined } from '@ant-design/icons';
import type { TableProps } from 'antd';
import { kbApi } from '@/api/client';
import type { DocItem, KbItem } from '@/types';
import { formatDateTime, formatSize } from './format';

/** 文档状态 → 标签 */
const DOC_STATUS: Record<DocItem['status'], { color: string; label: string }> = {
  PENDING: { color: 'default', label: '待处理' },
  PARSING: { color: 'blue', label: '解析中' },
  CHUNKING: { color: 'cyan', label: '分块中' },
  INDEXED: { color: 'green', label: '已入库' },
  FAILED: { color: 'red', label: '失败' },
};

/** 支持上传的文档格式 */
const ACCEPT = '.txt,.md,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.pdf,.html';

/**
 * 文档管理页:库内文档分页列表 + 上传(异步入库) + 删除
 */
export default function DocsPage() {
  const [kbs, setKbs] = useState<KbItem[]>([]);
  const [kbId, setKbId] = useState<number | null>(null);
  const [docs, setDocs] = useState<DocItem[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [loading, setLoading] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [messageApi, contextHolder] = message.useMessage();

  // 加载知识库列表,默认选中第一个
  const loadKbs = useCallback(async () => {
    try {
      const list = await kbApi.list();
      setKbs(list);
      setKbId((cur) => cur ?? list[0]?.id ?? null);
    } catch (e) {
      messageApi.error(`加载知识库失败：${e instanceof Error ? e.message : String(e)}`);
    }
  }, [messageApi]);

  useEffect(() => {
    void loadKbs();
  }, [loadKbs]);

  // 加载当前库的文档列表
  const loadDocs = useCallback(async () => {
    if (kbId == null) {
      setDocs([]);
      setTotal(0);
      return;
    }
    setLoading(true);
    try {
      const res = await kbApi.listDocs(kbId, page, pageSize);
      setDocs(res.list);
      setTotal(res.total);
    } catch (e) {
      messageApi.error(`加载文档失败：${e instanceof Error ? e.message : String(e)}`);
    } finally {
      setLoading(false);
    }
  }, [kbId, page, pageSize, messageApi]);

  useEffect(() => {
    void loadDocs();
  }, [loadDocs]);

  const handleUpload = async (file: File): Promise<boolean> => {
    if (kbId == null) {
      messageApi.warning('请先选择知识库');
      return false;
    }
    setUploading(true);
    try {
      await kbApi.uploadDoc(kbId, file);
      messageApi.success(`已上传「${file.name}」，正在入库（可到“入库任务”页查看进度）`);
      void loadDocs();
    } catch (e) {
      messageApi.error(`上传失败：${e instanceof Error ? e.message : String(e)}`);
    } finally {
      setUploading(false);
    }
    return false; // 阻止 antd 默认上传行为(由我们手动发送)
  };

  const handleDelete = async (doc: DocItem) => {
    if (kbId == null) return;
    try {
      await kbApi.removeDoc(kbId, doc.id);
      messageApi.success(`已删除文档「${doc.fileName}」`);
      void loadDocs();
    } catch (e) {
      messageApi.error(`删除失败：${e instanceof Error ? e.message : String(e)}`);
    }
  };

  const columns: TableProps<DocItem>['columns'] = [
    { title: '文件名', dataIndex: 'fileName', key: 'fileName', ellipsis: true },
    { title: '大小', dataIndex: 'fileSize', key: 'fileSize', width: 100, render: formatSize },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (s: DocItem['status']) => {
        const cfg = DOC_STATUS[s];
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '分块数', dataIndex: 'chunkCount', key: 'chunkCount', width: 90, render: (v?: number) => v ?? '-' },
    { title: '上传时间', dataIndex: 'createdAt', key: 'createdAt', width: 170, render: formatDateTime },
    {
      title: '操作',
      key: 'actions',
      width: 90,
      render: (_, record) => (
        <Popconfirm
          title={`删除文档「${record.fileName}」？`}
          description="将同时删除该文档的全部分块。"
          okText="删除"
          okButtonProps={{ danger: true }}
          cancelText="取消"
          onConfirm={() => handleDelete(record)}
        >
          <Button type="text" danger size="small" icon={<DeleteOutlined />}>
            删除
          </Button>
        </Popconfirm>
      ),
    },
  ];

  return (
    <Card
      title="文档管理"
      extra={
        <div className="docs-toolbar">
          <Select
            className="docs-kb-select"
            placeholder="选择知识库"
            value={kbId ?? undefined}
            onChange={(v) => {
              setKbId(v);
              setPage(1);
            }}
            options={kbs.map((kb) => ({
              value: kb.id,
              label: `${kb.name}（${kb.docCount ?? 0} 文档）`,
            }))}
          />
          <Upload beforeUpload={(file) => handleUpload(file as File)} showUploadList={false} accept={ACCEPT} disabled={uploading}>
            <Button type="primary" icon={<UploadOutlined />} loading={uploading} disabled={kbId == null}>
              上传文档
            </Button>
          </Upload>
        </div>
      }
    >
      {contextHolder}
      <Table
        rowKey="id"
        dataSource={docs}
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
