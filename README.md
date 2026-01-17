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

