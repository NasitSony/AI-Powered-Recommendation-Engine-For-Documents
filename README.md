# AI‑Powered Recommendation Engine for Documents (SmartSearch)

SmartSearch is a semantic search and Retrieval‑Augmented Generation
(RAG) backend built with:

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

------------------------------------------------------------------------

## 🧱 Architecture

Client \| \| POST /api/documents \| GET /api/search?q=...&k=... \| GET
/api/ask?q=...&k=... v Spring MVC Controller v Service Layer -
DocumentService: chunk → embed → store - RagService: retrieve → prompt →
LLM generate v JdbcTemplate + JPA + pgvector v PostgreSQL

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

------------------------------------------------------------------------

## 🎯 Motivation

This project demonstrates how to integrate LLMs into traditional Java
backends by:

-   orchestrating data flow between databases and AI models
-   implementing production‑style semantic retrieval pipelines
-   enabling grounded question answering using RAG

It is intended as a foundation for further work on intelligent document
systems and protocol research tools.
