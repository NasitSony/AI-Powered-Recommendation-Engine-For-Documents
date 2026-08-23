package com.veriprotocol.springAI.core;

public record IngestRequestEvent(
        String tenantId,
        String documentId,
       //String contentHash,
       long requestedAtEpochMs
) {}
