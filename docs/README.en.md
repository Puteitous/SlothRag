<h1 align="center">
  <img src="../assets/slothrag.svg" alt="SlothRag" width="40" height="40" style="vertical-align: middle; margin-right: 8px;">
  SlothRag
</h1>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white" alt="Java 21">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.4-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot">
  <img src="https://img.shields.io/badge/React-18-61DAFB?logo=react&logoColor=white" alt="React 18">
  <img src="https://img.shields.io/badge/TypeScript-5.6-3178C6?logo=typescript&logoColor=white" alt="TypeScript">
  <img src="https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white" alt="PostgreSQL">
  <img src="https://img.shields.io/badge/license-Apache%202.0-555555" alt="License">
  <img src="https://img.shields.io/github/last-commit/Puteitous/SlothRag" alt="Last Commit">
</p>

<p align="center">
  <a href="../README.md">简体中文</a> | English
</p>

A RAG knowledge base Q&A system with AI-powered chat, document ingestion, and conversation management.

## Features

| Feature | Description |
|---|---|
| **AI Chat** | Streaming chat with real-time response |
| **RAG Search** | Knowledge base retrieval with rerank |
| **Document Ingestion** | Parse, chunk, and embed documents (PDF, DOCX, PPTX, XLSX, MD) |
| **Knowledge Base** | Manage KBs, browse docs, monitor ingest tasks |
| **Conversation History** | Session management with history replay |
| **Agent Tools** | Knowledge search tool integration |
| **Admin Panel** | Auth, KB management, task monitoring |
| **Theme Switch** | Light / dark mode toggle |

## Tech Stack

| Layer | Technology |
|---|---|
| Frontend | React 18, TypeScript, Zustand, Ant Design |
| Backend | Java 21, Spring Boot, Virtual Threads |
| AI | OpenAI-compatible LLM / Embedding / Rerank |
| Database | PostgreSQL + pg_jieba (Chinese tokenizer) |
| Build | Maven, Vite |
| Deploy | Docker, docker-compose |

## Quick Start

### Prerequisites

- Java 21
- Node.js 20+
- PostgreSQL 16+
- Maven 3.9+

### Docker

```bash
docker compose up -d
```

### Manual

```bash
# 1. Build backend
mvn package -DskipTests
java -jar target/slothrag-*.jar

# 2. Start frontend
cd frontend
npm install
npm run dev
```

### Configuration

Edit `src/main/resources/application.yml`:

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

## Project Structure

```
src/main/java/com/slothrag/
├── admin/auth/              Auth & user management
├── agent/                   Agent tools
├── ai/                      LLM, embedding, rerank clients
├── chat/                    Chat controller & service
├── common/web/              Common utilities
├── config/                  Configuration
├── conversation/            Conversation management
├── knowledge/               Knowledge base & ingestion
├── search/                  Search service
└── session/                 Session store

frontend/src/
├── admin/                   Admin pages
├── api/                     API clients
├── components/              UI components
├── hooks/                   Custom hooks
├── i18n/                    Internationalization
├── stores/                  Zustand stores
├── types/                   TypeScript types
└── utils/                   Utilities
```

## License

[Apache License 2.0](../LICENSE)