package app.controller;

import app.dto.ApiResponse;
import app.dto.ChatRequest;
import app.dto.ChatResponse;
import app.dto.FeedbackRequest;
import app.service.ChatbotService;
import app.service.rag.AgentMetricsService;
import app.service.rag.RagasEvaluationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/chatbot")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ChatController {

    private final ChatbotService chatbotService;
    private final AgentMetricsService agentMetricsService;
    private final RagasEvaluationService ragasEvaluationService;

    @PostMapping("/message")
    public ResponseEntity<ApiResponse<ChatResponse>> sendMessage(@RequestBody ChatRequest request) {
        ChatResponse response = chatbotService.processMessage(request != null ? request.getMessage() : null);
        return ResponseEntity.ok(ApiResponse.success("Message processed successfully", response));
    }

    @GetMapping("/welcome")
    public ResponseEntity<ApiResponse<ChatResponse>> getWelcomeMessage() {
        ChatResponse response = chatbotService.processMessage(null);
        return ResponseEntity.ok(ApiResponse.success("Welcome message retrieved", response));
    }

    @PostMapping("/feedback")
    public ResponseEntity<ApiResponse<Boolean>> submitFeedback(@RequestBody FeedbackRequest request) {
        boolean ok = agentMetricsService.updateFeedback(request.getLogId(), request.isPositive());
        return ResponseEntity.ok(ApiResponse.success("Feedback recorded", ok));
    }

    @GetMapping("/metrics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMetrics() {
        Map<String, Object> metrics = agentMetricsService.getMetricsSummary();
        return ResponseEntity.ok(ApiResponse.success("Metrics retrieved", metrics));
    }

    @PostMapping("/ragas/evaluate")
    public ResponseEntity<ApiResponse<RagasEvaluationService.RagasScorecard>> runRagasEvaluation() {
        RagasEvaluationService.RagasScorecard scorecard = ragasEvaluationService.runEvaluation();
        return ResponseEntity.ok(ApiResponse.success("RAGAS evaluation completed", scorecard));
    }

    @GetMapping("/ragas/latest")
    public ResponseEntity<ApiResponse<RagasEvaluationService.RagasScorecard>> getLatestRagas() {
        RagasEvaluationService.RagasScorecard scorecard = ragasEvaluationService.getLatestScorecard();
        return ResponseEntity.ok(ApiResponse.success("Latest RAGAS scorecard retrieved", scorecard));
    }
}
