# ragbase 设计与实施计划

> 企业知识库问答产品：多格式文档入库 → 向量化检索 → AI 问答（带溯源）。
> 本文档沉淀前期讨论的关键决策与实施路线，是后续开发的参照。

---

## 1. 项目定位

- **形态**：服务化部署的 Web 产品（多人使用、管理后台、权限管控）
- **核心链路**：文档入库（Pipeline）→ 混合检索 → LLM 问答（SSE 流式、来源引用）
- **差异化**：Agent 引擎复用 HippoBuddy，知识底座设计借鉴 ragent，但只保留必需中间件

## 2. 技术栈与选型理由

| 层 | 选型 | 理由 |
|:---|:---|:---|
| 后端 | Java 21 + Spring Boot 3.5 | RAG 产品是"服务"（权限/任务/审计/运维），Java 服务化能力最强；两个参考项目均为 Java，存量可复用 |
| 数据+向量+关键词 | PostgreSQL + pgvector | **一个中间件搞定三件事**：业务表、向量检索、关键词全文检索（PG FTS），运维成本最低，Dify 默认同款 |
| 文档解析 | Apache Tika | Java 解析事实标准，PDF/Word/PPT/HTML 全覆盖，不造轮子 |
| Embedding | BGE-M3 API（硅基流动等） | 国内成熟，多语言好；MVP 调 API，后期可换本地部署 |
| Rerank | BGE-Reranker API | 阶段 2 引入，MVP 可不要 |
| 前端 | React + Ant Design | B 端管理后台事实标准组件库 |
| 会话 | JSONL 文件追加（照搬 HippoBuddy） | 已验证：顺序追加 = 稳定前缀，天然适配长窗口 + 上下文缓存命中 |

> **关于语言**：AI 应用层的 LLM/Embedding 调用本质都是 HTTP API，语言差异不在 AI 能力，而在服务化能力、生态存量、开发效率。做企业级知识库产品，Java 是匹配度最高的载体；且 HippoBuddy（引擎）与 ragent（知识底座）均为 Java，无需跨语言翻译。

## 3. 模块结构（单工程多包）

```
com.ragbase
├── bootstrap      启动类
├── common/web     统一响应、异常处理
├── config         配置
├── auth           登录、权限（阶段 3）
├── knowledge      知识库域：库/文档管理、入库任务、分块
│   ├── ingest     入库 Pipeline：解析→分块→embedding→写库
│   └── chunk      分块策略
├── search         检索域：向量+关键词双通道、RRF 融合、Rerank
├── agent          从 HippoBuddy 迁移的引擎（execute/llm/tool）
│   └── tool       检索工具、溯源工具（新注册）
├── chat           问答编排：上下文组装→LLM→SSE
├── session        会话管理（JSONL 方案）
└── admin          管理后台 API
```

## 4. 核心流程

**入库**：
```
上传文档 → Tika 解析 → 递归分块(+overlap) → BGE-M3 向量化 → 写 pgvector + 关键词索引
（异步任务，带进度/失败重试）
```

**检索**：
```
问题 → embedding → 向量检索(pgvector) ∥ 关键词检索(PG FTS)
     → RRF 融合 → Rerank → top-k 上下文
```

**问答**：
```
问题 + 会话历史(JSONL, 稳定前缀) + 检索上下文 → LLM → SSE 流式输出
     → 回答带来源引用
```

## 5. 数据模型（首批 6 张表）

| 表 | 说明 |
|:---|:---|
| `kb` | 知识库（名称、embedding 模型） |
| `doc` | 文档（状态、存储位置、解析结果） |
| `chunk` | 分块（pgvector 向量列 + 文本 + 元数据） |
| `ingest_task` | 入库任务（状态、进度、日志） |
| `conversation` | 会话（sessionId 关联 JSONL 文件） |
| `user` | 用户（后台管理员） |

## 6. 设计原则（控制复杂度）

- **中间件只有 PostgreSQL 一个**，不学 ragent 堆 ES/Milvus/RocketMQ
- **不做记忆模块**：会话 = JSONL + 全量上下文 + 缓存命中（百万窗口时代，摘要压缩型记忆已过时）
- **不做意图树**：路由用 function calling / 简单分类，不引入重路由
- **检索/Embedding/模型全部接口抽象**，后期可换实现
- **Rerank、权限、审计按阶段增量引入**，MVP 不做
- **中文关键词检索**（参考 ragent：ES + IK 分词 ik_max_word/ik_smart）：
  - MVP：`LIKE '%词%'` 子串匹配，零依赖直接命中
  - 正式：PG 装 `pg_jieba` / `zhparser` 中文分词扩展（功能等价 IK），不引入第二个中间件
  - 已知问题：PG 内置 simple/english 分词器不支持中文，FTS 对中文无效

## 7. 实施路线

| 阶段 | 内容 | 里程碑 |
|:---|:---|:---|
| 1 · MVP | Spring Boot 骨架（已完成）+ PG 初始化 + Tika 解析 txt/md + 入库 + 向量检索 + 问答(SSE) | 能基于文档问答 |
| 2 · 引擎整合 | 迁移 HippoBuddy agent 层，检索挂成工具，加 Rerank、多知识库、文档上传 | 完整问答 + 检索工具化 |
| 3 · 产品化 | 管理后台（React）、权限、审计、任务监控、多格式文档 | 可部署的产品 |

## 8. 参考项目分工

| 参考 | 借鉴内容 |
|:---|:---|
| **HippoBuddy** | Agent 引擎机制层（execute/llm/ToolRegistry）、会话 JSONL 方案、Office 解析器（阶段 3） |
| **ragent** | 知识底座设计：入库 Pipeline、多路检索融合、分块策略、来源引用、检索预算 |

> 只抄"底座层"（入库/检索/融合/溯源/容错），砍"系统层"（意图树、会话记忆、限流排队、管理后台的全套重设计）。
