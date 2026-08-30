

import subprocess
import json
import statistics
import time

DB = "postgresql://smartsearch:password@localhost:5434/smartsearch"

K = 10
EF_SEARCH_VALUES = [10, 20, 40, 80, 160]

QUERIES = [
    "How do distributed systems recover from failures?",
    "What improves vector search performance?",
    "How does sharding help databases scale?",
    "Why is caching useful for low latency?",
    "How does consensus coordinate distributed nodes?",
    "What techniques improve transaction processing?",
    "How does load balancing distribute work?",
    "What is fault tolerance in cloud infrastructure?",
    "How does scheduling manage shared resources?",
    "How do storage systems support efficient retrieval?",
]


def run_sql(sql):
    result = subprocess.run(
        ["psql", DB, "-Atc", sql],
        capture_output=True,
        text=True,
        check=True,
    )
    return [
        line.strip()
        for line in result.stdout.splitlines()
        if line.strip()
    ]


def embed(text):
    payload = json.dumps({
        "model": "nomic-embed-text",
        "input": text,
    })

    result = subprocess.run(
        [
            "curl",
            "-s",
            "http://localhost:11434/api/embed",
            "-d",
            payload,
        ],
        capture_output=True,
        text=True,
        check=True,
    )

    data = json.loads(result.stdout)
    return data["embeddings"][0]


def vector_literal(vec):
    return "[" + ",".join(f"{x:.8f}" for x in vec) + "]"


def exact_top_k(query_vector):
    sql = f"""
    BEGIN;
    SET LOCAL enable_indexscan = off;
    SET LOCAL enable_bitmapscan = off;

    SELECT doc_id
    FROM phase9_semantic_vectors
    ORDER BY embedding <-> '{query_vector}'::vector
    LIMIT {K};

    COMMIT;
    """

    rows = run_sql(sql)

    return [
        row for row in rows
        if row not in ("BEGIN", "SET", "COMMIT")
    ]


def hnsw_top_k(query_vector, ef_search):
    sql = f"""
    BEGIN;
    SET LOCAL enable_seqscan = off;
    SET LOCAL hnsw.ef_search = {ef_search};

    SELECT doc_id
    FROM phase9_semantic_vectors
    ORDER BY embedding <-> '{query_vector}'::vector
    LIMIT {K};

    COMMIT;
    """

    rows = run_sql(sql)

    return [
        row for row in rows
        if row not in ("BEGIN", "SET", "COMMIT")
    ]


def recall_at_k(exact, approx):
    return len(set(exact) & set(approx)) / K

def hnsw_execution_time(query_vector, ef_search):
    sql = f"""
    SET enable_seqscan = off;
    SET hnsw.ef_search = {ef_search};

    EXPLAIN (ANALYZE, FORMAT JSON)
    SELECT doc_id
    FROM phase9_semantic_vectors
    ORDER BY embedding <-> '{query_vector}'::vector
    LIMIT {K};
    """

    result = subprocess.run(
        ["psql", DB, "-X", "-q", "-A", "-t", "-c", sql],
        capture_output=True,
        text=True,
        check=True,
    )

    plan = json.loads(result.stdout)
    return plan[0]["Execution Time"]


def main():
    query_vectors = [
        (text, vector_literal(embed(text)))
        for text in QUERIES
    ]

    exact_results = {}

    print("Building exact ground truth...")

    for i, (_, qvec) in enumerate(query_vectors):
        exact_results[i] = exact_top_k(qvec)

    print()

    for ef_search in EF_SEARCH_VALUES:
        recalls = []
        execution_times = []

        for i, (text, qvec) in enumerate(query_vectors):
            approx = hnsw_top_k(
                qvec,
                ef_search
            )

            recall = recall_at_k(
                exact_results[i],
                approx
            )

            execution_ms = hnsw_execution_time(
                qvec,
                ef_search
            )

            recalls.append(recall)
            execution_times.append(execution_ms)

        times_sorted = sorted(execution_times)

        p95_index = int(
            0.95 * (len(times_sorted) - 1)
        )

        print(
            f"ef_search={ef_search:3d} "
            f"mean_recall@{K}={statistics.mean(recalls):.4f} "
            f"mean_exec_ms={statistics.mean(execution_times):.3f} "
            f"median_exec_ms={statistics.median(execution_times):.3f} "
            f"p95_exec_ms={times_sorted[p95_index]:.3f}"
        )

if __name__ == "__main__":
    main()