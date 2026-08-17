#!/usr/bin/env bash
set -euo pipefail

API="http://localhost:8080"
COUNT=100
RUN_ID=$(date +%Y%m%d-%H%M%S)
OUT="benchmarks/baseline/ingestion-${RUN_ID}.csv"


echo "request_id,doc_id,created_at,ready_at,latency_ms,status,retry_count" > "$OUT"

START_ALL=$(python3 - <<'PY'
import time
print(time.time())
PY
)

for i in $(seq 1 "$COUNT"); do
  REQ_ID="baseline-${RUN_ID}-$(printf "%03d" "$i")"

  TEXT="Document $i describes a distributed system. It discusses replication, leader election, fault tolerance, message delivery, storage durability, retries, and recovery from partial failures."

  RESP=$(curl -s -X POST "$API/api/documents" \
    -H "Content-Type: application/json" \
    -d "{
      \"requestId\": \"$REQ_ID\",
      \"id\": \"doc-$REQ_ID\",
      \"text\": \"$TEXT\"
    }")

  DOC_ID=$(python3 -c 'import sys,json; print(json.load(sys.stdin)["docId"])' <<< "$RESP")

  while true; do
    STATUS_RESP=$(curl -s "$API/api/documents/$DOC_ID")

    STATUS=$(python3 -c 'import sys,json; print(json.load(sys.stdin)["status"])' <<< "$STATUS_RESP")

    if [[ "$STATUS" == "READY" || "$STATUS" == "FAILED" ]]; then
      CREATED=$(python3 -c 'import sys,json; print(json.load(sys.stdin)["createdAt"])' <<< "$STATUS_RESP")
      UPDATED=$(python3 -c 'import sys,json; print(json.load(sys.stdin)["updatedAt"])' <<< "$STATUS_RESP")
      RETRIES=$(python3 -c 'import sys,json; print(json.load(sys.stdin)["retryCount"])' <<< "$STATUS_RESP")

      LATENCY_MS=$(python3 - "$CREATED" "$UPDATED" <<'PY'
import sys
from datetime import datetime

a = datetime.fromisoformat(sys.argv[1].replace("Z", "+00:00"))
b = datetime.fromisoformat(sys.argv[2].replace("Z", "+00:00"))

print(round((b-a).total_seconds()*1000, 3))
PY
)

      echo "$REQ_ID,$DOC_ID,$CREATED,$UPDATED,$LATENCY_MS,$STATUS,$RETRIES" >> "$OUT"
      echo "$i/$COUNT  $STATUS  ${LATENCY_MS} ms"
      break
    fi

    sleep 0.05
  done
done

END_ALL=$(python3 - <<'PY'
import time
print(time.time())
PY
)

python3 - "$START_ALL" "$END_ALL" "$OUT" <<'PY'
import sys, csv, statistics

start = float(sys.argv[1])
end = float(sys.argv[2])
path = sys.argv[3]

with open(path) as f:
    rows = list(csv.DictReader(f))

lat = sorted(float(r["latency_ms"]) for r in rows)
success = sum(r["status"] == "READY" for r in rows)
failed = len(rows) - success

def pct(values, p):
    if not values:
        return 0
    idx = round((len(values)-1) * p)
    return values[idx]

elapsed = end-start

print()
print("=== SmartSearch Baseline Ingestion ===")
print(f"Documents:       {len(rows)}")
print(f"READY:           {success}")
print(f"FAILED:          {failed}")
print(f"Total time:      {elapsed:.3f} s")
print(f"Throughput:      {len(rows)/elapsed:.3f} docs/s")
print(f"Average latency: {statistics.mean(lat):.3f} ms")
print(f"p50 latency:     {pct(lat, .50):.3f} ms")
print(f"p95 latency:     {pct(lat, .95):.3f} ms")
print(f"p99 latency:     {pct(lat, .99):.3f} ms")
print(f"Max latency:     {max(lat):.3f} ms")
PY
