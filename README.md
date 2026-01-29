# AI‑Powered Recommendation Engine for Documents (SmartSearch)

> 🚧 This project is being **built in public** to explore how AI-powered search systems evolve when real backend and distributed-systems concerns are introduced.

SmartSearch is an AI-powered document search and recommendation backend, starting with semantic search and RAG and evolving toward reliable, production-style distributed services, built with:

-   Java + Spring Boot
-   Spring AI (EmbeddingModel + ChatModel)
-   PostgreSQL + pgvector
-   Chunk‑level semantic search with RAG responses

------------------------------------------------------------------------

## 🔎 What It Does

This project provides APIs to:

### 📥 Ingest documents

Breaks large text documents into chunks and embeds each chunk.

### 📚 Semantic search

Retrieve most relevant chunks for a query using vector similarity.

### 🤖 RAG Q&A

Answer questions using the retrieved chunks as evidence, with citations.

------------------------------------------------------------------------

## 🚀 Features

### 🧠 Chunking + Embeddings

-   Paragraph‑level document chunking
-   Embeds each chunk using an embedding model

### 📍 pgvector Semantic Search

-   Stores chunk vectors in PostgreSQL with pgvector
-   Fast similarity search using `<->` distance operator

### 🗣️ RAG API

-   `/api/ask`: Answers questions grounded in retrieved chunks with
    citations like `[docId#chunkId]`


### 🧵 Asynchronous Ingestion with Kafka (v0.6)

To decouple API responsiveness from embedding and storage workflows, SmartSearch introduces **Kafka-based asynchronous ingestion**.

- API layer publishes ingestion requests as events
- Worker service consumes events and performs:
  - document chunking
  - embedding generation
  - vector persistence
- Request lifecycle is tracked explicitly (`PENDING → SUCCESS`)
- Failure scenarios are surfaced rather than hidden (FAILED handling in progress)

This design shifts the system from a synchronous demo pipeline to an **event-driven backend**, exposing real-world reliability and correctness challenges.    

------------------------------------------------------------------------

## 🧱 Architecture

Client \| \| POST /api/documents \| GET /api/search?q=...&k=... \| GET
/api/ask?q=...&k=... v Spring MVC Controller v Service Layer -
DocumentService: chunk → embed → store - RagService: retrieve → prompt →
LLM generate v JdbcTemplate + JPA + pgvector v PostgreSQL


Client  
↓  
Spring Boot API  
- POST /api/documents  
- Publishes ingestion event (Kafka)  
↓  
Kafka Topic (`document.ingestion`)  
↓  
Worker / Consumer Service  
- Chunk document  
- Generate embeddings  
- Persist vectors  
↓  
PostgreSQL + pgvector  

Search & RAG Flow (Synchronous):  
Client → API → pgvector similarity search → LLM → Response


### Architecture Notes

Recent iterations introduce asynchronous ingestion to decouple API responsiveness from embedding and storage workflows. This surfaced important reliability concerns around partial failures, retries, and state persistence, which are being addressed incrementally.


------------------------------------------------------------------------

## 📦 API Endpoints

### POST /api/documents

Add or update a document (with chunking and embedding):

``` bash
curl -X POST http://localhost:8080/api/documents   -H "Content-Type: application/json"   -d '{"id":"doc-1","text":"..."}'
```

### GET /api/search

Semantic search over document chunks:

``` bash
curl "http://localhost:8080/api/search?q=mvba&k=3"
```

### GET /api/ask

Retrieval‑Augmented Generation (RAG) question answering:

``` bash
curl "http://localhost:8080/api/ask?q=What%20is%20MVBA%3F&k=5"
```

------------------------------------------------------------------------

## 🛠️ Setup

### 1️⃣ PostgreSQL (no Docker required)

``` sql
CREATE DATABASE smartsearch;
\c smartsearch
CREATE EXTENSION vector;
```

### 2️⃣ Tables

documents: - id TEXT PRIMARY KEY - text TEXT - created_at TIMESTAMP -
embedding TEXT

document_chunks: - doc_id TEXT - chunk_id INT - chunk_text TEXT -
created_at TIMESTAMP - embedding VECTOR(1536) - PRIMARY KEY (doc_id,
chunk_id)

### 3️⃣ Configure Spring AI

Set your provider API key in environment variables or `application.yml`.

Example for OpenAI:

``` yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4o-mini
```

------------------------------------------------------------------------

## 📝 Example Response

``` json
{
  "question": "What is MVBA?",
  "answer": "MVBA handles multiple values [doc-test#1].",
  "sources": [
    {"docId":"doc-test","chunkId":1,"chunkText":"MVBA handles multiple values.","distance":0.78},
    {"docId":"doc-test","chunkId":0,"chunkText":"Byzantine agreement ensures safety.","distance":1.30}
  ]
}
```

### 4️⃣ Kafka (for async ingestion)

Kafka is used for asynchronous document ingestion in v0.6.

You can run Kafka locally using Docker Compose or any local Kafka setup.
The API publishes ingestion events, and a worker service consumes and processes them.

(Failure handling, retries, and dead-letter queues are being added incrementally.)

------------------------------------------------------------------------


## Project Evolution

SmartSearch is an incremental backend project that started with semantic search and RAG, and is gradually evolving toward production-style reliability and correctness.

- **v0.5 — Semantic Search & RAG Core**
  - Spring AI–based embeddings and chat models
  - PostgreSQL + pgvector for vector similarity search
  - Paragraph-level chunking and retrieval
  - RAG question answering with grounded citations

- **v0.6 — Async Ingestion & Reliability Foundations**
  - Kafka-based event-driven ingestion, decoupling API latency from embedding and persistence
  - Decoupled API and worker-style processing
  - Explicit request lifecycle states (PENDING → SUCCESS)
  - Focus on observability and failure-mode awareness

- **Next — Failure Handling & Correctness**
  - Reliable FAILED-state persistence
  - Retry semantics and idempotent writes
  - Dead-letter handling and error classification
 
------------------------------------------------------------------------

## 🎯 Motivation

This project demonstrates how to integrate LLMs into traditional Java
backends by:

-   orchestrating data flow between databases and AI models
-   implementing production‑style semantic retrieval pipelines
-   enabling grounded question answering using RAG

It is intended as a foundation for further work on intelligent document
systems and protocol research tools.

------------------------------------------------------------------------

## Roadmap

- [x] Semantic search with pgvector
- [x] RAG question answering with citations
- [x] Async ingestion foundation
- [ ] Reliable FAILED-state persistence
- [ ] Retry and idempotency guarantees
- [ ] Dead-letter handling and observability improvements
