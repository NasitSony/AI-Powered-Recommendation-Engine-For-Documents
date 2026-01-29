package com.veriprotocol.springAI.controller.api;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.veriprotocol.springAI.controller.api.dto.DocStatusResponse;
import com.veriprotocol.springAI.controller.api.dto.DocumentRequest;
import com.veriprotocol.springAI.core.DocumentService;
import com.veriprotocol.springAI.core.RagService;
import com.veriprotocol.springAI.persistance.ChunkSearchDao;

import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api")
@Validated
public class DocumentSearchController {


    private final DocumentService documentService;
    private final RagService ragService;

    public DocumentSearchController(DocumentService documentService, RagService ragService) {
        this.documentService = documentService;
        this.ragService = ragService;
    }

    public record UpsertDocumentRequest(String id, @NotBlank String text) {}




    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok");
    }

   /* @PostMapping("/documents")
   // public Map<String, Object> upsert(@RequestBody UpsertDocumentRequest req) {
        String id = (req.id() == null || req.id().isBlank())
                ? UUID.randomUUID().toString()
                : req.id();

        documentService.addDocument(id, req.text());  // ✅ this does chunking + inserts chunks

        return Map.of("docId", id);
   // }*/

  /*  @PostMapping("/documents")
    public ResponseEntity<DocStatusResponse> create(@RequestBody DocumentRequest req) {

        String docId = documentService.createPending(req);

        return ResponseEntity
            .accepted()
            .body(new DocStatusResponse(docId, "PENDING"));
    }*/

    @PostMapping("/documents")
    public ResponseEntity<DocStatusResponse> add(@RequestBody DocumentRequest req) {
        String docId = documentService.createPending(req.id(), req.text());
        return ResponseEntity.status(202).body(new DocStatusResponse(docId, "PENDING"));
    }

    @GetMapping("/search")
    public List<ChunkSearchDao.ChunkHit> search(@RequestParam(name = "q") String q,
                                                @RequestParam(name = "k", defaultValue = "3") int k) {
        return documentService.semanticSearchChunks(q, k);
    }

    @GetMapping("/ask")
    public RagService.AskResponse ask(
            @RequestParam(name = "q") String q,
            @RequestParam(name = "k", defaultValue = "5") int k
    ) {
        return ragService.ask(q, k);
    }

}
