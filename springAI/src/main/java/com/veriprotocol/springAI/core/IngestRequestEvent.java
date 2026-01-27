package com.veriprotocol.springAI.core;

import java.time.Instant;

public record IngestRequestEvent(String documentId, String contentHash, Instant requestedAt) {}