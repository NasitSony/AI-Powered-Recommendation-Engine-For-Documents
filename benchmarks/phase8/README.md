## Phase 8 — Redis Caching & Low-Latency Search Serving

Phase 8 introduces Redis as an optional acceleration layer for semantic search.

The design goal is:

> **Redis is an optimization, not a dependency.**

When Redis is healthy, repeated searches can be served directly from cache. When Redis is unavailable, SmartSearch bypasses the cache and continues serving requests through the normal embedding + pgvector path.

### Serving Path

```text
Search request
      ↓
tenant-aware cache key
      ↓
    Redis
   /     \
 HIT     MISS
  |        |
  |        ↓
  |   embedding model
  |        ↓
  |   pgvector search
  |        ↓
  |   populate cache
  |        |
  └────────┴──→ response
```

Cache keys include the tenant, query hash, and requested result count:

```text
search:<tenantId>:<queryHash>:<k>
```

This prevents cached search results from being shared across tenants.

---

### Cache TTL

Cached search results use a TTL so entries eventually expire without requiring explicit cleanup.

Observed behavior was verified as:

```text
first request       → MISS
repeated request    → HIT
TTL expires
next request        → MISS
```

This provides bounded staleness even without an explicit invalidation event.

---

### Cache Invalidation After Ingestion

TTL alone is insufficient because newly ingested documents may change semantic search results.

After a document successfully reaches `READY`, SmartSearch invalidates cached search results belonging to that tenant.

Observed sequence:

```text
search
  ↓
MISS
  ↓
cache populated
  ↓
HIT
  ↓
new document ingested
  ↓
document reaches READY
  ↓
tenant cache invalidated
  ↓
next search
  ↓
MISS → fresh vector search
```

Instrumentation confirms invalidation:

```text
metric=search_cache_invalidate tenantId=tenant-A
```

The subsequent search included the newly ingested document.

---

### Redis Failure Behavior

Redis is deliberately treated as a performance optimization rather than a correctness or availability dependency.

If a Redis operation fails:

```text
Redis unavailable
       ↓
cache access fails
       ↓
fallback to normal search
       ↓
embedding + pgvector
       ↓
response still returned
```

Initial failure testing confirmed that search remained available while Redis was stopped.

A Redis connection failure initially added approximately 200 ms of latency because the request had to wait for the Redis connection attempt before falling back.

This motivated adding a cache circuit breaker.

---

### Redis Circuit Breaker

Repeatedly waiting for a known-unavailable cache would make an optional optimization harm serving latency.

SmartSearch therefore opens a short cache circuit after Redis failures.

```text
Redis healthy
     ↓
   CLOSED
     ↓
Redis failure
     ↓
    OPEN
     ↓
subsequent requests
bypass Redis
     ↓
embedding + pgvector
     ↓
cooldown expires
     ↓
probe Redis again
     ↓
Redis healthy
     ↓
MISS → populate → HIT
```

During the outage test, the first Redis failures were observed explicitly:

```text
Redis cache read failed ... error=Unable to connect to Redis
```

Subsequent requests avoided Redis:

```text
metric=search_cache_bypass ... reason=circuit_open
```

Observed bypass request latency was approximately:

```text
23–39 ms
```

rather than repeatedly paying the Redis connection-failure penalty.

After Redis was restored and the cooldown expired, the cache automatically recovered:

```text
MISS
 ↓
cache populated
 ↓
HIT
```

A recovery test observed approximately:

```text
recovery MISS ≈ 72.8 ms
next HIT      ≈ 9.2 ms
```

No application restart was required for cache recovery.

---

## Controlled Cache Benchmark

A controlled workload was used to quantify cache behavior.

### Methodology

The benchmark used:

```text
10 distinct semantic queries
5 requests per query
50 total requests
```

Tenant A cache entries were cleared before the experiment.

Each query therefore followed:

```text
request 1 → MISS
requests 2–5 → HIT
```

The workload included queries such as:

```text
Mercury planet
Venus planet
Mars planet
Jupiter planet
Saturn planet
distributed systems
database replication
cache invalidation
semantic search
failover latency
```

The expected cache distribution was therefore:

```text
10 MISS
40 HIT
```

Application instrumentation confirmed exactly:

```text
HITS=40
MISSES=10
TOTAL=50
```

### Cache Hit Ratio

```text
hit ratio = 40 / 50 = 80%
```

The measured workload therefore achieved an **80% cache hit ratio**.

### Latency Results

| Metric | Cache MISS | Cache HIT |
|---|---:|---:|
| Requests | 10 | 40 |
| Mean | 30.20 ms | 2.47 ms |
| p50 | 20.53 ms | 2.29 ms |
| p95 | 71.95 ms | 3.03 ms |
| Maximum | 95.99 ms | 10.31 ms |

Overall across all 50 requests:

```text
mean ≈ 8.02 ms
p50  ≈ 2.38 ms
p95  ≈ 21.19 ms
```

The mean latency comparison was:

```text
MISS mean = 30.20 ms
HIT mean  =  2.47 ms

30.20 / 2.47 ≈ 12.2×
```

Under this controlled workload, cached searches therefore had approximately **12.2× lower mean latency** than cache misses.

The hit path also achieved a measured **p95 latency of approximately 3.03 ms**.

### Observed Outliers

The benchmark intentionally retains observed latency variation rather than removing outliers.

Notable observations included:

```text
highest MISS = 95.99 ms
highest HIT  = 10.31 ms
```

These values remain part of the reported latency distributions.

---

## Phase 8 Results

Phase 8 demonstrates:

- Redis-backed semantic-search caching
- tenant-aware cache keys
- TTL-based expiration
- cache invalidation after successful ingestion
- graceful fallback when Redis is unavailable
- circuit breaking to avoid repeated Redis failure penalties
- automatic cache recovery after Redis returns
- cache hit/miss instrumentation
- controlled mixed-workload benchmarking

The resulting serving architecture is:

```text
                    ┌───────────────┐
Search request ────►│ Tenant-aware  │
                    │ cache key     │
                    └───────┬───────┘
                            ↓
                         Redis
                       /       \
                    HIT         MISS
                     |            |
                     |            ↓
                     |       Embedding
                     |            ↓
                     |        pgvector
                     |            ↓
                     |       populate cache
                     |            |
                     └────────────┴────► response

Redis unavailable
       ↓
circuit breaker
       ↓
cache bypass
       ↓
embedding + pgvector
       ↓
response remains available
```

The key production property is that the cache improves latency without becoming part of the critical availability path:

> **Redis failure degrades SmartSearch performance, but does not take semantic search down.**