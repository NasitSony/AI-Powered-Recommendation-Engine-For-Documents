package com.veriprotocol.springAI.persistance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.networknt.schema.OutputFormat.List;

public interface DocumentRepository extends JpaRepository<DocumentEntity, String> {

	@Query(value = """
	        SELECT *, (embedding <-> CAST(:queryVec AS vector)) AS dist
	        FROM documents
	        ORDER BY embedding <-> CAST(:queryVec AS vector)
	        LIMIT :k
	        """, nativeQuery = true)
	    List search(@Param("queryVec") String queryVec,
	                                @Param("k") int k);
}
