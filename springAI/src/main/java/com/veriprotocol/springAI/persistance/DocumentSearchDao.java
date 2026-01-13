package com.veriprotocol.springAI.persistance;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

//import com.networknt.schema.OutputFormat.List;
import java.util.List;

@Repository
public class DocumentSearchDao {
	
	private final JdbcTemplate jdbcTemplate;

    public DocumentSearchDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record SearchHit(String id, String text, double score) {}


 // cosine distance operator in pgvector: <=> (smaller is more similar)
    // score = 1 - distance  (roughly; for cosine distance this is a convenient monotone transform)
    public List<SearchHit> searchByCosine(String queryVectorLiteral, int k) {
        String sql = """
            SELECT id, text, (1.0 - (embedding <=> ?::vector)) AS score
            FROM documents
            ORDER BY embedding <=> ?::vector
            LIMIT ?
        """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new SearchHit(
                        rs.getString("id"),
                        rs.getString("text"),
                        rs.getDouble("score")
                ),
                queryVectorLiteral,
                queryVectorLiteral,
                k
        );
    }
}
