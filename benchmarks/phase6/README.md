## Phase 6 — Sharding Hotspot Characterization

### H1 — Tenant Placement Balance

Shard function:

`floorMod(tenantId.hashCode(), 2)`

For 100 synthetic tenants:

- shard-0: 51 tenants
- shard-1: 49 tenants

Result: tenant identities were distributed approximately evenly.

### H2 — Hot-Tenant Simulation

Workload model:

- tenant-1: 80% of requests
- remaining 99 tenants: 20% combined

Observed modeled shard load:

- shard-0: 90.10%
- shard-1: 9.90%

Although tenant placement was approximately 51/49, workload distribution became highly skewed because the hot tenant mapped to shard-0.

### H3 — Real SmartSearch Hotspot Test

Real ingestion workload:

- tenant-1 → shard-0 → 20 documents
- tenant-2 → shard-1 → 2 documents

Observed physical placement:

- shard-0: 20 hotspot documents
- shard-1: 2 hotspot documents

This reproduced a 10:1 workload skew in the real application.

### Conclusion

Tenant-based hashing provides deterministic routing and good tenant-count distribution, but it does not guarantee workload balance.

A single high-volume tenant can create a hot shard even when the number of tenants per shard is nearly balanced.

Tradeoffs of tenant-level sharding:

- strong tenant locality
- no scatter/gather for tenant-local search
- deterministic routing
- simple ownership model

But:

- hot tenants create hot shards
- tenant count is not a proxy for resource demand
- a single tenant cannot be split across shards under the current scheme
- rebalancing requires tenant migration or a more adaptive placement strategy