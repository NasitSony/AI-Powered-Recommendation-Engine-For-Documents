package com.veriprotocol.springAI.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;



import jakarta.transaction.Transactional;


public interface DocumentRepository extends JpaRepository<DocumentEntity, String> {
	
	// ✅ ADD THIS
    Optional<DocumentEntity> findByTenantIdAndRequestId(
            String tenantId,
            String requestId
    );
	
	@Modifying
    @Transactional
    @Query("""
        update DocumentEntity d
           set d.status = :status,
               d.lastError = :err,
               d.retryCount = :retryCount,
               d.workerId = :workerId,
               d.processingStartedAt = :processingStartedAt,
               d.nextRetryAt = :nextRetryAt
         where d.id = :id
        """)
    int updateProcessingFields(
            @Param("id") String id,
            @Param("status") DocumentStatus status,
            @Param("err") String err,
            @Param("retryCount") Integer retryCount,
            @Param("workerId") String workerId,
            @Param("processingStartedAt") java.time.Instant processingStartedAt,
            @Param("nextRetryAt") java.time.Instant nextRetryAt
    );
	
	@Modifying
    @Transactional
    @Query("""
        update DocumentEntity d
           set d.status = :status,
               d.lastError = :err
         where d.id = :id
        """)
    int updateStatusAndError(@Param("id") String id,
                             @Param("status") DocumentStatus status,
                             @Param("err") String err);

	@Modifying
    @Transactional
    @Query("""
        update DocumentEntity d
           set d.lastError = :err
         where d.id = :id
        """)
    int updateLastError(@Param("id") String id,
                        @Param("err") String err);
	
	
	@Modifying
    @Transactional
    @Query("""
        update DocumentEntity d
           set d.status = 'PROCESSING',
               d.workerId = :workerId,
               d.processingStartedAt = CURRENT_TIMESTAMP,
               d.lastError = null
         where d.id = :id
           and d.status in ('PENDING','FAILED')
        """)
    int tryMarkProcessing(@Param("id") String id,
                          @Param("workerId") String workerId);

	
}
/*
 * 
 * @Query(value = """
	        SELECT *, (embedding <-> CAST(:queryVec AS vector)) AS dist
	        FROM documents
	        ORDER BY embedding <-> CAST(:queryVec AS vector)
	        LIMIT :k
	        """, nativeQuery = true)
	    List search(@Param("queryVec") String queryVec,
	                                @Param("k") int k);*/
 