# Phase 9 — Search Scaling & Vector Indexing

## Goal

Evaluate how SmartSearch vector retrieval behaves as the dataset grows, determine when approximate nearest-neighbor indexing becomes useful, measure the recall/latency tradeoff of HNSW, and study how ANN interacts with tenant filtering.

SmartSearch uses PostgreSQL + pgvector with 768-dimensional embeddings and L2 distance:

```sql
WHERE tenant_id = ?
ORDER BY embedding <-> ?
LIMIT ?
```

The experiments isolate database vector-search behavior from Redis caching, HTTP handling, and Ollama embedding latency unless otherwise noted.

---

## 1. Exact Search Scaling

A deterministic synthetic dataset of 768-dimensional vectors was generated and loaded into `document_chunks`.

Dataset sizes:

- 10,000 vectors
- 50,000 vectors
- 100,000 vectors

For each dataset size:

- fixed query vector
- exact L2 top-10 search
- 5 warm-up queries
- 20 measured queries
- PostgreSQL `EXPLAIN (ANALYZE, FORMAT JSON)`

### Results

| Dataset | Mean | Median | p95 |
|---|---:|---:|---:|
| 10K | 22.79 ms | 20.95 ms | 28.39 ms |
| 50K | 129.58 ms | 128.65 ms | 132.63 ms |
| 100K | 343.17 ms | 349.02 ms | 376.28 ms |

Without ANN indexing, PostgreSQL used sequential scanning plus top-N sorting and computed vector distance across candidate rows.

From 10K to 100K vectors, the dataset grew 10x while median exact-search latency increased from approximately 20.95 ms to 349.02 ms.

---

## 2. HNSW Index

An HNSW index was created using:

```sql
CREATE INDEX idx_document_chunks_embedding_hnsw
ON document_chunks
USING hnsw (embedding vector_l2_ops)
WITH (
    m = 16,
    ef_construction = 64
);
```

The production schema now includes the same index through Flyway migration V16.

### Build characteristics

At 100K vectors:

- build time: approximately 3m56s
- HNSW index size: approximately 391 MB
- relation total size before HNSW: approximately 430 MB
- relation total size after HNSW: approximately 821 MB

During construction PostgreSQL reported that the HNSW graph exceeded the configured 64 MB `maintenance_work_mem` after approximately 16.7K tuples, causing the remaining build to take longer.

This demonstrates that ANN indexing introduces meaningful storage and index-build costs in addition to query-performance benefits.

---

## 3. HNSW Search Performance

Using the same 100K synthetic-vector dataset and 20 measured queries after 5 warm-ups:

| Metric | Exact | HNSW |
|---|---:|---:|
| Mean | 343.17 ms | 1.62 ms |
| Median | 349.02 ms | 1.52 ms |
| p95 | 376.28 ms | 3.01 ms |

The isolated database benchmark showed approximately:

- 212x mean speedup
- 230x median speedup
- 125x p95 speedup

These measurements represent database vector-search execution under the benchmark methodology and are not end-to-end SmartSearch API latency.

---

## 4. Recall vs Search Cost

A separate semantic validation corpus was generated using the local `nomic-embed-text` model.

Corpus:

- 10,000 text records
- 768-dimensional embeddings
- 10 semantic query embeddings
- exact top-10 used as ground truth
- HNSW evaluated using Recall@10

Exact ground truth explicitly disabled ANN index scans.

### Results

| ef_search | Mean Recall@10 | Mean execution | Median execution | p95 execution |
|---:|---:|---:|---:|---:|
| 10 | 95% | 0.670 ms | 0.603 ms | 0.866 ms |
| 20 | 99% | 0.806 ms | 0.771 ms | 1.098 ms |
| 40 | 100% | 1.201 ms | 1.285 ms | 1.577 ms |
| 80 | 100% | 1.200 ms | 1.159 ms | 1.493 ms |
| 160 | 100% | 2.036 ms | 1.986 ms | 2.383 ms |

Increasing `ef_search` improved recall while generally increasing search work.

For this validation workload, `ef_search=20` provided 99% mean Recall@10 with approximately 0.77 ms median PostgreSQL execution time.

`ef_search=40` matched the exact top-10 results for all 10 tested queries, with approximately 1.29 ms median execution time.

The small difference between some adjacent latency measurements should not be interpreted as a monotonic performance relationship because the query set contains only 10 queries.

---

## 5. Synthetic Recall Stress Test

Recall was also tested against 100K uniformly random 768-dimensional vectors.

Results:

- `ef_search=40`: mean Recall@10 = 5%
- `ef_search=400`: mean Recall@10 = 17%

This synthetic high-dimensional random-vector workload behaved very differently from the semantic embedding workload.

The result is retained as a stress test rather than as an estimate of semantic retrieval quality. It demonstrates that ANN recall depends strongly on vector distribution and workload characteristics.

---

## 6. Multi-Tenant ANN Filtering

SmartSearch searches within a tenant:

```sql
WHERE tenant_id = ?
ORDER BY embedding <-> ?
LIMIT 10;
```

The HNSW index itself is global across the shard.

