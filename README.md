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

### ▶️ Full Demo Flow 

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
```

### Example Response

```json
{
  "query": "asynchronous byzantine consensus",
  "k": 3,
  "results": [
    {
      "id": "doc-1",
      "score": 0.87,
      "snippet": "Asynchronous Byzantine agreement and MVBA protocols..."
    },
    {
      "id": "doc-3",
      "score": 0.29,
      "snippet": "Kafka-based event streaming and exactly-once semantics..."
    },
    {
      "id": "doc-2",
      "score": 0.11,
      "snippet": "Spring Boot microservices and PostgreSQL performance tuning..."
    }
  ]
}
```

**What this demonstrates:**

1. Documents are ingested and embedded using Spring AI
2. Embeddings are stored in the vector index (pgvector or in-memory)
3. Query is embedded and matched using cosine similarity
4. Results are returned in ranked order by semantic relevance


