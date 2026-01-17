package com.veriprotocol.springAI.persistance;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "documents")
public class DocumentEntity {

	@Id
	private String id;
	
	@Column(name = "text", nullable = false, columnDefinition = "TEXT")
	private String text;
	
	@Column(name = "created_at", nullable = false)
	private Instant createdAt;
	
	// Store vector as String in JPA (we’ll query via JdbcTemplate for similarity)
    @Column(name = "embedding", nullable = false, columnDefinition = "TEXT")
    private String embedding; // pgvector literal like '[0.1,0.2,...]'

    protected DocumentEntity() {}

    public DocumentEntity(String id, String text, Instant createdAt, String embedding) {
        this.id = id;
        this.text = text;
        this.createdAt = createdAt;
        this.embedding = embedding;
    }
    
    public String getId() { return id; }
    public String getText() { return text; }
    public Instant getCreatedAt() { return createdAt; }
    public String getEmbedding() { return embedding; }

    public void setText(String text) { this.text = text; }
    public void setEmbedding(String embedding) { this.embedding = embedding; }

    
	
}
