package com.veriprotocol.springAI.controller.api.dto;

public record DocumentRequest(
        String tenantId,
        String requestId,
        String id,
        String text
) {}