package com.veriprotocol.springAI.core;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class InMemoryDocumentIndex {
	public record Document(String id, String text, float[] embedding, Instant createdAt) {}
    public record ScoredDocument(Document doc, double score) {}

    private final Map<String, Document> docs = new ConcurrentHashMap<>();

    public Document upsert(String id, String text, float[] embedding) {
        Document d = new Document(id, text, embedding, Instant.now());
        docs.put(id, d);
        return d;
    }


    public int size() {
        return docs.size();
    }

    public ArrayList<ScoredDocument> search(float[] queryEmbedding, int k) {
        ArrayList<ScoredDocument> scored = new ArrayList<>(docs.size());
        for (Document d : docs.values()) {
            double score = VectorMath.cosineSimilarity(queryEmbedding, d.embedding());
            scored.add(new ScoredDocument(d, score));
        }
        // Deterministic ordering: score desc, then id asc
        scored.sort((a, b) -> {
            int c = Double.compare(b.score(), a.score());
            if (c != 0) {
				return c;
			}
            return a.doc().id().compareTo(b.doc().id());
        });
        if (k < scored.size()) {
			return (ArrayList<ScoredDocument>) scored.subList(0, k);
		}
        return scored;
    }
}
