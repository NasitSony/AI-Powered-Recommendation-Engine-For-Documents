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

## Experiment B5 — More Consumers Without More Partitions

### Configuration
- Documents: 500
- Producer concurrency: 2
- Kafka partitions: 1
- Listener concurrency: 2
- Consumer group: `smartsearch-workers`
- Embedding model: `nomic-embed-text`

### Results
- Producer rate: 58.610 docs/s
- Completion rate: 35.672 docs/s
- READY: 500/500
- FAILED: 0
- Average latency: 3140.987 ms
- p50: 3180.201 ms
- p95: 5349.675 ms
- p99: 5553.893 ms
- Max: 5615.386 ms

### Observation
Increasing Spring Kafka listener concurrency from 1 to 2 did not materially
increase completion throughput.

The topic still had only one Kafka partition, so only one consumer in the
group could actively process records from the ingest topic.

### Conclusion
Kafka partition count bounded consumer-group parallelism. Adding consumers
without adding partitions did not move the approximately 33–37 docs/sec
service-capacity region.

## Experiment B6 — Two Partitions, Two Consumers

### Configuration
- Documents: 500
- Producer concurrency: 2
- Kafka partitions: 2
- Listener concurrency: 2
- Consumer group: `smartsearch-workers`
- Embedding model: `nomic-embed-text`
- Embedding dimensions: 768

### Results
- Producer rate: 57.496 docs/s
- Completion rate: 58.089 docs/s
- READY: 500/500
- FAILED: 0
- Average latency: 102.276 ms
- p50: 54.552 ms
- p95: 370.210 ms
- p99: 489.586 ms
- Max: 527.382 ms

### Comparison with B5
B5 used one partition with two consumers and completed approximately
35.7 docs/s.

B6 increased the ingest topic to two partitions while keeping two
consumers and the same workload.

Completion throughput increased to approximately 58.1 docs/s.

### Conclusion
Kafka partition count was a real throughput constraint in the original
configuration.

Adding consumers without partitions provided no useful parallelism.
Increasing partitions allowed both consumers to process records concurrently,
raising throughput by roughly 63%.

The system is now close to the producer rate at concurrency 2, so queueing
latency dropped dramatically.

## Experiment B7 — Saturation After Consumer Scaling

### Goal

Determine the saturation behavior of the ingestion pipeline after increasing
Kafka parallelism to two partitions and two active consumers.

### Configuration

- Documents: 500
- Producer concurrency: 4
- Kafka partitions: 2
- Listener concurrency: 2
- Consumer group: `smartsearch-workers`
- `max.poll.records`: 1
- Embedding model: `nomic-embed-text`
- Embedding dimensions: 768
- Workload: small one-chunk documents

### Results

- Producer rate: 99.652 docs/s
- Completion rate: 65.265 docs/s
- READY: 500/500
- FAILED: 0
- Average latency: 1409.811 ms
- p50: 1614.198 ms
- p95: 2485.172 ms
- p99: 2651.209 ms
- Max: 2720.922 ms

### Comparison

| Experiment | Partitions | Consumers | Producer Concurrency | Producer Rate | Completion Rate | p50 | p95 |
|---|---:|---:|---:|---:|---:|---:|---:|
| B5 | 1 | 2 | 2 | 58.610 docs/s | 35.672 docs/s | 3180 ms | 5350 ms |
| B6 | 2 | 2 | 2 | 57.496 docs/s | 58.089 docs/s | 55 ms | 370 ms |
| B7 | 2 | 2 | 4 | 99.652 docs/s | 65.265 docs/s | 1614 ms | 2485 ms |

### Observation

Increasing Kafka partitions from one to two allowed both configured consumers
to process records concurrently.

Under producer concurrency 2, the two-consumer pipeline completed approximately
58.1 docs/s while receiving approximately 57.5 docs/s, so little sustained
backlog accumulated.

Increasing producer concurrency to 4 raised the arrival rate to approximately
99.7 docs/s. Completion throughput increased only to approximately 65.3 docs/s.

The difference between arrival and service rate caused queued work to
accumulate. Median PENDING-to-READY latency increased from approximately
55 ms in B6 to 1.61 seconds in B7, while p95 increased from approximately
370 ms to 2.49 seconds.

All 500 documents still reached READY with zero failures.

### Conclusion

For this local one-chunk workload, increasing Kafka parallelism from one active
consumer to two increased observed ingestion capacity from roughly 33–37
docs/s to roughly 58–65 docs/s.

However, throughput did not scale indefinitely with producer concurrency.
Once the arrival rate reached approximately 100 docs/s, the two-consumer
pipeline saturated and queue waiting again became the dominant contributor
to end-to-end ingestion latency.

The results suggest that the original single-partition Kafka bottleneck has
been removed and that the next limiting resource is somewhere downstream in
the processing path.

The next step is to measure processing time across:

1. database claim/load
2. document chunking
3. Ollama embedding generation
4. pgvector/database writes
5. final status update

