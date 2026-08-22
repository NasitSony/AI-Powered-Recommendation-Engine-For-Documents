package com.veriprotocol.springAI.sharding;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ShardingTestController {

    private final ShardedDocumentDao dao;
    private final ShardRouter shardRouter;

    public ShardingTestController(
            ShardedDocumentDao dao,
            ShardRouter shardRouter) {

        this.dao = dao;
        this.shardRouter = shardRouter;
    }

    @PostMapping("/api/sharding/test")
    public Map<String, Object> insert(
            @RequestParam String tenantId) {

        String id = dao.insertTestDocument(
                tenantId,
                "phase6-" + tenantId,
                "Phase 6 routed write for " + tenantId
        );

        return Map.of(
                "tenantId", tenantId,
                "shard", shardRouter.shardNameFor(tenantId),
                "docId", id
        );
    }
}