## Phase 7 — Healthy Steady-State Baseline

Topology:

- shard-1 primary: localhost:5435
- shard-1 replica: localhost:5438
- SmartSearch uses writable-primary discovery

Workload:

- tenant-B
- 20 ingestion requests
- all requests reached READY

Results:

- n = 20
- mean = 49.9 ms
- p50 = 46 ms
- p95 ≈ 71.6 ms
- min = 37 ms
- max = 82 ms
- success = 20/20

Observed e2e latencies (ms):

62, 41, 39, 37, 57,
37, 46, 41, 51, 60,
71, 50, 82, 44, 46,
38, 55, 38, 61, 42

Interpretation:

The healthy steady-state ingestion path is stable at roughly 50 ms mean end-to-end latency under this small local workload.

This baseline will be compared against controlled primary-failure and failover experiments.