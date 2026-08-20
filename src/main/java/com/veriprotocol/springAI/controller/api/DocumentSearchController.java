package com.veriprotocol.springAI.controller.api;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.veriprotocol.springAI.controller.api.dto.DocStatusResponse;
import com.veriprotocol.springAI.controller.api.dto.DocumentRequest;
import com.veriprotocol.springAI.controller.api.dto.DocumentStatusDto;
import com.veriprotocol.springAI.core.DbHealth;
import com.veriprotocol.springAI.core.DocumentService;
import com.veriprotocol.springAI.core.RagService;
import com.veriprotocol.springAI.persistence.ChunkSearchDao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api")
@Validated
public class DocumentSearchController {


    private final DocumentService documentService;
    private final RagService ragService;
    private final DbHealth dbHealth;   // ✅ injected instance
    private static final Logger log = LoggerFactory.getLogger(DocumentSearchController.class);


    public DocumentSearchController(DocumentService documentService, RagService ragService, DbHealth dbHealth) {
        this.documentService = documentService;
        this.ragService = ragService;
        this.dbHealth = dbHealth;
    }

    public record UpsertDocumentRequest(String id, @NotBlank String text) {}




    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok");
    }



    @PostMapping("/documents")
    public ResponseEntity<?> add(@RequestBody DocumentRequest req) {
    	
    	// ✅ FAST FAIL before touching JPA
        if (!dbHealth.isDbUp()) {
            return ResponseEntity.status(503)
                .body(new ErrorResponse(
                    "DB_UNAVAILABLE",
                    "Database is unavailable. Please retry."
                ));
        }

        String docId = documentService.createPending(
                req.tenantId(),
                req.requestId(),
                req.text()
        );
        log.info("metric=ingest_accepted docId={} requestId={}", docId, req.requestId());

        return ResponseEntity.status(202)
            .body(new DocStatusResponse(docId, "PENDING"));
    }

    @GetMapping("/search")
    public List<ChunkSearchDao.ChunkHit> search(
            @RequestParam(name = "tenantId") String tenantId,
            @RequestParam(name = "q") String q,
            @RequestParam(name = "k", defaultValue = "3") int k) {

        return documentService.semanticSearchChunks(
                tenantId,
                q,
                k
        );
    }

    @GetMapping("/documents/{id}")
    public ResponseEntity<DocumentStatusDto> status(
            @RequestParam(name = "tenantId") String tenantId,
            @PathVariable("id") String id) {

        Optional<DocumentStatusDto> status =
                documentService.getStatus(
                        tenantId,
                        id
                );

        return status.map(ResponseEntity::ok)
                .orElseGet(() ->
                        ResponseEntity.notFound().build());
    }

    @GetMapping("/ask")
    public RagService.AskResponse ask(
            @RequestParam(name = "tenantId") String tenantId,
            @RequestParam(name = "q") String q,
            @RequestParam(name = "k", defaultValue = "5") int k
    ) {
        return ragService.ask(
                tenantId,
                q,
                k
        );
    }

    @GetMapping("/documents")
    public ResponseEntity<List<DocumentStatusDto>> list(
            @RequestParam(name = "tenantId") String tenantId,
            @RequestParam(name = "status") String status,
            @RequestParam(name = "limit", defaultValue = "20") int limit
    ) {

        String s = status.toUpperCase();

        if (!List.of(
                "PENDING",
                "PROCESSING",
                "READY",
                "FAILED"
        ).contains(s)) {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(
                documentService.listByStatus(
                        tenantId,
                        s,
                        limit
                )
        );
    }


    record ErrorResponse(String code, String message) {}

}
