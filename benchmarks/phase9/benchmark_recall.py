import subprocess
import random
import statistics

DB = "postgresql://smartsearch:password@localhost:5434/smartsearch"
TENANT = "phase9-search-bench"

K = 10
NUM_QUERIES = 10
DIM = 768
SEED = 20260829


def run_sql(sql):
    result = subprocess.run(
        ["psql", DB, "-Atc", sql],
        capture_output=True,
        text=True,
        check=True,
    )
    return [line.strip() for line in result.stdout.splitlines() if line.strip()]


def vector_literal(values):
    return "[" + ",".join(f"{x:.6f}" for x in values) + "]"


def make_queries():
    queries = []

    for i in range(NUM_QUERIES):
        rng = random.Random(SEED + i)

        vec = [
            rng.uniform(-1.0, 1.0)
            for _ in range(DIM)
        ]

        queries.append(vector_literal(vec))

    return queries


def exact_top_k(query_vector):
    sql = f"""
    BEGIN;
    SET LOCAL enable_indexscan = off;
    SET LOCAL enable_bitmapscan = off;

    SELECT doc_id
    FROM document_chunks
    WHERE tenant_id = '{TENANT}'
    ORDER BY embedding <-> '{query_vector}'::vector
    LIMIT {K};

    COMMIT;
    """

    rows = run_sql(sql)

    return [
        row
        for row in rows
        if row not in ("BEGIN", "SET", "COMMIT")
    ]


def hnsw_top_k(query_vector, ef_search=40):
    sql = f"""
    BEGIN;
    SET LOCAL enable_seqscan = off;
    SET LOCAL hnsw.ef_search = {ef_search};

    SELECT doc_id
    FROM document_chunks
    WHERE tenant_id = '{TENANT}'
    ORDER BY embedding <-> '{query_vector}'::vector
    LIMIT {K};

    COMMIT;
    """

    rows = run_sql(sql)

    return [
        row
        for row in rows
        if row not in ("BEGIN", "SET", "COMMIT")
    ]


def recall_at_k(exact, approx):
    return len(set(exact) & set(approx)) / K


def main():
    queries = make_queries()
    EF_SEARCH = 400

    recalls = []

    for i, query in enumerate(queries):
        exact = exact_top_k(query)
        approx = hnsw_top_k(query, ef_search=EF_SEARCH)
        recall = recall_at_k(exact, approx)
        recalls.append(recall)

        print(
            f"query={i:02d} "
            f"exact={len(exact)} "
            f"hnsw={len(approx)} "
            f"recall@{K}={recall:.2f}"
        )

    print()
    print(f"ef_search={EF_SEARCH}")
    print(f"queries={NUM_QUERIES}")
    print(f"ef_search=40")
    print(f"mean_recall@{K}={statistics.mean(recalls):.4f}")
    print(f"min_recall@{K}={min(recalls):.4f}")
    print(f"max_recall@{K}={max(recalls):.4f}")


if __name__ == "__main__":
    main()