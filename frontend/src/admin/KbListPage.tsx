import { useCallback, useEffect, useState } from 'react';
import { Button, Card, Form, Input, Modal, Popconfirm, Table, Tag, message } from 'antd';
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons';
import type { TableProps } from 'antd';
import { kbApi } from '@/api/client';
import type { KbItem } from '@/types';
import { formatDateTime } from './format';

/**
 * 库管理页:知识库列表 + 新建 + 删除(级联删文档/分块)
 */
export default function KbListPage() {
  const [kbs, setKbs] = useState<KbItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [form] = Form.useForm();
  const [messageApi, contextHolder] = message.useMessage();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setKbs(await kbApi.list());
    } catch (e) {
      messageApi.error(`加载知识库失败：${e instanceof Error ? e.message : String(e)}`);
    } finally {
      setLoading(false);
    }
  }, [messageApi]);

  useEffect(() => {
    void load();
  }, [load]);

  const handleCreate = async () => {
    try {
      const values = await form.validateFields();
      setCreating(true);
      await kbApi.create(values.name, values.description);
      messageApi.success('知识库已创建');
      setModalOpen(false);
      form.resetFields();
      void load();
    } catch (e) {
      if (e instanceof Error) messageApi.error(`创建失败：${e.message}`);
    } finally {
      setCreating(false);
    }
  };

  const handleDelete = async (record: KbItem) => {
    try {
      await kbApi.remove(record.id);
      messageApi.success(`已删除知识库「${record.name}」`);
      void load();
    } catch (e) {
      messageApi.error(`删除失败：${e instanceof Error ? e.message : String(e)}`);
    }
  };

  const columns: TableProps<KbItem>['columns'] = [
    { title: '名称', dataIndex: 'name', key: 'name', render: (v: string) => <span className="kb-name-cell">{v}</span> },
    { title: '描述', dataIndex: 'description', key: 'description', ellipsis: true, render: (v?: string) => v || '-' },
    { title: '文档数', dataIndex: 'docCount', key: 'docCount', width: 90, render: (v?: number) => v ?? 0 },
    { title: 'Embedding 模型', dataIndex: 'embeddingModel', key: 'embeddingModel', width: 200, render: (v?: string) => <Tag>{v ?? '-'}</Tag> },
    { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 170, render: formatDateTime },
    {
      title: '操作',
      key: 'actions',
      width: 90,
      render: (_, record) => (
        <Popconfirm
          title={`删除知识库「${record.name}」？`}
          description="将级联删除库内所有文档与分块，且不可恢复。"
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
      title="知识库"
      extra={
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>
          新建知识库
        </Button>
      }
    >
      {contextHolder}
      <Table rowKey="id" dataSource={kbs} loading={loading} columns={columns} pagination={false} />
      <Modal
        open={modalOpen}
        title="新建知识库"
        okText="创建"
        cancelText="取消"
        confirmLoading={creating}
        onOk={handleCreate}
        onCancel={() => {
          setModalOpen(false);
          form.resetFields();
        }}
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入知识库名称' }]}>
            <Input placeholder="如：员工制度库" maxLength={50} />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea placeholder="选填" rows={3} maxLength={200} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
