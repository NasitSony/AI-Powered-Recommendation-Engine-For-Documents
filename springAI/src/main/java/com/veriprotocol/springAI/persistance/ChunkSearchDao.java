package com.veriprotocol.springAI.persistance;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ChunkSearchDao {
	
	private final JdbcTemplate jdbcTemplate;

    public ChunkSearchDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    public List<ChunkHit> searchTopK(String queryVectorLiteral, int k) {

        String sql = """
            SELECT doc_id, chunk_id, chunk_text,
                   (embedding <-> CAST(? AS vector)) AS dist
            FROM document_chunks
            ORDER BY embedding <-> CAST(? AS vector)
            LIMIT ?
        """;

        return jdbcTemplate.query(
                sql,
                new Object[]{queryVectorLiteral, queryVectorLiteral, k},
                new RowMapper<ChunkHit>() {
                    @Override
                    public ChunkHit mapRow(ResultSet rs, int rowNum) throws SQLException {
                        return new ChunkHit(
                                rs.getString("doc_id"),
                                rs.getInt("chunk_id"),
                                rs.getString("chunk_text"),
                                rs.getDouble("dist")
                        );
                    }
                }
        );
    }
    
    public record ChunkHit(String docId, int chunkId, String chunkText, double distance) {}



}
