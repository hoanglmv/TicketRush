package com.ticketrush.service.kafka;

import com.ticketrush.event.AgentTelemetryEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

/**
 * Consumer thu thập số liệu phân tích AI Agent & RAG từ Kafka stream:
 * - Hỗ trợ truyền phát real-time logs sang Elasticsearch / Datadog / Prometheus mà không làm chậm latency trả lời chatbot.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class AgentTelemetryConsumer {

    @KafkaListener(
            topics = "${app.kafka.topics.telemetry:agent-telemetry-events}",
            groupId = "ticketrush-telemetry-group"
    )
    public void handleTelemetry(@Payload AgentTelemetryEvent event) {
        log.info("Kafka Consumed AgentTelemetry: logId={}, latency={}ms, model={}, ragScore={}",
                event.getLogId(), event.getLatencyMs(), event.getModelUsed(), event.getSimilarityScore());
        // Dữ liệu telemetry có thể được đẩy tiếp vào TimescaleDB / ClickHouse / BigQuery để phân tích lâu dài
    }
}
