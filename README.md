# SlothRag

A RAG knowledge base Q&A system with AI-powered chat, document ingestion, and conversation management.

## Features

| Feature | Description |
|---|---|
| **AI Chat** | Streaming chat with LLM, real-time response |
| **RAG Search** | Knowledge base retrieval with rerank |
| **Document Ingestion** | Parse, chunk, and embed documents (PDF, DOCX, PPTX, XLSX, MD) |
| **Knowledge Base** | Manage KBs, browse docs, monitor ingest tasks |
| **Conversation History** | Session management with history replay |
| **Agent Tools** | Knowledge search tool integration |
| **Admin Panel** | Auth, KB management, task monitoring |
| **Theme** | Light / dark mode switch |

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

### Start with Docker

```bash
docker compose up -d
```

### Start manually

```bash
# 1. Backend
mvn package -DskipTests
java -jar target/slothrag-*.jar

# 2. Frontend
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

[Apache License 2.0](./LICENSE)
