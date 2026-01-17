package com.veriprotocol.springAI.controller.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;


import jakarta.validation.constraints.NotBlank;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.veriprotocol.springAI.core.DocumentService;
import com.veriprotocol.springAI.core.EmbeddingService;
import com.veriprotocol.springAI.core.InMemoryDocumentIndex;
import com.veriprotocol.springAI.persistance.ChunkSearchDao;
import com.veriprotocol.springAI.persistance.DocumentEntity;
import com.veriprotocol.springAI.persistance.DocumentRepository;
import com.veriprotocol.springAI.persistance.DocumentSearchDao;
import com.veriprotocol.springAI.persistance.DocumentWriteDao;
import com.veriprotocol.springAI.persistance.PgVector;

@RestController
@RequestMapping("/api")
@Validated
public class DocumentSearchController {

	
    private final DocumentService documentService;

    public DocumentSearchController(DocumentService documentService) {
        this.documentService = documentService;   
    }

    public record UpsertDocumentRequest(String id, @NotBlank String text) {}

    

    
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok");
    }
    
    @PostMapping("/documents")
    public Map<String, Object> upsert(@RequestBody UpsertDocumentRequest req) {
        String id = (req.id() == null || req.id().isBlank())
                ? UUID.randomUUID().toString()
                : req.id();

        documentService.addDocument(id, req.text());  // ✅ this does chunking + inserts chunks

        return Map.of("docId", id);
    }

    
    @GetMapping("/search")
    public List<ChunkSearchDao.ChunkHit> search(@RequestParam(name = "q") String q,
                                                @RequestParam(name = "k", defaultValue = "3") int k) {
        return documentService.semanticSearchChunks(q, k);
    }
      
}
