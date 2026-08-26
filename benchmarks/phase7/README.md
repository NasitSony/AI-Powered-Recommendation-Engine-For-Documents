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


# Phase 7 — PostgreSQL Replication and Failover

## Goal

Evaluate SmartSearch behavior when a PostgreSQL shard primary becomes unavailable and its replica is promoted to primary.

This phase focuses on:

- PostgreSQL streaming replication
- Primary failure
- Replica promotion
- Application routing to the writable node
- Request success during failover
- End-to-end ingestion latency during the failover transition
- Rejoining the old primary as a replica

---

## Architecture

SmartSearch uses two PostgreSQL shards.

Each shard can have a primary and replica:

```text
                     SmartSearch
                         |
                    Shard Router
                    /          \
                   /            \
                  v              v
              Shard 0          Shard 1
              Primary          Primary
               :5434            :5435
                 |                |
                 | WAL            | WAL
                 v                v
              Replica          Replica
               :5437            :5438
```

Replication uses PostgreSQL streaming replication.

The experiments in this phase primarily exercised **Shard 1**.

---

## Healthy Shard-1 Topology

Before failover:

```text
Shard 1

Primary :5435
    |
    | asynchronous WAL streaming
    v
Replica :5438
```

The replica state was verified with:

```sql
SELECT pg_is_in_recovery();
```

Result:

```text
t
```

This confirms that `:5438` was operating as a standby.

The primary replication state was verified with:

```sql
SELECT client_addr, state, sync_state
FROM pg_stat_replication;
```

Observed result:

```text
state      = streaming
sync_state = async
```

Therefore the healthy topology was:

```text
:5435 PRIMARY
   |
   | streaming / async
   v
:5438 REPLICA
```

---

# Replication Validation

Replication was tested by writing documents to each primary and reading them from its corresponding replica.

For Shard 1, a test document written to the primary appeared on the replica:

```text
id                  tenant_id   status
------------------------------------------------
phase7-repl-test-1  tenant-B    READY
```

The primary also reported its standby as:

```text
streaming | async
```

This confirmed that PostgreSQL streaming replication was functioning.

---

# Primary Failure Experiment

The Shard-1 primary was intentionally stopped:

```bash
docker stop smartsearch-postgres-shard-1
```

The original primary on:

```text
localhost:5435
```

became unavailable.

Meanwhile, the replica on:

```text
localhost:5438
```

remained accessible.

Before promotion:

```sql
SELECT pg_is_in_recovery();
```

returned:

```text
t
```

The replica still contained data previously written to the primary.

This demonstrated an important distinction:

```text
Replication
    !=
Automatic failover
```

The data survived on the replica, but the replica was still read-only.

---

# Replica Promotion

The surviving replica was manually promoted:

```bash
docker exec smartsearch-postgres-replica-1 \
  pg_ctl -D /var/lib/postgresql/data promote
```

PostgreSQL reported:

```text
server promoted
```

After promotion:

```sql
SELECT pg_is_in_recovery();
```

returned:

```text
f
```

Therefore:

```text
Before failure:

:5435 PRIMARY
   |
   v
:5438 REPLICA


After failure + promotion:

:5435 DOWN

:5438 PRIMARY
```

A direct write to the promoted node succeeded:

```text
phase7-after-promotion | tenant-B | READY
```

This confirmed that the former standby had become writable.

---

# Application-Level Routing

Replication and promotion alone are not sufficient for application availability.

Initially the application topology effectively looked like:

```text
tenant-B
   |
Shard Router
   |
:5435
```

If `:5435` failed, PostgreSQL could have a healthy promoted node on `:5438`, but the application still needed to discover which database node was writable.

The routing layer was therefore extended so that SmartSearch could select the writable node for each shard rather than relying solely on a permanently fixed primary endpoint.

Conceptually:

