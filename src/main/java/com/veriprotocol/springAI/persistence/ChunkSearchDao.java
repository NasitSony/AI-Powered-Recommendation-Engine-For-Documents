package com.veriprotocol.springAI.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.veriprotocol.springAI.sharding.ShardJdbcRouter;

@Repository
public class ChunkSearchDao {


    private final ShardJdbcRouter shardJdbcRouter;

    public ChunkSearchDao(
            ShardJdbcRouter shardJdbcRouter) {
        this.shardJdbcRouter = shardJdbcRouter;
    }

    public List<ChunkHit> searchTopK(
            String tenantId,
            String queryVectorLiteral,
            int k) {

        JdbcTemplate jdbc =
                shardJdbcRouter.jdbcFor(tenantId);

        String sql = """
        SELECT doc_id, chunk_id, chunk_text,
               (embedding <-> CAST(? AS vector)) AS dist
        FROM document_chunks
        WHERE tenant_id = ?
        ORDER BY embedding <-> CAST(? AS vector)
        LIMIT ?
        """;

        return jdbc.query(
                sql,
                new Object[]{
                        queryVectorLiteral,
                        tenantId,
                        queryVectorLiteral,
                        k
                },
                new RowMapper<ChunkHit>() {
                    @Override
                    public ChunkHit mapRow(
                            ResultSet rs,
                            int rowNum) throws SQLException {

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

    public record ChunkHit(
            String docId,
            int chunkId,
            String chunkText,
            double distance
    ) {}


}
