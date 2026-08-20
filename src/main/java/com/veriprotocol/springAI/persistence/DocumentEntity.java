package com.veriprotocol.springAI.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;



@Entity
@Table(name = "documents")
public class DocumentEntity {


	@Id
	private String id;

	@Column(name = "text", nullable = false, columnDefinition = "TEXT")
	private String text;

	@Column(name = "status", nullable = false)
	@Enumerated(EnumType.STRING)
	private DocumentStatus status = DocumentStatus.PENDING;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "request_id")
    private String requestId;


	@Column(name = "content_hash", length = 64)
	private String contentHash;

	@Column(name = "last_error", columnDefinition = "TEXT")
	private String lastError;
	
	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt = Instant.now();
	
	
	// --- reliability fields in your schema ---
    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @Column(name = "worker_id")
    private String workerId;

    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;
    
    


	

	

	// Store vector as String in JPA (we’ll query via JdbcTemplate for similarity)
	//@Transient
	//@Column(name = "embedding", columnDefinition = "TEXT")
	//private String embedding; // nullable until worker fills it


    protected DocumentEntity() {}

    public DocumentEntity(String id, String text) {
    	  this.id = id;
    	  this.text = text;
    	  this.status = DocumentStatus.PENDING;
    }


    public DocumentEntity(String id, String text, Instant createdAt, String embedding) {
    	  this.id = id;
    	  this.text = text;
    	  this.createdAt = createdAt;
    	  //this.embedding = embedding;
    	  this.status = DocumentStatus.READY; // if embedding exists
    	  //touch();
    }

    /* ===== getters ===== */
    public String getId() { return id; }
    public String getRequestId() { return requestId; }
    public String getText() { return text; }
    public DocumentStatus getStatus() { return status; }
    public String getContentHash() { return contentHash; }
    public String getLastError() { return lastError; }
    public String getTenantId() {
        return tenantId;
    }



    /* ===== setters (only where needed) ===== */
    public void setRequestId(String requestId) { this.requestId = requestId;}
    public void setText(String text) { this.text = text; }
    public void setStatus(DocumentStatus status) { this.status = status; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
    public void setLastError(String lastError) { this.lastError = lastError; }

    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public void setWorkerId(String workerId) { this.workerId = workerId; }
    public void setProcessingStartedAt(Instant t) { this.processingStartedAt = t; }
    public void setNextRetryAt(Instant t) { this.nextRetryAt = t; }
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }



    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
