package com.veriprotocol.springAI.core;


import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class IngestProducer {

  private final KafkaTemplate<String, IngestRequestEvent> kafkaTemplate;
  private final String topic;

  public IngestProducer(
      KafkaTemplate<String, IngestRequestEvent> kafkaTemplate,
      @Value("${smartsearch.kafka.ingest-topic}") String topic
  ) {
    this.kafkaTemplate = kafkaTemplate;
    this.topic = topic;
  }

  public void send(String docId, String contentHash) {
    kafkaTemplate.send(topic, docId, new IngestRequestEvent(docId, contentHash, Instant.now()));
  }
}