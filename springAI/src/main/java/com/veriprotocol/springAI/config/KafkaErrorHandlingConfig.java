package com.veriprotocol.springAI.config;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;

import com.veriprotocol.springAI.core.DocumentService;
import com.veriprotocol.springAI.core.IngestRequestEvent;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;


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
    DocumentService documentService
) {

  return new DeadLetterPublishingRecoverer(kafkaTemplate,
      (record, ex) -> new TopicPartition(dlqTopic, record.partition())) {

    @Override
    public void accept(ConsumerRecord<?, ?> record, Exception ex) {
      try {
        // docId should be key in your pipeline
        if (record.key() != null) {
          documentService.markFailed(record.key().toString(), rootMessage(ex));
        }
      } catch (Exception e) {
        // do NOT block DLQ publishing
        log.error("DLQ: failed to mark doc FAILED, continuing DLQ publish", e);
      }

      super.accept(record, ex); // ✅ publish to DLQ
    }
  };
}



	@Bean(name = "smartsearchKafkaListenerContainerFactory")
	public ConcurrentKafkaListenerContainerFactory<String, IngestRequestEvent>
	smartsearchKafkaListenerContainerFactory(
	    ConsumerFactory<String, IngestRequestEvent> consumerFactory,
	    DefaultErrorHandler kafkaErrorHandler
	) {
		//log.info("✅ SmartSearch DefaultErrorHandler bean created");
		log.info("✅ smartsearchKafkaListenerContainerFactory created");


	  var factory = new ConcurrentKafkaListenerContainerFactory<String, IngestRequestEvent>();
	  factory.setConsumerFactory(consumerFactory);
	  factory.setCommonErrorHandler(kafkaErrorHandler);
	  return factory;
	}



@Bean
public DefaultErrorHandler kafkaErrorHandler(DeadLetterPublishingRecoverer recoverer) {

  // 3 retries after the first failure => total 4 deliveries
  var backOff = new org.springframework.util.backoff.FixedBackOff(2000L, 3L);

  DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);

  handler.addNotRetryableExceptions(DeserializationException.class);

  // Log each retry attempt number (this is the key debug signal)
  handler.setRetryListeners((record, ex, attempt) -> {
    log.error("🔁 retry attempt={} key={} topic={} partition={} offset={}",
        attempt, record.key(), record.topic(), record.partition(), record.offset(), ex);
  });

  log.info("✅ DefaultErrorHandler wired: backOff={}, recoverer={}",
      backOff.getClass().getSimpleName(), recoverer.getClass().getName());

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



}
