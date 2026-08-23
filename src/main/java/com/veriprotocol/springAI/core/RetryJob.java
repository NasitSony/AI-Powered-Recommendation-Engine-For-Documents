package com.veriprotocol.springAI.core;

import java.util.List;

import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.veriprotocol.springAI.persistence.DocumentReadDao;
import com.veriprotocol.springAI.persistence.DocumentWriteDao;

import com.veriprotocol.springAI.sharding.ShardedRetryDao;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class RetryJob {

    private final ShardedRetryDao shardedRetryDao;
    private final IngestProducer ingestProducer;

    // tune these later
    private static final int BATCH_SIZE = 20;
    private static final int MAX_RETRIES = 3;

    @Scheduled(fixedDelay = 30_000, initialDelay = 20_000)
    public void republishFailedDocs() {
        try {
            List<DocumentReadDao.RetryableDoc> docs =
                    shardedRetryDao.findRetryableFailedDocs(
                            BATCH_SIZE,
                            MAX_RETRIES
                    );
            if (docs.isEmpty()) {
                return;
            }

            for (DocumentReadDao.RetryableDoc doc : docs) {

                String tenantId = doc.tenantId();
                String id = doc.docId();

                try {
                    int updated =
                            shardedRetryDao.resetFailedToPending(
                                    tenantId,
                                    id
                            );

                    if (updated == 1) {
                        ingestProducer.sendRetry(
                                tenantId,
                                id
                        );

                        log.info(
                                "RetryJob republished tenantId={} docId={}",
                                tenantId,
                                id
                        );
                    }

                } catch (DataAccessException e) {
                    log.warn(
                            "RetryJob DB error for tenantId={} docId={}: {}",
                            tenantId,
                            id,
                            e.getMostSpecificCause().getMessage()
                    );

                } catch (Exception e) {
                    log.warn(
                            "RetryJob failed for tenantId={} docId={}: {}",
                            tenantId,
                            id,
                            e.getMessage()
                    );
                }
            }
        } catch (DataAccessException e) {
            // DB down / connection refused / pool timeout → expected in chaos test
            log.warn("RetryJob skipped (DB unavailable): {}", e.getMostSpecificCause().getMessage());
        } catch (Exception e) {
            log.error("RetryJob unexpected failure", e);
        }
    }
}
