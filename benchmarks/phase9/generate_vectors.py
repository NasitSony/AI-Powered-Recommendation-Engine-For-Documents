import csv
import random
import sys
from datetime import datetime, timezone

ROWS = int(sys.argv[1]) if len(sys.argv) > 1 else 10000
START = int(sys.argv[2]) if len(sys.argv) > 2 else 0
DIM = 768
SEED = 42
TENANT = "phase9-search-bench"

writer = csv.writer(sys.stdout)

created_at = datetime.now(timezone.utc).isoformat()

for i in range(START, START + ROWS):
    rng = random.Random(SEED + i)

    vec = [
        rng.uniform(-1.0, 1.0)
        for _ in range(DIM)
    ]
    vector_literal = "[" + ",".join(f"{x:.6f}" for x in vec) + "]"

    writer.writerow([
        TENANT,
        f"bench-doc-{i}",
        0,
        f"phase9 synthetic benchmark chunk {i}",
        created_at,
        vector_literal,
    ])
