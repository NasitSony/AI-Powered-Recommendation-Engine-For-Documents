# SmartSearch Baseline

## Purpose

Establish the performance and reliability baseline of SmartSearch before
introducing caching, sharding, replication, stream-processing changes,
or other scaling improvements.

## Environment

- Machine: Apple Mac mini M4
- Memory: 16 GB
- Java: 21
- Spring Boot
- Apache Kafka
- PostgreSQL 16
- pgvector
- Ollama
- Embedding model: nomic-embed-text
- Embedding dimensions: 768
- RAG model: llama3.1:8b

## Current Architecture

### Ingestion

Client
  -> Spring Boot API
  -> PostgreSQL PENDING record
  -> Kafka
  -> Worker
  -> Chunking
  -> Ollama embedding
  -> PostgreSQL/pgvector
  -> READY

### Search

Query
  -> Ollama query embedding
  -> PostgreSQL/pgvector similarity search
  -> Top-K chunks

### RAG

Question
  -> Semantic retrieval
  -> Retrieved chunks
  -> Llama 3.1 8B
  -> Grounded answer

## Baseline Configuration

- Kafka partitions: 1
- Kafka consumer group: smartsearch-workers
- Worker count: 1
- Ingest topic: smartsearch.ingest.v6
- Vector dimensions: 768

## Metrics To Measure

### Ingestion

- accepted requests/sec
- documents/sec
- chunks/sec
- PENDING -> READY latency
- p50 processing latency
- p95 processing latency
- p99 processing latency
- failures
- retries
- Kafka consumer lag

### Search

- requests/sec
- p50 latency
- p95 latency
- p99 latency
- max latency

### RAG

- p50 latency
- p95 latency
- p99 latency

### Resources

- application CPU
- application memory
- PostgreSQL CPU/memory
- Kafka CPU/memory
- Ollama CPU/GPU/memory

## Experiments

Baseline experiments have not been run yet.


## Baseline Environment

### Kafka

- Partitions: 1
- Consumer group: `smartsearch-workers`
- `max.poll.records`: 1
- Ack mode: manual
- Auto commit: disabled
- Ingest topic: `smartsearch.ingest.v6`

### AI Models

- Embedding model: `nomic-embed-text`
- Embedding dimensions: 768
- RAG model: `llama3.1:8b`
- Runtime: Ollama

### Infrastructure Idle Resource Usage

| Component | Memory | CPU |
|---|---:|---:|
| PostgreSQL | 72.62 MiB | 0.02% |
| Kafka | 381.8 MiB | 0.90% |
| Kafka UI | 238.4 MiB | 0.10% |
| Prometheus | 128 MiB | 0.31% |
| Grafana | 197.1 MiB | 6.12% |
| ZooKeeper | 104.5 MiB | 0.29% |

### Baseline Smoke Test

Test document:
- Request ID: `baseline-check-001`
- HTTP response: `202 Accepted`
- Final status: `READY`
- Retries: 0
- Error: none

Timestamps:
- Created: `2026-08-17T00:16:47.329426Z`
- Ready/updated: `2026-08-17T00:16:47.890822Z`
- Approx. PENDING → READY latency: **561 ms**

> This is a smoke-test observation, not a benchmark result.

