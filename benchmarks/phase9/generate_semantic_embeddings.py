import json
import subprocess
import random
import sys

ROWS = int(sys.argv[1]) if len(sys.argv) > 1 else 1000
START = int(sys.argv[2]) if len(sys.argv) > 2 else 0
SEED = 20260829


TOPICS = [
    "distributed systems",
    "database indexing",
    "machine learning",
    "computer networking",
    "operating systems",
    "cloud infrastructure",
    "cybersecurity",
    "data engineering",
    "storage systems",
    "software architecture",
]

CONCEPTS = [
    "replication",
    "consensus",
    "fault tolerance",
    "vector search",
    "transaction processing",
    "caching",
    "load balancing",
    "scheduling",
    "sharding",
    "failure recovery",
]

ACTIONS = [
    "improves reliability",
    "reduces latency",
    "increases throughput",
    "handles failures",
    "coordinates concurrent work",
    "scales across multiple nodes",
    "protects shared resources",
    "supports efficient retrieval",
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


rng = random.Random(SEED)

for i in range(START + ROWS):
    topic = rng.choice(TOPICS)
    concept = rng.choice(CONCEPTS)
    action = rng.choice(ACTIONS)

    # Consume the exact same deterministic RNG sequence for
    # rows before START, but don't generate their embeddings again.
    if i < START:
        continue

    text = (
        f"This document discusses {concept} in {topic}. "
        f"The technique {action} in production systems. "
        f"Example record number {i}."
    )

    vec = embed(text)

    safe_text = text.replace("\t", " ").replace("\n", " ")

    print(
        f"semantic-{i:05d}\t"
        f"{safe_text}\t"
        f"{vector_literal(vec)}"
    )