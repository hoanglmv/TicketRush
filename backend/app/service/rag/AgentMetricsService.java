package app.service.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class AgentMetricsService {

    private static final int MAX_LOGS = 100;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM");

    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong successfulRequests = new AtomicLong(0);
    private final AtomicLong fallbackRequests = new AtomicLong(0);
    private final AtomicLong totalLatencyMs = new AtomicLong(0);
    private final AtomicLong totalRetrievalTimeMs = new AtomicLong(0);
    private final AtomicInteger ragHitCount = new AtomicInteger(0);
    private final AtomicInteger positiveFeedback = new AtomicInteger(0);
    private final AtomicInteger negativeFeedback = new AtomicInteger(0);

    private final Deque<AgentLogEntry> recentLogs = new ConcurrentLinkedDeque<>();
    private final Map<String, AgentLogEntry> logLookup = new HashMap<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentLogEntry {
        private String id;
        private String timestamp;
        private String userQuery;
        private String replyPreview;
        private long latencyMs;
        private long retrievalTimeMs;
        private boolean ragHit;
        private double similarityScore;
        private String ragSource;
        private String model;
        private String status; // SUCCESS, FALLBACK, ERROR
        private String feedback; // POSITIVE, NEGATIVE, NONE
    }

    /**
     * Ghi nhận log truy vấn và cập nhật chỉ số đánh giá của Agent & RAG.
     */
    public synchronized String recordLog(String query, String reply, long totalLatency, long retrievalTime,
                                         boolean ragHit, double score, String source, String model, String status) {
        String logId = UUID.randomUUID().toString();
        totalRequests.incrementAndGet();
        totalLatencyMs.addAndGet(totalLatency);
        totalRetrievalTimeMs.addAndGet(retrievalTime);

        if ("SUCCESS".equals(status)) {
            successfulRequests.incrementAndGet();
        } else if ("FALLBACK".equals(status)) {
            fallbackRequests.incrementAndGet();
        }

        if (ragHit) {
            ragHitCount.incrementAndGet();
        }

        String preview = reply != null ? (reply.length() > 100 ? reply.substring(0, 100) + "..." : reply) : "";

        AgentLogEntry entry = AgentLogEntry.builder()
                .id(logId)
                .timestamp(LocalDateTime.now().format(TIME_FORMATTER))
                .userQuery(query)
                .replyPreview(preview)
                .latencyMs(totalLatency)
                .retrievalTimeMs(retrievalTime)
                .ragHit(ragHit)
                .similarityScore(Math.round(score * 1000.0) / 1000.0)
                .ragSource(source != null ? source : "None")
                .model(model)
                .status(status)
                .feedback("NONE")
                .build();

        recentLogs.addFirst(entry);
        logLookup.put(logId, entry);

        // Duy trì kích thước tối đa
        while (recentLogs.size() > MAX_LOGS) {
            AgentLogEntry removed = recentLogs.removeLast();
            logLookup.remove(removed.getId());
        }

        return logId;
    }

    /**
     * Cập nhật đánh giá của người dùng (Thumbs Up / Thumbs Down).
     */
    public synchronized boolean updateFeedback(String logId, boolean isPositive) {
        AgentLogEntry entry = logLookup.get(logId);
        if (entry != null) {
            if (isPositive) {
                entry.setFeedback("POSITIVE");
                positiveFeedback.incrementAndGet();
            } else {
                entry.setFeedback("NEGATIVE");
                negativeFeedback.incrementAndGet();
            }
            return true;
        }
        return false;
    }

    /**
     * Tổng hợp các chỉ số KPI cho Monitor Dashboard.
     */
    public Map<String, Object> getMetricsSummary() {
        long total = totalRequests.get();
        long success = successfulRequests.get();
        long fallback = fallbackRequests.get();
        int pos = positiveFeedback.get();
        int neg = negativeFeedback.get();
        int totalFeedback = pos + neg;

        double avgLatency = total > 0 ? (double) totalLatencyMs.get() / total : 0.0;
        double avgRetrieval = total > 0 ? (double) totalRetrievalTimeMs.get() / total : 0.0;
        double hitRate = total > 0 ? ((double) ragHitCount.get() / total) * 100.0 : 0.0;
        double satisfaction = totalFeedback > 0 ? ((double) pos / totalFeedback) * 100.0 : 100.0;

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalRequests", total);
        summary.put("successfulRequests", success);
        summary.put("fallbackRequests", fallback);
        summary.put("llmUsageRate", total > 0 ? Math.round(((double) success / total) * 100.0) : 0);
        summary.put("fallbackRate", total > 0 ? Math.round(((double) fallback / total) * 100.0) : 0);
        summary.put("avgLatencyMs", Math.round(avgLatency));
        summary.put("avgRetrievalTimeMs", Math.round(avgRetrieval));
        summary.put("ragHitRate", Math.round(hitRate * 10.0) / 10.0);
        summary.put("userSatisfactionRate", Math.round(satisfaction * 10.0) / 10.0);
        summary.put("positiveFeedback", pos);
        summary.put("negativeFeedback", neg);
        summary.put("recentLogs", new ArrayList<>(recentLogs));

        return summary;
    }
}
