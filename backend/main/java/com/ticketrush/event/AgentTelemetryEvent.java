package com.ticketrush.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTelemetryEvent {
    private String logId;
    private String userQuery;
    private String replySnippet;
    private long latencyMs;
    private long retrievalTimeMs;
    private double similarityScore;
    private String ragSource;
    private String modelUsed;
    private String status;
    private LocalDateTime timestamp;
}
