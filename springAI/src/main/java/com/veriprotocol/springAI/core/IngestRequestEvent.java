package com.veriprotocol.springAI.core;

import java.time.Instant;

public record IngestRequestEvent(String documentId, String content, String contentHash, Instant requestedAt) {}