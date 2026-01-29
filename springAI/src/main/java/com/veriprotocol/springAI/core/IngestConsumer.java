package com.veriprotocol.springAI.core;


import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class IngestConsumer {

	private final EmbeddingService embeddingService;
    private final DocumentService documentService;


    @KafkaListener(
    		  topics = "${smartsearch.kafka.ingest-topic}",
    		  groupId = "smartsearch-workers",
    		  containerFactory = "smartsearchKafkaListenerContainerFactory"
    )

    /*public void consume(IngestRequestEvent event) {

    	log.info("Worker received doc {}", event.documentId());

        try {
            documentService.markProcessing(event.documentId());
            embeddingService.processDocument(event.documentId());
            documentService.markReady(event.documentId());

        } catch (Exception e) {
            log.error("Worker failed for {}", event.documentId(), e);
            documentService.markError(event.documentId(), e.getMessage());
            throw e; // allows Kafka retry later
        }
    }*/

    public void consume(IngestRequestEvent event) {

           log.info("Worker received doc {}", event.documentId());


            documentService.markProcessing(event.documentId());

         // TEMP failure injection
            if (event.documentId().startsWith("fail")) {
                throw new RuntimeException("forced failure for DLQ test");
            }

            // ✅ pass content to embedding pipeline
            embeddingService.processDocument(event.documentId(), event.content()); // or event.content()

            documentService.markReady(event.documentId());
    }



}
