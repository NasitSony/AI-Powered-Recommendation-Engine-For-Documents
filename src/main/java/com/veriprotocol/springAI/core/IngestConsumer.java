package com.veriprotocol.springAI.core;

import java.time.Duration;
import java.time.OffsetDateTime;

import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.kafka.support.Acknowledgment;

import com.veriprotocol.springAI.persistence.DocumentReadDao;


import jakarta.annotation.PostConstruct;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;

//import com.veriprotocol.springAI.core.DocumentService;
//import com.veriprotocol.springAI.core.IngestRequestEvent;
//import com.veriprotocol.springAI.core.WorkerIdentity;
//import com.veriprotocol.springAI.core.IngestMetrics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;


@Lazy(false)
@Component
@Slf4j
public class IngestConsumer {

    private final WorkerIdentity workerIdentity;
    private final IngestMetrics ingestMetrics;
    private final DocumentService documentService;
    private final DocumentReadDao documentReadDao;
    private final DistributionSummary processingAge;
    private final MeterRegistry meterRegistry;
    private final Counter dbWriteCounter;

    public IngestConsumer(
            WorkerIdentity workerIdentity,
            DocumentService documentService,
            IngestMetrics ingestMetrics,
            DocumentReadDao documentReadDao,
           // DistributionSummary processingAge,
            MeterRegistry meterRegistry
            
    ) {
        this.workerIdentity = workerIdentity;
        this.documentService = documentService;
        this.ingestMetrics = ingestMetrics;
        this.documentReadDao = documentReadDao;
        this.meterRegistry = meterRegistry;
        this.dbWriteCounter = Counter.builder("smartsearch_db_write_total")
                .description("Total DB writes")
                .register(meterRegistry);
        this.processingAge = DistributionSummary.builder("smartsearch_processing_age_seconds")
                .description("Age of document when processing begins")
                .register(meterRegistry);
       
        
    }

    @KafkaListener(
            topics = "${smartsearch.kafka.ingest-topic}",
            //groupId = "smartsearch-workers",
            containerFactory = "smartsearchKafkaListenerContainerFactory"
    )
    public void consume(IngestRequestEvent event)  {

        String tenantId = event.tenantId();
        String docId = event.documentId();

        MDC.put("tenantId", tenantId);
        MDC.put("docId", docId);

        var st0 =
                documentService
                        .getStatusInternal(tenantId, docId)
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Doc not found tenantId="
                                                + tenantId
                                                + " docId="
                                                + docId
                                )
                        );

        var createdAt = st0.createdAt();

        var status = st0;

        long ageSeconds = Math.max(0, Duration.between(status.createdAt(), OffsetDateTime.now()).getSeconds());

        try {
            log.info("📥 Received ingest event for docId={}", docId);
            
           
            // 1) Optional idempotency check via status
            var statusOpt =
                    documentService.getStatusInternal(
                            tenantId,
                            docId
                    );
            if (statusOpt.isEmpty()) {
                throw new IllegalStateException("Doc not found: " + docId);
            }
           // var status = statusOpt.get();
            if ("READY".equals(status.status())) { // your DB uses READY, not SUCCESS
                log.info("✅ Already READY, skipping {}", docId);
                return;
            }
            
            processingAge.record(ageSeconds);
            
            


            // 2) Claim the lease (prevents double-processing on rebalance/retry)
            String workerId = workerIdentity.getWorkerId();

            long leaseStart = System.nanoTime();
            boolean claimed = documentService.claimProcessingLease(tenantId, docId, workerId);

            long leaseMs =
                    (System.nanoTime() - leaseStart) / 1_000_000;
            if (!claimed) {
                log.info("⏭️ Not claimed (already processing or not pending/failed), skipping {}", docId);
                return;
            }



         // 3) Load payload (text) from DB (Kafka carries only docId)
            long loadStart = System.nanoTime();
            DocumentReadDao.DocPayload payload =
                    documentService.loadDocPayload(
                            tenantId,
                            docId
                    );
            long loadMs =
                    (System.nanoTime() - loadStart) / 1_000_000;

            String text = payload.text();



            /*if (text.contains("DUPLICATE_INFLIGHT_TEST")) {
                log.warn("CHAOS_DUPLICATE_INFLIGHT_WINDOW docId={} sleeping_ms=10000", docId);
                try {
                    Thread.sleep(100_000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ie);
                }
            }*/

            // Hard-crash test: simulates JVM/process death after lease claim
            /*if (text.contains("CRASH_AFTER_CLAIM")) {
                log.error("CHAOS_WORKER_HARD_CRASH docId={}", docId);
                Runtime.getRuntime().halt(137);
            }*/

            /*if (text.contains("DB_DOWN_TEST")) {
                log.warn("CHAOS_DB_DOWN_WINDOW docId={} sleeping_before_work_ms=15000", docId);
                try {
                    Thread.sleep(15_000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Chaos sleep interrupted", ie);
                }
            }*/
            
            //Throw exception in consumer
            /*if (text.contains("FAIL")) {
                log.error("TEST_SIMULATED_EXCEPTION docId={}", docId);
                throw new RuntimeException("Simulated consumer failure for retry test");
            }*/
           
            // 4) Do the real work (chunk + embed + write chunks)
            long workStart = System.nanoTime();
            documentService.addDocument(
                    tenantId,
                    docId,
                    text
            );

            /*if (text.contains("CRASH_AFTER_WRITE")) {
                log.error("CHAOS_CRASH_AFTER_WRITE docId={}", docId);
                Runtime.getRuntime().halt(137);
            }*/

            long workMs =
                    (System.nanoTime() - workStart) / 1_000_000;
           

            // 5) Mark READY
            long readyStart = System.nanoTime();
            documentService.markReadyDb(
                    tenantId,
                    docId
            );

            documentService.invalidateSearchCache(
                    tenantId
            );
            long readyMs =
                    (System.nanoTime() - readyStart) / 1_000_000;
         // AFTER success
            dbWriteCounter.increment();
            log.info("WROTE_CHUNKS_TO_DB_SLEEPING_BEFORE_COMMIT docId={}", docId);
            long e2eMs = java.time.Duration.between(createdAt.toInstant(), java.time.Instant.now()).toMillis();
            log.info("metric=e2e_latency_ms docId={} value={} status=READY", docId, e2eMs);
            log.info(
                    "metric=consumer_stage docId={} lease_ms={} payload_load_ms={} work_ms={} mark_ready_ms={}",
                    docId,
                    leaseMs,
                    loadMs,
                    workMs,
                    readyMs
            );
           //CHAOS TEST: kill-before-commit (validated no duplicates)
           // Runtime.getRuntime().halt(137); // hard kill, no shutdown hooks
           // ack.acknowledge(); // ✅ commit offset ONLY after DB success

            log.info("✅ Ingest complete docId={}", docId);
            ingestMetrics.onSucceeded();

        } catch (Exception e) {

            log.error("❌ Ingest attempt failed docId={}", docId, e);

            // Do NOT mark durable FAILED here.
            // Spring Kafka local retries are still part of the same processing cycle.
            // Durable FAILED + retry_count increment happens only after
            // DefaultErrorHandler exhausts its local retry policy.

            throw e;

        } finally {
            MDC.clear();
        }
    }
    
    @PostConstruct
    public void init() {
      log.info("🔥 IngestConsumer bean created");
    }
}
