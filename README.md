# SlothRag

基于 RAG 的知识库问答系统，支持 AI 对话、文档导入与对话管理。

## 功能概览

| 功能 | 说明 |
|---|---|
| **AI 对话** | 流式对话，实时响应 |
| **RAG 检索** | 知识库检索 + Rerank 重排序 |
| **文档导入** | 支持 PDF、DOCX、PPTX、XLSX、MD 等格式解析、分块与向量化 |
| **知识库管理** | 创建知识库、管理文档、监控导入任务 |
| **对话历史** | 会话管理，支持历史记录回显 |
| **Agent 工具** | 知识搜索工具调用 |
| **管理后台** | 登录认证、知识库管理、任务监控 |
| **主题切换** | 明暗两种主题自由切换 |

## 技术栈

| 层 | 技术 |
|---|---|
| 前端 | React 18, TypeScript, Zustand, Ant Design |
| 后端 | Java 21, Spring Boot, 虚拟线程 |
| AI | OpenAI 兼容接口（LLM / Embedding / Rerank） |
| 数据库 | PostgreSQL + pg_jieba（中文分词） |
| 构建 | Maven, Vite |
| 部署 | Docker, docker-compose |

## 快速开始

### 环境要求

- Java 21
- Node.js 20+
- PostgreSQL 16+
- Maven 3.9+

### Docker 启动

```bash
docker compose up -d
```

### 手动启动

```bash
# 1. 编译后端
mvn package -DskipTests
java -jar target/slothrag-*.jar

# 2. 启动前端
cd frontend
npm install
npm run dev
```

### 配置说明

编辑 `src/main/resources/application.yml`：

```yaml
ai:
  llm:
    api-key: your-api-key
    model: deepseek-chat
    base-url: https://api.deepseek.com
  embedding:
    api-key: your-api-key
  rerank:
    api-key: your-api-key
```

## 项目结构

```
src/main/java/com/slothrag/
├── admin/auth/              认证与用户管理
├── agent/                   工具调用
├── ai/                      LLM、Embedding、Rerank 客户端
├── chat/                    聊天控制器与服务
├── common/web/              通用工具
├── config/                  配置
├── conversation/            对话管理
├── knowledge/               知识库与文档导入
├── search/                  检索服务
└── session/                 会话存储

frontend/src/
├── admin/                   管理后台页面
├── api/                     API 客户端
├── components/              UI 组件
├── hooks/                   自定义 Hooks
├── i18n/                    国际化
├── stores/                  Zustand 状态管理
├── types/                   TypeScript 类型定义
└── utils/                   工具函数
```

## 开源协议

[Apache License 2.0](./LICENSE)
