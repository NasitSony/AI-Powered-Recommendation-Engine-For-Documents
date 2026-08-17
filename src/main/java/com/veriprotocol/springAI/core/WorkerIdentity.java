package com.veriprotocol.springAI.core;

import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class WorkerIdentity {

    private final String instanceId = UUID.randomUUID().toString();

    public String getWorkerId() {
        return instanceId + ":" + Thread.currentThread().getName();
    }
}
