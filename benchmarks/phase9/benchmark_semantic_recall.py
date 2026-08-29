import subprocess
import json
import statistics

DB = "postgresql://smartsearch:password@localhost:5434/smartsearch"

K = 10
EF_SEARCH = 20

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


def hnsw_top_k(query_vector):
    sql = f"""
    BEGIN;
    SET LOCAL enable_seqscan = off;
    SET LOCAL hnsw.ef_search = {EF_SEARCH};

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


def main():
    recalls = []

    for i, text in enumerate(QUERIES):
        qvec = vector_literal(embed(text))

        exact = exact_top_k(qvec)
        approx = hnsw_top_k(qvec)

        recall = recall_at_k(exact, approx)
        recalls.append(recall)

        print(
            f"query={i:02d} "
            f"recall@{K}={recall:.2f} "
            f"text=\"{text}\""
        )

    print()
    print(f"queries={len(QUERIES)}")
    print(f"ef_search={EF_SEARCH}")
    print(f"mean_recall@{K}={statistics.mean(recalls):.4f}")
    print(f"min_recall@{K}={min(recalls):.4f}")
    print(f"max_recall@{K}={max(recalls):.4f}")


if __name__ == "__main__":
    main()