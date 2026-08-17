#!/usr/bin/env bash
set -euo pipefail

API="http://localhost:8080"
COUNT=100
RUN_ID=$(date +%Y%m%d-%H%M%S)

IDS="benchmarks/baseline/async-${RUN_ID}-ids.csv"
OUT="benchmarks/baseline/async-${RUN_ID}-results.csv"

echo "request_id,doc_id" > "$IDS"
echo "request_id,doc_id,created_at,ready_at,latency_ms,status,retry_count" > "$OUT"

START_NS=$(python3 - <<'PY'
import time
print(time.time_ns())
PY
)

# --------------------------------------------------
# Phase 1: submit without waiting for READY
# --------------------------------------------------

for i in $(seq 1 "$COUNT"); do

  REQ_ID="async-${RUN_ID}-$(printf "%03d" "$i")"

  TEXT="Document $i describes distributed systems including replication, consensus, fault tolerance, message delivery, storage durability, retries, recovery, and asynchronous processing."

  RESP=$(curl -s -X POST "$API/api/documents" \
    -H "Content-Type: application/json" \
    -d "{
      \"requestId\": \"$REQ_ID\",
      \"id\": \"doc-$REQ_ID\",
      \"text\": \"$TEXT\"
    }")

  DOC_ID=$(python3 -c \
    'import sys,json; print(json.load(sys.stdin)["docId"])' <<< "$RESP")

  echo "$REQ_ID,$DOC_ID" >> "$IDS"

done

SUBMIT_END_NS=$(python3 - <<'PY'
import time
print(time.time_ns())
PY
)

echo "Submitted $COUNT documents."

# --------------------------------------------------
# Phase 2: wait for every document
# --------------------------------------------------

tail -n +2 "$IDS" | while IFS=, read -r REQ_ID DOC_ID; do

  while true; do

    RESP=$(curl -s "$API/api/documents/$DOC_ID")

    STATUS=$(python3 -c \
      'import sys,json; print(json.load(sys.stdin)["status"])' <<< "$RESP")

    if [[ "$STATUS" == "READY" || "$STATUS" == "FAILED" ]]; then

      CREATED=$(python3 -c \
        'import sys,json; print(json.load(sys.stdin)["createdAt"])' <<< "$RESP")

      UPDATED=$(python3 -c \
        'import sys,json; print(json.load(sys.stdin)["updatedAt"])' <<< "$RESP")

      RETRIES=$(python3 -c \
        'import sys,json; print(json.load(sys.stdin)["retryCount"])' <<< "$RESP")

      LATENCY_MS=$(python3 - "$CREATED" "$UPDATED" <<'PY'
import sys
from datetime import datetime

a = datetime.fromisoformat(sys.argv[1].replace("Z","+00:00"))
b = datetime.fromisoformat(sys.argv[2].replace("Z","+00:00"))

print(round((b-a).total_seconds()*1000,3))
PY
)

      echo "$REQ_ID,$DOC_ID,$CREATED,$UPDATED,$LATENCY_MS,$STATUS,$RETRIES" >> "$OUT"

      break
    fi

    sleep 0.02

  done

done

END_NS=$(python3 - <<'PY'
import time
print(time.time_ns())
PY
)

python3 - "$START_NS" "$SUBMIT_END_NS" "$END_NS" "$OUT" <<'PY'
import sys
import csv
import statistics

start = int(sys.argv[1])
submit_end = int(sys.argv[2])
end = int(sys.argv[3])
path = sys.argv[4]

with open(path) as f:
    rows = list(csv.DictReader(f))

lat = sorted(float(r["latency_ms"]) for r in rows)

ready = sum(r["status"] == "READY" for r in rows)
failed = len(rows) - ready

submit_seconds = (submit_end-start)/1e9
total_seconds = (end-start)/1e9

def pct(values,p):
    idx = round((len(values)-1)*p)
    return values[idx]

print()
print("=== SmartSearch Async Ingestion Baseline ===")
print(f"Documents:          {len(rows)}")
print(f"READY:              {ready}")
print(f"FAILED:             {failed}")
print(f"Submission time:    {submit_seconds:.3f} s")
print(f"Producer rate:      {len(rows)/submit_seconds:.3f} docs/s")
print(f"Total drain time:   {total_seconds:.3f} s")
print(f"Pipeline throughput:{len(rows)/total_seconds:.3f} docs/s")
print(f"Average latency:    {statistics.mean(lat):.3f} ms")
print(f"p50 latency:        {pct(lat,.50):.3f} ms")
print(f"p95 latency:        {pct(lat,.95):.3f} ms")
print(f"p99 latency:        {pct(lat,.99):.3f} ms")
print(f"Max latency:        {max(lat):.3f} ms")
PY