```text
tenant
  |
  v
ShardRouter
  |
  v
ShardJdbcRouter
  |
  +---- Node A
  |
  +---- Node B
          |
          v
    determine writable node
```

The PostgreSQL property:

```sql
SELECT pg_is_in_recovery();
```

provides a simple distinction:

```text
false -> writable primary
true  -> standby
```

This allowed the application to identify the currently writable database node.

---

# Application Failover Validation

After the Shard-1 topology changed, a Tenant-B request was submitted through the normal SmartSearch API:

```bash
curl -X POST http://localhost:8080/api/documents \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId":"tenant-B",
    "requestId":"phase7-auto-failover-B-001",
    "text":"Automatic routing after shard-1 primary failover"
  }'
```

The API accepted the request:

```json
{
  "status": "PENDING"
}
```

The document subsequently reached:

```text
READY
```

on the writable Shard-1 node.

This demonstrated application-level routing across the changed database topology.

---

# Healthy Baseline Benchmark

A controlled baseline of 20 Tenant-B ingestion requests was executed while the database topology was healthy.

Observed end-to-end ingestion latency:

| Metric | Healthy |
|---|---:|
| Requests | 20 |
| Mean | ~49.9 ms |
| p50 | 46 ms |
| p95 | ~71.6 ms |
| Min | 37 ms |
| Max | 82 ms |
| Successful | 20/20 |

Representative measured latencies included:

```text
62
41
39
37
57
37
46
41
51
60
71
50
82
44
46
38
55
38
61
42
```

All 20 requests reached `READY`.

---

# Controlled Failover Benchmark

A second 20-request experiment was performed around the Shard-1 failover scenario.

The primary was stopped:

```bash
docker stop smartsearch-postgres-shard-1
```

The replica was promoted:

```bash
docker exec smartsearch-postgres-replica-1 \
  pg_ctl -D /var/lib/postgresql/data promote
```

The application was not restarted and its configuration was not manually edited during the request test.

20 Tenant-B requests were then submitted.

Measured end-to-end latencies were:

```text
585
609
514
537
606
557
539
563
591
605
483
609
502
534
524
548
613
494
529
552
```

All 20 requests reached:

```text
READY
```

---

## Failover Results

| Metric | Failover |
|---|---:|
| Requests | 20 |
| Mean | ~554.7 ms |
| p50 | ~550 ms |
| p95 | ~609 ms |
| Min | 483 ms |
| Max | 613 ms |
| Successful | 20/20 |

---

# Healthy vs Failover

| Metric | Healthy | Failover |
|---|---:|---:|
| Requests | 20 | 20 |
| Mean latency | ~49.9 ms | ~554.7 ms |
| p50 | 46 ms | ~550 ms |
| p95 | ~71.6 ms | ~609 ms |
| Min | 37 ms | 483 ms |
| Max | 82 ms | 613 ms |
| Successful | 20/20 | 20/20 |

The observed mean-latency ratio was approximately:

```text
554.7 / 49.9 ~= 11.1x
```

Therefore the failover experiment produced a substantial temporary latency increase.

However:

```text
20 requests submitted
        |
        v
20 requests completed
        |
        v
20/20 READY
```

No request loss was observed in this 20-request experiment.

---

# Observed Availability/Latency Tradeoff

The experiment showed a useful distinction between availability and latency.

The system did not maintain normal latency during the failover scenario:

```text
Healthy p50       ~46 ms
Failover p50     ~550 ms

Healthy p95       ~72 ms
Failover p95     ~609 ms
```

However, requests continued to complete successfully.

The tested behavior can therefore be summarized as:

```text
Primary failure
      |
      v
Replica promotion
      |
      v
Application identifies writable node
      |
      v
Requests continue processing
      |
      v
Temporary latency increase
      |
      v
20/20 READY
```

For this experiment, failover caused **bounded disruption rather than observed request loss**.

---

# Rejoining the Old Primary

After promotion, the previous primary needed to return as a standby rather than independently becoming writable again.

