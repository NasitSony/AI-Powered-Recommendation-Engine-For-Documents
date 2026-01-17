# AI-Powered Recommendation Engine
AI-Powered Recommendation Engine for Documents (SmartSearch)

A production-style semantic document search & recommendation backend built with Spring Boot and Spring AI. It ingests documents, generates vector embeddings, stores them in an interchangeable vector index (in-memory or PostgreSQL pgvector), and exposes APIs to retrieve the top-K most relevant documents for a natural-language query.

This project is designed to showcase how to orchestrate backend data flow into an AI model (embedding pipeline → vector store → ranking → API response) while keeping a clean, extensible architecture suitable for real services.

Architecture (High Level)

Flow:

Ingest: Client sends a document (id, text, optional metadata) to the service.

Embed: The service generates an embedding vector using Spring AI (LLM embedding model).

Index: The vector is stored in a pluggable vector store:

In-Memory Index (fast dev/demo)

pgvector (PostgreSQL) (persistent and scalable)

Search: Client sends a query string. The service embeds the query and performs top-K similarity search (cosine distance) to return ranked results.

Return: API returns document ids, similarity scores, and snippets/metadata.

Key design choices:

Pluggable vector store interface (VectorStore / DocumentIndex) to switch between in-memory and pgvector

Batch-friendly embedding pipeline to reduce latency/cost for bulk ingestion

Clear separation: Controller → Service → Persistence/Index


Quickstart
Prerequisites

Java 17+ (or the version your project uses)

(Optional) Docker for running Postgres + pgvector

An embedding provider key (e.g., OpenAI) configured via environment variables

Run (In-Memory Mode)

Set your embedding key:

export OPENAI_API_KEY="YOUR_KEY"

Start the application:

./mvnw spring-boot:run

Run (pgvector Mode)

Start Postgres + pgvector (example):

docker compose up -d

Configure DB + embedding key:

export OPENAI_API_KEY="YOUR_KEY"
export SPRING_PROFILES_ACTIVE="pgvector"

Start the application:

./mvnw spring-boot:run


API Demo (curl)

Update the host/port if your app runs differently.

1) Ingest documents
curl -s -X POST "http://localhost:8080/api/documents" \
  -H "Content-Type: application/json" \
  -d '{"id":"doc-1","text":"Asynchronous Byzantine agreement and MVBA protocols."}'
curl -s -X POST "http://localhost:8080/api/documents" \
  -H "Content-Type: application/json" \
  -d '{"id":"doc-2","text":"Spring Boot microservices, caching, and PostgreSQL performance tuning."}'
curl -s -X POST "http://localhost:8080/api/documents" \
  -H "Content-Type: application/json" \
  -d '{"id":"doc-3","text":"Kafka-based event streaming and exactly-once processing concepts."}'


2) Semantic search / recommendation
curl -s "http://localhost:8080/api/search?q=asynchronous%20byzantine%20consensus&k=3"

Example response shape:

{
  "query": "asynchronous byzantine consensus",
  "k": 3,
  "results": [
    { "id": "doc-1", "score": 0.87, "snippet": "Asynchronous Byzantine agreement..." },
    { "id": "doc-3", "score": 0.31, "snippet": "Kafka-based event streaming..." },
    { "id": "doc-2", "score": 0.12, "snippet": "Spring Boot microservices..." }
  ]
}

