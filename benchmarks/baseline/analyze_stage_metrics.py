import re
import statistics
from pathlib import Path

path = Path("benchmarks/baseline/instrumented-b7-metrics.log")
lines = path.read_text().splitlines()

patterns = {
    "delete_ms": r"delete_ms=(\d+)",
    "chunking_ms": r"chunking_ms=(\d+)",
    "embedding_ms": r"embedding_ms=(\d+)",
    "chunk_write_ms": r"chunk_write_ms=(\d+)",
    "add_document_total_ms": r"add_document_total_ms=(\d+)",
    "lease_ms": r"lease_ms=(\d+)",
    "payload_load_ms": r"payload_load_ms=(\d+)",
    "work_ms": r"work_ms=(\d+)",
    "mark_ready_ms": r"mark_ready_ms=(\d+)",
}

data = {k: [] for k in patterns}

for line in lines:
    for name, pattern in patterns.items():
        m = re.search(pattern, line)
        if m:
            data[name].append(int(m.group(1)))

def percentile(values, p):
    values = sorted(values)
    if not values:
        return 0
    idx = round((len(values) - 1) * p)
    return values[idx]

print()
print("=== SmartSearch Stage Timing Analysis ===")
print(f"{'Stage':28} {'N':>5} {'Avg':>9} {'p50':>9} {'p95':>9} {'p99':>9} {'Max':>9}")
print("-" * 87)

for name, values in data.items():
    if not values:
        continue

    print(
        f"{name:28} "
        f"{len(values):5d} "
        f"{statistics.mean(values):9.2f} "
        f"{percentile(values, .50):9.2f} "
        f"{percentile(values, .95):9.2f} "
        f"{percentile(values, .99):9.2f} "
        f"{max(values):9.2f}"
    )