The old primary was rebuilt from the new primary using `pg_basebackup`.

The resulting topology became:

```text
New Primary
:5438
   |
   | streaming WAL
   v
Rejoined Standby
:5435
```

The rejoined node reported:

```sql
SELECT pg_is_in_recovery();
```

Result:

```text
t
```

A new document was written to the promoted primary:

```text
phase7-rejoin-proof
```

The document subsequently appeared on the rejoined standby:

```text
phase7-rejoin-proof | tenant-B | READY
```

The new primary reported:

```sql
SELECT client_addr, state, sync_state
FROM pg_stat_replication;
```

with:

```text
streaming | async
```

This confirmed that replication had been restored in the reverse direction.

---

# Complete Tested Lifecycle

Phase 7 exercised the following lifecycle:

```text
       NORMAL OPERATION

     Primary (:5435)
           |
           | WAL
           v
     Replica (:5438)

           |
           | primary failure
           v

        FAILURE

       :5435 DOWN
       :5438 standby

           |
           | promotion
           v

        FAILOVER

       :5438 PRIMARY

           |
           | application routing
           v

     requests continue

           |
           | old node rebuilt
           v

         REJOIN

       :5438 PRIMARY
           |
           | WAL
           v
       :5435 REPLICA
```

The experiments therefore covered more than simply copying data between two PostgreSQL instances.

They exercised:

```text
replication
    +
failure
    +
promotion
    +
application routing
    +
continued writes
    +
rejoin
    +
replication restoration
```

---

# Key Findings

### 1. Replication is not failover

Streaming replication preserved another copy of the shard data, but the standby remained read-only until promoted.

### 2. Promotion is not application failover

Even after a replica becomes writable, the application needs a mechanism to locate the writable node.

### 3. Failover has a latency cost

Healthy requests had approximately:

```text
p50 = 46 ms
p95 = 72 ms
```

while the controlled failover experiment observed approximately:

```text
p50 = 550 ms
p95 = 609 ms
```

### 4. Availability and latency are different properties

All 20 failover-test requests succeeded, despite the substantial temporary latency increase.

### 5. Rejoining requires topology repair

The old primary cannot simply return as another primary. It must rejoin from the current primary to restore a safe primary/standby topology.

---

# Limitations

This phase is a controlled local Docker experiment rather than a production PostgreSQL HA deployment.

Important limitations include:

- Replica promotion is externally initiated.
- PostgreSQL replication is asynchronous.
- The experiment does not establish zero data loss.
- Split-brain prevention is not provided by a production HA coordinator.
- Failure detection and leader election are not implemented as a distributed consensus system.
- Results were measured on a local development machine.
- The failover benchmark contains 20 requests.
- The measured results should therefore be interpreted as experimental evidence for this configuration rather than general production performance guarantees.

A production deployment would typically use dedicated HA infrastructure for:

```text
health checking
leader election
promotion
fencing
split-brain prevention
connection routing
topology management
```

---

# Phase 7 Result

SmartSearch moved from:

```text
sharded PostgreSQL
```

toward:

```text
sharded
   +
replicated
   +
failover-aware
PostgreSQL storage
```

The most important measured result from this phase was:

```text
Healthy
-------
p50 ~46 ms
p95 ~72 ms

Failover experiment
-------------------
p50 ~550 ms
p95 ~609 ms

Success
-------
20/20 requests READY
```

The experiment demonstrates the engineering tradeoff clearly:

> Replication preserves another copy of the data, but availability requires promotion and routing. During the tested failover scenario, SmartSearch maintained successful request processing at the cost of a substantial temporary latency increase.

---

# Takeaway

High availability is not a single database feature.

It is a system-level property involving:

```text
replication
     +
failure detection
     +
promotion
     +
routing
     +
recovery
     +
rejoin
     +
validation
```

Phase 7 provides an experimental foundation for reasoning about those mechanisms inside SmartSearch.