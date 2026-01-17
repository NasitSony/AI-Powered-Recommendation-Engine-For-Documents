# AI-Powered Recommendation Engine
# AI-Powered Recommendation Engine for Documents

AI-Powered Recommendation Engine for Documents is a semantic search and recommendation backend built with **Spring Boot**, **Spring AI**, and **pgvector**. It enables ingesting documents, generating vector embeddings, storing them in a vector index, and performing **semantic retrieval** for natural-language queries.

Semantic search finds results based on *meaning and context* instead of exact keyword matches, using vector embeddings stored inside PostgreSQL with the **pgvector** extension. :contentReference[oaicite:2]{index=2}

---

## 🚀 Features

- Document ingestion with embedding generation  
- Vector storage using pgvector or in-memory index  
- Top-K semantic similarity search API  
- Extensible architecture for storage backends  
- Designed for production-style backend use cases

---

## 🛠️ Tech Stack

| Component | Purpose |
|-----------|---------|
| Spring Boot | Application framework |
| Spring AI | Embeddings & AI integration |
| PostgreSQL + pgvector | Persistent vector search |
| REST APIs | Document ingestion & search |
| Maven | Build & dependency management |

---

## 📦 Setup & Run (Local)

### Prerequisites
- Java 17+  
- Docker (for PostgreSQL + pgvector)  
- `OPENAI_API_KEY` (or other provider) set as an environment variable

### Step 1 — Database
```bash
docker compose up -d

### Step 2 — Set Environment Variables
```bash
export OPENAI_API_KEY="YOUR_KEY"

### Step 3 — Run the App
```bash
./mvnw spring-boot:run

### ▶️ Full Demo Flow (Copy–Paste)

```bash
# -----------------------------
# 1) Ingest sample documents
# -----------------------------

curl -s -X POST "http://localhost:8080/api/documents" \
  -H "Content-Type: application/json" \
  -d '{"id":"doc-1","text":"Asynchronous Byzantine agreement and MVBA protocols for fault-tolerant distributed systems."}'

curl -s -X POST "http://localhost:8080/api/documents" \
  -H "Content-Type: application/json" \
  -d '{"id":"doc-2","text":"Spring Boot microservices, caching strategies, and PostgreSQL performance tuning."}'

curl -s -X POST "http://localhost:8080/api/documents" \
  -H "Content-Type: application/json" \
  -d '{"id":"doc-3","text":"Kafka-based event streaming, exactly-once semantics, and real-time data pipelines."}'


# -----------------------------
# 2) Run semantic search
# -----------------------------

curl -s "http://localhost:8080/api/search?q=asynchronous%20byzantine%20consensus&k=3"



# SmartSearch (v0.5) — Semantic Chunk Search + RAG (Spring AI + pgvector)

SmartSearch is a production-style backend that ingests documents, chunks them, embeds each chunk, stores vectors in PostgreSQL (pgvector), and provides:
- `/api/search`: semantic top-k retrieval over document chunks
- `/api/ask`: RAG endpoint that answers questions using retrieved chunks with citations

## Tech Stack
- Java + Spring Boot
- Spring AI (EmbeddingModel + ChatModel)
- PostgreSQL + pgvector
- JPA for document storage (metadata)
- JdbcTemplate for pgvector inserts/search (`CAST(? AS vector)`, `<->` distance)

## Architecture

```text
Client
  |
  | POST /api/documents
  | GET  /api/search?q=...&k=...
  | GET  /api/ask?q=...&k=...
  v
Spring MVC Controller
  v
Service Layer
  - DocumentService: chunk -> embed -> store (doc + chunks)
  - RagService: retrieve top-k chunks -> prompt -> LLM answer (+ citations)
  v
Persistence Layer
  - Documents: JPA (id, text, created_at, embedding as TEXT)
  - Chunks: JdbcTemplate writes (vector cast) + pgvector similarity search
  v
PostgreSQL + pgvector
  - document_chunks.embedding VECTOR(1536)
  - similarity: embedding <-> query_vector

