package com.veriprotocol.springAI.persistance;

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


	//@Enumerated(EnumType.STRING)
	//@Column(nullable = false)
	//private DocumentStatus status = DocumentStatus.PENDING;

	@Column(name = "status", nullable = false)
	@Enumerated(EnumType.STRING)
	private DocumentStatus status = DocumentStatus.PENDING;


	@Column(name = "content_hash", length = 64)
	private String contentHash;

	@Column(name = "last_error", columnDefinition = "TEXT")
	private String lastError;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt = Instant.now();


	@Id
	private String id;

	@Column(name = "text", nullable = false, columnDefinition = "TEXT")
	private String text;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	// Store vector as String in JPA (we’ll query via JdbcTemplate for similarity)
	@Transient
	@Column(name = "embedding", columnDefinition = "TEXT")
	private String embedding; // nullable until worker fills it


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
    	  this.embedding = embedding;
    	  this.status = DocumentStatus.READY; // if embedding exists
    	  touch();
    }

    public String getId() { return id; }
    public String getText() { return text; }
   // public Instant getCreatedAt() { return createdAt; }
    public String getEmbedding() { return embedding; }

    public void setText(String text) { this.text = text; }
    public void setEmbedding(String embedding) { this.embedding = embedding; }

    public DocumentStatus getStatus() { return status; }
    public void setStatus(DocumentStatus status) { this.status = status; }

    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }

    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }

    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCreatedAt() { return createdAt; }


    @PrePersist
    void onCreate() {
      if (createdAt == null) {
		createdAt = Instant.now();
	  }
      touch();
    }

    @PreUpdate
    void onUpdate() {
      touch();
    }



    private void touch() {
    	  this.updatedAt = Instant.now();
    }

}
