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

import com.veriprotocol.springAI.core.EmbeddingService;
import com.veriprotocol.springAI.core.InMemoryDocumentIndex;
import com.veriprotocol.springAI.persistance.DocumentEntity;
import com.veriprotocol.springAI.persistance.DocumentRepository;
import com.veriprotocol.springAI.persistance.DocumentSearchDao;
import com.veriprotocol.springAI.persistance.DocumentWriteDao;
import com.veriprotocol.springAI.persistance.PgVector;

@RestController
@RequestMapping("/api")
@Validated
public class DocumentSearchController {

	private final EmbeddingService embeddingService;
   // private final InMemoryDocumentIndex index;
    private final DocumentRepository documentRepository;
    private final DocumentSearchDao searchDao;
    private final DocumentWriteDao documentWriteDao;

    public DocumentSearchController(EmbeddingService embeddingService,
            DocumentRepository documentRepository,
            DocumentSearchDao searchDao, DocumentWriteDao documentWriteDao) {
        this.embeddingService = embeddingService;
        this.documentRepository = documentRepository;
        this.searchDao = searchDao;
        this.documentWriteDao = documentWriteDao;
        
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok");
    }
    
    public record UpsertDocumentRequest(String id, @NotBlank String text) {}

    @PostMapping("/documents")
    public Map<String, Object> upsert(@RequestBody UpsertDocumentRequest req) {
    	String id = (req.id() == null || req.id().isBlank()) ? UUID.randomUUID().toString() : req.id();

        float[] emb = embeddingService.embed(req.text());
        String vec = PgVector.toLiteral(emb);

        // Upsert behavior: simplest is save() overwrite (same id)
        DocumentEntity entity = new DocumentEntity(id, req.text(), Instant.now(), vec);
        //documentRepository.save(entity);

        documentWriteDao.upsert(id, req.text(), Instant.now(), vec);

        
        return Map.of("docId", id, "dims", emb.length);
    }
    
    @GetMapping("/search")
    public Map<String, Object> search(@RequestParam("q") String q,
                                      @RequestParam(name = "k", defaultValue = "5") int k) {
       // float[] qv = embeddingService.embed(q);
        
        float[] qEmb = embeddingService.embed(q);
        String qVec = PgVector.toLiteral(qEmb);

        List<Map<String, Object>> results = searchDao.searchByCosine(qVec, k).stream()
                .map(h -> Map.<String, Object>of("docId", h.id(), "score", h.score(), "text", h.text()))
                .toList();

        return Map.of("query", q, "k", k, "results", results);
    }
      
}
