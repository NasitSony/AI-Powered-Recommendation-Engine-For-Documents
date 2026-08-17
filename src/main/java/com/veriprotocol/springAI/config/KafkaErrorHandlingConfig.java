package com.veriprotocol.springAI.config;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;

import com.veriprotocol.springAI.core.DocumentService;
import com.veriprotocol.springAI.core.IngestMetrics;
import com.veriprotocol.springAI.core.IngestRequestEvent;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
//api-key: ${OPENAI_API_KEY}

@Configuration
@Slf4j
public class KafkaErrorHandlingConfig {


	@PostConstruct
	  void loaded() {
	    log.info("✅ KafkaErrorHandlingConfig LOADED");
	  }

@Bean
public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(
    KafkaTemplate<String, Object> kafkaTemplate,
    @Value("${smartsearch.kafka.ingest-dlq-topic}") String dlqTopic,
    DocumentService documentService,
    IngestMetrics metrics
) {
	log.info("✅ Using custom DeadLetterPublishingRecoverer with FAILED-persist + metrics");
    return new DeadLetterPublishingRecoverer(
        kafkaTemplate,
        (record, ex) -> new TopicPartition(dlqTopic, record.partition())
    ) {
        @Override
        public void accept(
                ConsumerRecord<?, ?> record,
                Consumer<?, ?> consumer,
                Exception ex) {

            //String docId = (record.key() == null) ? "null" : record.key().toString();
            String docId = record.key() != null ? record.key().toString() : "unknown";
            Object v = record.value();
            if ("unknown".equals(docId) && v != null) {
                docId = v.toString(); // temporary fallback until you key properly
            }
            String reason =
                (ex instanceof org.springframework.dao.DataAccessException) ? "DB_ERROR" :
                (ex instanceof org.springframework.kafka.support.serializer.DeserializationException) ? "DESERIALIZATION_ERROR" :
                "PROCESSING_ERROR";

            // ✅ DLQ observability (do this BEFORE any best-effort DB update)
            metrics.onDlq();
            log.error("metric=dlq_event docId={} reason={} topic={} partition={} offset={} error={}",
                docId, reason, record.topic(), record.partition(), record.offset(), ex.toString()
            );

            // ✅ Persist FAILED state (best effort; don't block DLQ publishing)
            // Local Kafka retries have now been exhausted.
// Count ONE durable retry cycle.
            try {
                if (record.key() != null) {
                    int retryCount =
                            documentService.markRetryCycleExhausted(
                                    docId,
                                    rootMessage(ex)
                            );

                    log.warn(
                            "metric=retry_cycle_exhausted docId={} durableRetryCount={}",
                            docId,
                            retryCount
                    );
                }
            } catch (Exception e) {
                log.error(
                        "Failed to persist exhausted retry cycle docId={}",
                        docId,
                        e
                );
            }

            super.accept(record, consumer, ex); // ✅ publish to DLQ
        }
    };
}



	@Bean(name = "smartsearchKafkaListenerContainerFactory")
	public ConcurrentKafkaListenerContainerFactory<String, IngestRequestEvent>
	smartsearchKafkaListenerContainerFactory(
	    ConsumerFactory<String, IngestRequestEvent> consumerFactory,
	    @Qualifier("smartsearchErrorHandler") DefaultErrorHandler handler
	) {
	    log.info("✅ smartsearchKafkaListenerContainerFactory created");

	    var factory = new ConcurrentKafkaListenerContainerFactory<String, IngestRequestEvent>();
	    factory.setConsumerFactory(consumerFactory);
	    factory.setCommonErrorHandler(handler);

	    // ✅ commit offsets after each record is successfully processed
	    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);

        factory.setConcurrency(4);

	    return factory;
	}



@Bean(name = "smartsearchErrorHandler")
public DefaultErrorHandler kafkaErrorHandler(DeadLetterPublishingRecoverer recoverer,
	    IngestMetrics metrics) {
    var backOff = new org.springframework.util.backoff.FixedBackOff(2000L, 3L);
    DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);

    handler.addNotRetryableExceptions(
        //org.springframework.kafka.listener.ListenerExecutionFailedException.class,
        IllegalArgumentException.class,
        org.springframework.kafka.support.serializer.DeserializationException.class
    );

    handler.setRetryListeners((record, ex, attempt) -> {
    	metrics.onRetry(); // ✅ add this
        log.error("metric=retry_attempt attempt={} docId={} topic={} partition={} offset={} error={}",
            attempt, record.key(), record.topic(), record.partition(), record.offset(), ex.toString());

    });

    log.info("✅ smartsearchErrorHandler wired");
    return handler;
}


	/*@Bean
	public ConcurrentKafkaListenerContainerFactory<String, IngestRequestEvent> kafkaListenerContainerFactory(
	    ConsumerFactory<String, IngestRequestEvent> consumerFactory,
	    DefaultErrorHandler kafkaErrorHandler
	) {
	  var factory = new ConcurrentKafkaListenerContainerFactory<String, IngestRequestEvent>();
	  factory.setConsumerFactory(consumerFactory);
	  factory.setCommonErrorHandler(kafkaErrorHandler);
	  return factory;
	}*/


	private static String rootMessage(Throwable t) {
		  Throwable cur = t;
		  while (cur.getCause() != null) {
			cur = cur.getCause();
		  }
		  String msg = cur.getMessage();
		  if (msg == null || msg.isBlank()) {
			msg = cur.getClass().getSimpleName();
		  }
		  return msg.length() > 500 ? msg.substring(0, 500) : msg;
	}


    private static Object safeKey(ConsumerRecord<?, ?> record) {
        try { return record.key(); } catch (Exception ignored) { return "<key?>"; }
    }

    private static Throwable rootCause(Throwable t) {
        Throwable cur = t;
        while (cur != null && cur.getCause() != null) {
			cur = cur.getCause();
		}
        return cur == null ? t : cur;
    }


}