To study post-filtering behavior, the benchmark table was expanded with additional tenants and a selective target tenant containing 1,000 vectors.

Approximate distribution:

- primary benchmark tenant: 100,000
- noise tenant A: 25,000
- noise tenant B: 25,000
- noise tenant C: 25,000
- selective target tenant: 1,000

The selective tenant represented approximately 0.57% of the table.

Noise rows intentionally reused vectors from the main benchmark corpus, creating a difficult overlapping multi-tenant ANN workload.

### Forced global HNSW

With:

- `ef_search=40`
- iterative scanning disabled
- global HNSW forced for the benchmark

the query returned:

- requested: 10
- returned: 0
- rows removed by tenant filter: 52
- execution time: 10.781 ms

The ANN candidate set was exhausted before tenant-valid results were found.

### HNSW iterative scan

With:

```sql
SET LOCAL hnsw.iterative_scan = strict_order;
```

the same query returned:

- requested: 10
- returned: 10
- rows removed by tenant filter: 1,406
- execution time: 222.939 ms

Iterative scanning restored the requested result count, but required substantially more search work.

---

## 7. PostgreSQL Query Planning by Tenant Selectivity

When PostgreSQL was allowed to choose the execution plan naturally, it did not always choose HNSW.

### Large tenant

For the 100K-vector tenant PostgreSQL selected:

```text
global HNSW index
    ↓
tenant filter
    ↓
top-10
```

The plan used:

```text
idx_document_chunks_embedding_hnsw
```

### Selective tenant

For the 1K-vector tenant PostgreSQL instead selected:

```text
tenant B-tree
    ↓
1,000 tenant rows
    ↓
exact L2 distance
    ↓
top-N sort
```

The plan used:

```text
idx_document_chunks_tenant_doc
```

Observed execution for this particular plan run was approximately 19.4 ms.

This provides an important design result: ANN is not automatically the cheapest retrieval strategy simply because an HNSW index exists.

For a sufficiently selective tenant, exact vector search over the tenant-local candidate set can be preferable to traversing a global ANN index.

---

## 8. Production Design Decision

SmartSearch retains both:

- tenant-aware B-tree indexing
- global HNSW vector indexing

The application continues to issue one query shape:

```sql
SELECT ...
FROM document_chunks
WHERE tenant_id = ?
ORDER BY embedding <-> ?
LIMIT ?;
```

No application-level threshold such as:

```text
if tenant_size < X → exact
else → HNSW
```

is currently introduced.

The experiments do not establish a robust threshold `X`, and PostgreSQL already demonstrated that its optimizer can choose different physical plans based on tenant selectivity and table statistics.

Therefore the current design is:

```text
semantic query
      ↓
tenant-filtered vector SQL
      ↓
PostgreSQL optimizer
   ↙              ↘
selective       large
tenant          tenant
  ↓               ↓
B-tree +        HNSW
exact search      ANN
```

Iterative HNSW scanning remains an available mechanism for filtered ANN workloads but is not enabled globally because the selective-tenant experiment demonstrated potentially substantial additional search cost.

---

## 9. Key Findings

1. Exact vector search degraded substantially as the corpus increased from 10K to 100K vectors.

2. HNSW reduced median isolated database search latency from approximately 349 ms to 1.52 ms at 100K vectors under the synthetic scaling benchmark.

3. ANN indexing has non-trivial costs: the measured HNSW index consumed approximately 391 MB and roughly doubled total relation storage in the 100K experiment.

4. ANN quality is workload-dependent. Random high-dimensional vectors produced poor HNSW recall, while the semantic `nomic-embed-text` validation corpus achieved 95–100% Recall@10 across the tested `ef_search` range.

5. Increasing `ef_search` improved semantic recall while generally increasing execution cost.

6. Global ANN indexes can interact poorly with highly selective tenant filters because candidate filtering occurs during/after ANN retrieval.

7. Iterative HNSW scanning can recover filtered results, but may require substantially more work.

8. PostgreSQL can choose tenant-indexed exact search for selective tenants and HNSW for larger tenants using the same application SQL.

The broader systems lesson is that vector indexing, tenant selectivity, query planning, storage cost, and retrieval quality must be designed and evaluated together.

---

## Limitations

These results are local benchmark measurements and should not be generalized directly to production-scale deployments.

Important limitations include:

- benchmarks were run on a local development machine
- synthetic scaling vectors were uniformly generated rather than natural embedding distributions
- semantic validation text was deterministically generated and is not a natural production corpus
- semantic recall used 10 queries
- the 100K HNSW latency experiment used 20 measured queries
- p95/p99 estimates would benefit from larger sample counts
- `EXPLAIN ANALYZE` introduces measurement overhead
- direct database benchmarks exclude Redis, Ollama, HTTP, and application-layer latency
- the selective-tenant experiment deliberately created highly overlapping vector distributions as a stress case
- HNSW build behavior depends on hardware, pgvector configuration, PostgreSQL memory settings, dataset distribution, and index parameters

Future production-scale evaluation should use a larger natural semantic corpus, more query samples, realistic tenant distributions, and end-to-end load testing.