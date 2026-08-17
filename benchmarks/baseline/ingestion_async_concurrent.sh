#!/usr/bin/env bash
set -euo pipefail

API="http://localhost:8080"
COUNT=${COUNT:-500}
CONCURRENCY=${CONCURRENCY:-10}
RUN_ID=$(date +%Y%m%d-%H%M%S)

IDS="benchmarks/baseline/concurrent-${RUN_ID}-ids.csv"
OUT="benchmarks/baseline/concurrent-${RUN_ID}-results.csv"

echo "request_id,doc_id" > "$IDS"
echo "request_id,doc_id,created_at,ready_at,latency_ms,status,retry_count" > "$OUT"

submit_one() {
  i="$1"
  req_id="concurrent-${RUN_ID}-$(printf "%04d" "$i")"

  text="Document $i discusses distributed systems, replication, consensus, storage durability, fault tolerance, retries, recovery, and asynchronous processing."

  resp=$(curl -s -X POST "$API/api/documents" \
    -H "Content-Type: application/json" \
    -d "{
      \"requestId\": \"$req_id\",
      \"id\": \"doc-$req_id\",
      \"text\": \"$text\"
    }")

  doc_id=$(python3 -c \
    'import sys,json; print(json.load(sys.stdin)["docId"])' <<< "$resp")

  echo "$req_id,$doc_id"
}

export -f submit_one
export API RUN_ID

START_NS=$(python3 - <<'PY'
import time
print(time.time_ns())
PY
)

seq 1 "$COUNT" \
  | xargs -P "$CONCURRENCY" -I{} bash -c 'submit_one "$@"' _ {} \
  >> "$IDS"

SUBMIT_END_NS=$(python3 - <<'PY'
import time
print(time.time_ns())
PY
)

echo "Submitted $COUNT documents with concurrency=$CONCURRENCY"

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

python3 - "$START_NS" "$SUBMIT_END_NS" "$END_NS" "$OUT" "$CONCURRENCY" <<'PY'
import sys,csv,statistics
from datetime import datetime

start=int(sys.argv[1])
submit_end=int(sys.argv[2])
end=int(sys.argv[3])
path=sys.argv[4]
concurrency=sys.argv[5]

with open(path) as f:
    rows=list(csv.DictReader(f))

lat=sorted(float(r["latency_ms"]) for r in rows)
ready=sum(r["status"]=="READY" for r in rows)
failed=len(rows)-ready

def pct(v,p):
    return v[round((len(v)-1)*p)]

created=[datetime.fromisoformat(r["created_at"].replace("Z","+00:00")) for r in rows]
done=[datetime.fromisoformat(r["ready_at"].replace("Z","+00:00")) for r in rows]

processing_span=(max(done)-min(created)).total_seconds()
submit_seconds=(submit_end-start)/1e9

print()
print("=== SmartSearch Concurrent Ingestion ===")
print(f"Documents:              {len(rows)}")
print(f"Concurrency:            {concurrency}")
print(f"READY:                  {ready}")
print(f"FAILED:                 {failed}")
print(f"Submission time:        {submit_seconds:.3f} s")
print(f"Producer rate:          {len(rows)/submit_seconds:.3f} docs/s")
print(f"Processing span:        {processing_span:.3f} s")
print(f"Completion rate:        {len(rows)/processing_span:.3f} docs/s")
print(f"Average latency:        {statistics.mean(lat):.3f} ms")
print(f"p50 latency:            {pct(lat,.50):.3f} ms")
print(f"p95 latency:            {pct(lat,.95):.3f} ms")
print(f"p99 latency:            {pct(lat,.99):.3f} ms")
print(f"Max latency:            {max(lat):.3f} ms")
PY