before increasing Kafka partition or consumer counts further.

## Experiment B8 — Worker Stage Profiling Under Saturation

### Goal

Identify the dominant processing stage after scaling Kafka ingestion to two
partitions and two active consumers.

### Configuration

- Documents: 500
- Producer concurrency: 4
- Kafka partitions: 2
- Active consumers: 2
- Embedding model: `nomic-embed-text`
- Embedding dimensions: 768
- Workload: small one-chunk documents
- Ollama model warm before benchmark

### Pipeline Results

- Producer rate: 87.252 docs/s
- Completion rate: 62.740 docs/s
- READY: 500/500
- FAILED: 0
- p50 end-to-end latency: 1719.418 ms
- p95 end-to-end latency: 2294.514 ms
- p99 end-to-end latency: 2351.083 ms

### Stage Timing Results

| Stage | Average | p50 | p95 | p99 |
|---|---:|---:|---:|---:|
| Lease claim | 0.30 ms | 0 ms | 1 ms | 1 ms |
| Payload load | 0.02 ms | 0 ms | 0 ms | 1 ms |
| Delete existing chunks | 0.02 ms | 0 ms | 0 ms | 1 ms |
| Chunking | ~0 ms | 0 ms | 0 ms | 0 ms |
| Embedding | 23.49 ms | 24 ms | 32 ms | 38 ms |
| Chunk/vector write | 0.90 ms | 1 ms | 2 ms | 3 ms |
| Mark READY | 0.31 ms | 0 ms | 1 ms | 1 ms |
| addDocument total | 25.32 ms | 26 ms | 34 ms | 43 ms |
| Worker work | 25.39 ms | 26 ms | 34 ms | 45 ms |

### Observation

Embedding generation dominated worker processing time.

Average embedding latency was 23.49 ms compared with approximately
0.90 ms for the chunk/vector database write. Embedding represented roughly
92% of measured worker work time for this workload.

Warm embedding latency under light load had previously been approximately
18 ms. Under the saturated two-consumer workload, average embedding latency
increased to 23.49 ms, with p95 reaching 32 ms.

Database operations remained comparatively inexpensive:
lease acquisition, payload loading, vector writes, and final status updates
were generally around 0–1 ms on average for this local workload.

### Conclusion

The earlier single-partition Kafka bottleneck was successfully removed by
increasing the topic to two partitions and allowing two consumers to process
records concurrently.

After that change, embedding generation became the dominant measured
processing cost.

The two-consumer pipeline sustained approximately 63–65 docs/sec in repeated
saturated tests. Increasing producer concurrency beyond this capacity created
Kafka queueing and amplified end-to-end latency rather than proportionally
increasing throughput.

The next scaling experiment should determine whether additional Kafka
partitions and consumers improve throughput further or whether contention
in the shared local embedding service causes diminishing returns.


## Experiment B9 — Four Partitions and Four Consumers

### Goal

Determine whether doubling Kafka-side parallelism from two consumers to four
continues to increase ingestion throughput after the original partition
bottleneck was removed.

### Configuration

- Documents: 500
- Producer concurrency: 4
- Kafka partitions: 4
- Listener concurrency: 4
- Consumer group: `smartsearch-workers`
- Embedding model: `nomic-embed-text`
- Embedding dimensions: 768
- Workload: small one-chunk documents

### Results

- Producer rate: 90.493 docs/s
- Completion rate: 67.569 docs/s
- READY: 500/500
- FAILED: 0
- Average latency: 1585.865 ms
- p50: 1694.256 ms
- p95: 2253.725 ms
- p99: 2366.335 ms
- Max: 2383.246 ms

### Observation

Doubling Kafka partitions and listener concurrency from two to four produced
only a small throughput improvement.

The two-consumer configuration repeatedly completed approximately 63–65
documents/sec, while the four-consumer configuration completed approximately
67.6 documents/sec.

Tail latency remained broadly similar.

### Conclusion

Kafka partition count was no longer the dominant throughput constraint.

The strong scaling improvement observed when moving from one active consumer
to two did not continue when moving from two consumers to four.

Combined with the B8 stage profiling result, where embedding accounted for
approximately 92% of measured worker processing time, this indicates that
the bottleneck has migrated downstream from Kafka consumption toward the
shared embedding-inference path.

Additional Kafka consumers alone are therefore unlikely to materially
increase ingestion capacity without scaling or changing the embedding tier.


## Experiment B10 — Embedding Contention Under Four Consumers

### Goal

Determine why increasing Kafka partitions and active consumers from two to
four produced only a small throughput improvement.

### Configuration

- Documents: 500
- Producer concurrency: 4
- Kafka partitions: 4
- Active consumers: 4
- Embedding model: `nomic-embed-text`
- Embedding dimensions: 768
- Workload: small one-chunk documents
- Ollama model warm before benchmark

### Stage Timing Results

