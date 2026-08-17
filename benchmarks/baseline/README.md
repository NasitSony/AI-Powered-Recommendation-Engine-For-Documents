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

## Experiment B1 — Sequential Ingestion

Configuration:
- Documents: 100
- Producer concurrency: 1
- Kafka partitions: 1
- Consumers: 1
- max.poll.records: 1
- Embedding: nomic-embed-text
- Vector dimensions: 768
- Model state: warm

Results:
- READY: 100/100
- Failed: 0
- Throughput: X docs/s
- Average: X ms
- p50: X ms
- p95: X ms
- p99: X ms
- Max: X ms

Raw data:
`ingestion-YYYYMMDD-HHMMSS.csv`


## Experiment B1 — Sequential End-to-End Ingestion Latency

### Configuration

- Documents: 100
- Producer concurrency: 1
- Kafka partitions: 1
- Consumer group: `smartsearch-workers`
- `max.poll.records`: 1
- Embedding model: `nomic-embed-text`
- Vector dimensions: 768
- All requests used unique request IDs

### Results

- READY: 100/100
- FAILED: 0
- Average PENDING → READY latency: 32.364 ms
- p50: 27.843 ms
- p95: 30.505 ms
- p99: 50.894 ms
- Max: 484.147 ms
- First request: 484.147 ms

### Observation

Steady-state ingestion latency was tightly clustered around 25–31 ms.

The first request showed a substantial warm-up/cold-start penalty.

The measured wall-clock rate of 4.989 docs/s should NOT be interpreted
as maximum ingestion throughput because the benchmark submits one
document and waits for READY before submitting the next. The shell-based
benchmark also introduces polling and process-launch overhead.

A separate asynchronous load experiment is required to measure pipeline
throughput.

Observation:
At ~34 submitted docs/sec, SmartSearch processed the workload without backlog,
failures, or meaningful tail-latency growth.

Conclusion:
The single-worker pipeline saturation point is above ~34 docs/sec for this
small one-chunk document workload.

## Experiment B3 — Saturated Concurrent Ingestion

### Configuration

- Documents: 500
- Producer concurrency: 10
- Kafka partitions: 1
- Consumer group: smartsearch-workers
- max.poll.records: 1
- Embedding model: nomic-embed-text
- Embedding dimensions: 768

### Results

- Producer rate: 127.595 docs/s
- Completion rate: 32.435 docs/s
- READY: 500/500
- FAILED: 0
- Average latency: 6588.879 ms
- p50: 6668.360 ms
- p95: 11138.781 ms
- p99: 11520.164 ms
- Max: 11606.546 ms

### Observation

The producer generated work approximately 4x faster than the
single-consumer pipeline could process it.

Kafka absorbed the burst and all 500 documents eventually reached READY,
but queueing caused substantial latency amplification.

Compared with the unsaturated B2 workload, median PENDING-to-READY
latency increased from approximately 26 ms to 6.7 seconds.

### Conclusion

 The 100-document B2 run showed that the pipeline could sustain approximately
34 docs/sec over a short workload. The longer B4 experiments later showed
that this rate is near the single-consumer saturation region rather than
comfortably below it.

When arrival rate substantially exceeds service rate, Kafka protects
the API/worker boundary from immediate overload, but queue waiting time
dominates end-to-end ingestion latency.

Concurrency 1 / 500 docs
Producer:   33.43 docs/s
Completion: 33.49 docs/s
p50:        26 ms
p95:        238 ms
p99:        493 ms

Interpretation:
Arrival rate approximately equals service capacity.
Median latency remains low, but tail latency increases,
indicating intermittent queue buildup near saturation.

## Experiment B4 — Producer Concurrency Sweep

### Goal

Determine where the single-consumer ingestion pipeline begins to saturate
without changing Kafka partitions, worker count, embedding model, or
document size.

### Fixed Configuration

- Documents per run: 500
- Kafka partitions: 1
- Consumer group: `smartsearch-workers`
- `max.poll.records`: 1
- Embedding model: `nomic-embed-text`
- Embedding dimensions: 768
- Small one-chunk documents

### Results

| Producer Concurrency | Producer Rate | Completion Rate | p50 | p95 | p99 | Failed |
|---:|---:|---:|---:|---:|---:|---:|
| 1 | 33.429 docs/s | 33.493 docs/s | 26 ms | 238 ms | 493 ms | 0 |
| 2 | 61.914 docs/s | 37.126 docs/s | 2.848 s | 5.168 s | 5.392 s | 0 |
| 10 | 127.595 docs/s | 32.435 docs/s | 6.668 s | 11.139 s | 11.520 s | 0 |

### Observation

At producer concurrency 1, arrival rate and completion rate were almost
identical at approximately 33.5 docs/s. Median latency remained low at
26 ms, but p95 and p99 increased to 238 ms and 493 ms, indicating
intermittent queue buildup near saturation.

At producer concurrency 2, the producer generated approximately
61.9 docs/s while the pipeline completed approximately 37.1 docs/s.
The excess arrival rate accumulated as queued work, increasing median
PENDING-to-READY latency to approximately 2.85 seconds and p95 to
approximately 5.17 seconds.

At producer concurrency 10, arrival rate increased to approximately
127.6 docs/s while completion throughput remained approximately
32.4 docs/s. Median latency increased further to approximately
6.67 seconds and p95 reached approximately 11.14 seconds.

All workloads completed successfully with zero failed documents.

### Conclusion

For this workload and local environment, the current single-consumer
pipeline has an observed service-capacity region of approximately
33–37 documents/sec.

Increasing producer concurrency beyond this point does not materially
increase completion throughput. Instead, excess work accumulates in
the asynchronous ingestion pipeline and substantially increases
PENDING-to-READY latency.

Kafka allows SmartSearch to absorb these bursts without losing work,
but buffering protects reliability rather than eliminating overload.
