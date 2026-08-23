package com.veriprotocol.springAI.controller.api;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.veriprotocol.springAI.controller.api.dto.DocumentStatusDto;
import com.veriprotocol.springAI.core.IngestProducer;
import com.veriprotocol.springAI.persistence.DocumentReadDao;
import com.veriprotocol.springAI.persistence.DocumentWriteDao;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final DocumentWriteDao documentWriteDao;
    private final DocumentReadDao documentReadDao;
    private final IngestProducer ingestProducer;

    @PostMapping("/retry/{id}")
    public ResponseEntity<DocumentStatusDto> retry(
            @RequestParam(name = "tenantId") String tenantId,
            @PathVariable("id") String id) {

        var existing =
                documentReadDao.findStatusByTenantAndId(
                        tenantId,
                        id
                );

        if (existing.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        int updated =
                documentWriteDao.forceToPending(
                        tenantId,
                        id
                );

        if (updated == 1) {
            ingestProducer.sendRetry(tenantId, id);
        }

        return documentReadDao
                .findStatusByTenantAndId(tenantId, id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/republish-pending")
    public ResponseEntity<?> republishPending(
            @RequestParam(name = "tenantId") String tenantId,
            @RequestParam(defaultValue = "50") int limit) {

        int republished = 0;

        List<String> ids =
                documentReadDao.findOldPendingDocIdsForTenant(
                        tenantId,
                        limit,
                        3600
                );

        for (String id : ids) {

            if (documentWriteDao.claimPendingForRepublish(
                    tenantId,
                    id,
                    3600
            ) == 1) {

                ingestProducer.sendRetry(tenantId, id);
                republished++;
            }
        }

        return ResponseEntity.ok(
                new RepublishResponse(republished, ids)
        );
    }

    record RepublishResponse(int republished, List<String> docIds) {}
}