| Stage | Average | p50 | p95 | p99 |
|---|---:|---:|---:|---:|
| Lease claim | 0.22 ms | 0 ms | 1 ms | 1 ms |
| Payload load | 0.02 ms | 0 ms | 0 ms | 1 ms |
| Delete existing chunks | 0.01 ms | 0 ms | 0 ms | 1 ms |
| Chunking | ~0 ms | 0 ms | 0 ms | 0 ms |
| Embedding | 46.29 ms | 52 ms | 57 ms | 62 ms |
| Chunk/vector write | 0.87 ms | 1 ms | 2 ms | 3 ms |
| Mark READY | 0.26 ms | 0 ms | 1 ms | 1 ms |
| addDocument total | 48.06 ms | 54 ms | 59 ms | 64 ms |
| Worker work | 48.11 ms | 54 ms | 59 ms | 64 ms |

### Comparison With Two Consumers

| Metric | 2 Consumers | 4 Consumers |
|---|---:|---:|
| Embedding avg | 23.49 ms | 46.29 ms |
| Embedding p50 | 24 ms | 52 ms |
| Embedding p95 | 32 ms | 57 ms |
| Embedding p99 | 38 ms | 62 ms |
| Chunk DB write avg | 0.90 ms | 0.87 ms |
| Worker avg | 25.39 ms | 48.11 ms |
| Observed completion rate | ~63–65 docs/s | ~67 docs/s |

### Observation

Doubling consumer parallelism from two to four nearly doubled average
embedding latency, from 23.49 ms to 46.29 ms.

Average worker processing time increased correspondingly from 25.39 ms to
48.11 ms.

In contrast, PostgreSQL chunk-write latency remained essentially unchanged
at approximately 0.9 ms.

This explains why doubling Kafka-side parallelism produced only a small
throughput improvement: additional consumers generated more concurrent work
against the same shared embedding tier, increasing the latency of the
dominant processing stage.

### Conclusion

The ingestion bottleneck migrated as the system was scaled.

Initially, a single Kafka partition limited consumer parallelism. Increasing
the topic to two partitions allowed two consumers to operate concurrently and
substantially increased throughput.

Increasing the system again to four partitions and four consumers did not
produce comparable scaling. Stage profiling showed that embedding latency
nearly doubled under the additional concurrency while database latency
remained stable.

For this local deployment and workload, the shared embedding service is now
the dominant scaling constraint.

Further ingestion scaling should therefore focus on the embedding tier rather
than adding Kafka consumers alone.

## Experiment B11 — Increasing Ollama Request Parallelism

### Goal

Determine whether increasing Ollama request parallelism improves ingestion
capacity after embedding generation became the dominant worker-stage cost.

### Configuration

- Documents: 500
- Producer concurrency: 4
- Kafka partitions: 4
- Active consumers: 4
- `OLLAMA_NUM_PARALLEL=2`
- Embedding model: `nomic-embed-text`
- Embedding dimensions: 768
- Workload: small one-chunk documents
- Model warm before benchmark

### Pipeline Results

- Producer rate: 85.133 docs/s
- Completion rate: 69.272 docs/s
- READY: 500/500
- FAILED: 0
- Average end-to-end latency: 858.894 ms
- p50: 818.510 ms
- p95: 1710.501 ms
- p99: 1774.493 ms
- Max: 1830.753 ms

### Stage Results

| Stage | Average | p50 | p95 | p99 |
|---|---:|---:|---:|---:|
| Embedding | 46.24 ms | 52 ms | 60 ms | 73 ms |
| Chunk/vector write | 1.10 ms | 1 ms | 2 ms | 6 ms |
| addDocument total | 48.28 ms | 54 ms | 63 ms | 81 ms |
| Worker work | 48.34 ms | 54 ms | 63 ms | 81 ms |

### Comparison With B10

| Metric | Parallel=1 | Parallel=2 |
|---|---:|---:|
| Completion rate | 67.569 docs/s | 69.272 docs/s |
| Embedding avg | 46.29 ms | 46.24 ms |
| Embedding p50 | 52 ms | 52 ms |
| Embedding p95 | 57 ms | 60 ms |
| Worker avg | 48.11 ms | 48.34 ms |
| E2E p50 | 1694 ms | 819 ms |
| E2E p95 | 2254 ms | 1711 ms |

### Observation

Increasing Ollama request parallelism from one to two did not materially
reduce embedding service time.

Average embedding latency remained approximately 46 ms and average worker
processing time remained approximately 48 ms.

Observed completion throughput increased only modestly, from approximately
67.6 to 69.3 documents/sec.

End-to-end queueing latency was lower in this run, but the producer arrival
rate was also lower and only one trial was performed. Therefore the latency
improvement cannot yet be attributed solely to the Ollama parallelism change.

### Conclusion

Increasing request parallelism within the same local embedding service did
not materially improve the dominant embedding-stage latency or ingestion
throughput.

The results reinforce the conclusion that the embedding tier is the current
capacity constraint for this workload.

Further scaling would likely require changing the embedding architecture
rather than simply increasing Kafka consumer count or local request
parallelism.