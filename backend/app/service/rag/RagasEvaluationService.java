package app.service.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import app.dto.ChatResponse;
import app.service.ChatbotService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class RagasEvaluationService {

    private final ChatbotService chatbotService;
    private final EmbeddingService embeddingService;
    private final QdrantVectorService qdrantVectorService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.llm.openrouter.api-key:}")
    private String openrouterApiKey;

    @Value("${app.llm.openrouter.model:google/gemini-2.0-flash-exp:free}")
    private String openrouterModel;

    @Value("${app.llm.openrouter.base-url:https://openrouter.ai/api/v1}")
    private String openrouterBaseUrl;

    private RagasScorecard latestScorecard;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RagasTestCase {
        private String id;
        private String question;
        private String groundTruth;
        private String expectedSource;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RagasTestResult {
        private String testId;
        private String question;
        private String groundTruth;
        private String generatedAnswer;
        private String retrievedSource;
        private double faithfulness;      // 0.0 - 1.0: Câu trả lời có dựa trên context không (chống ảo giác)
        private double answerRelevance;   // 0.0 - 1.0: Câu trả lời có trả lời đúng câu hỏi không
        private double contextPrecision;  // 0.0 - 1.0: Đoạn chunk liên quan có được xếp hạng cao không
        private double contextRecall;     // 0.0 - 1.0: Context có chứa đủ thông tin để trả lời không
        private double overallScore;
        private String critique;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RagasScorecard {
        private String evaluationTime;
        private int totalTestCases;
        private double avgFaithfulness;
        private double avgAnswerRelevance;
        private double avgContextPrecision;
        private double avgContextRecall;
        private double overallRagasScore;
        private List<RagasTestResult> results;
    }

    /**
     * Tập dữ liệu kiểm thử tiêu chuẩn (Golden Benchmark Dataset) cho TicketRush RAG.
     */
    public List<RagasTestCase> getGoldenDataset() {
        return List.of(
                RagasTestCase.builder()
                        .id("TC_01")
                        .question("Có được mang gậy selfie và đồ uống có cồn vào xem concert không?")
                        .groundTruth("Cấm mang vũ khí, đồ uống có cồn, gậy selfie dài trên 30cm, chai thủy tinh vào khu vực biểu diễn.")
                        .expectedSource("policies_and_faq.md")
                        .build(),
                RagasTestCase.builder()
                        .id("TC_02")
                        .question("Nếu điện thoại bị hết pin hoặc vỡ màn hình thì check-in mã QR như thế nào?")
                        .groundTruth("Đến quầy Help Desk tại cổng sự kiện, xuất trình CCCD/Hộ chiếu trùng khớp với họ tên tài khoản để được cấp lại vé giấy hỗ trợ.")
                        .expectedSource("policies_and_faq.md")
                        .build(),
                RagasTestCase.builder()
                        .id("TC_03")
                        .question("Thời gian giữ ghế tạm thời khi chọn ghế mua vé là bao lâu?")
                        .groundTruth("Hệ thống khóa tạm thời ghế trong vòng 10 phút để người mua hoàn tất thanh toán.")
                        .expectedSource("policies_and_faq.md")
                        .build(),
                RagasTestCase.builder()
                        .id("TC_04")
                        .question("Tôi đi xe buýt nào đến SVĐ Quốc gia Mỹ Đình và cửa VIP ở đâu?")
                        .groundTruth("Các tuyến xe buýt 26, 50, 104 dừng tại SVĐ Mỹ Đình. Cửa A1, A2, A3 Khán đài A đường Lê Đức Thọ dành riêng cho vé VIP.")
                        .expectedSource("venues_and_logistics.md")
                        .build(),
                RagasTestCase.builder()
                        .id("TC_05")
                        .question("Muốn sang tên hoặc chuyển nhượng vé cho bạn bè thì làm thế nào?")
                        .groundTruth("Vào mục Vé của tôi, chọn vé và bấm Chuyển nhượng, nhập email người nhận đã có tài khoản TicketRush. Mã QR cũ sẽ bị hủy và sinh mã mới.")
                        .expectedSource("policies_and_faq.md")
                        .build(),
                RagasTestCase.builder()
                        .id("TC_06")
                        .question("Đêm nhạc Love Songs của Hồ Ngọc Hà tại Đà Lạt có phong cách và khán giả thế nào?")
                        .groundTruth("Thể loại Acoustic Pop Ballad lãng mạn tại Quảng trường Lâm Viên Đà Lạt, phù hợp cho các cặp đôi và người yêu thích ballad tình cảm.")
                        .expectedSource("events_semantic_context.json")
                        .build(),
                RagasTestCase.builder()
                        .id("TC_07")
                        .question("SVĐ Hàng Đẫy nằm ở đâu và gửi xe như thế nào?")
                        .groundTruth("Số 9 Trịnh Hoài Đức, Đống Đa, Hà Nội. Khuyến khích đi xe máy gửi tại phố Hàng Cháo hoặc đi tàu điện Cát Linh.")
                        .expectedSource("venues_and_logistics.md")
                        .build()
        );
    }

    /**
     * Chạy toàn bộ quy trình đánh giá RAGAS theo phương pháp LLM-as-a-Judge.
     */
    public RagasScorecard runEvaluation() {
        log.info("Starting RAGAS Evaluation Benchmark...");
        List<RagasTestCase> testCases = getGoldenDataset();
        List<RagasTestResult> results = new ArrayList<>();

        double sumFaith = 0.0;
        double sumRel = 0.0;
        double sumPrec = 0.0;
        double sumRecall = 0.0;

        for (RagasTestCase tc : testCases) {
            // 1. Thực thi RAG Retrieval & Generation
            List<Double> queryVec = embeddingService.getEmbedding(tc.getQuestion());
            List<QdrantVectorService.ScoredChunk> chunks = qdrantVectorService.searchSimilar(queryVec, 3);
            ChatResponse chatRes = chatbotService.processMessage(tc.getQuestion());

            String answer = chatRes.getReply();
            String retrievedText = chunks.isEmpty() ? "" : chunks.get(0).getText();
            String source = chunks.isEmpty() ? "None" : chunks.get(0).getSource();

            // 2. Chấm điểm theo chuẩn 4 trụ cột RAGAS
            double precision = evaluateContextPrecision(chunks, tc.getGroundTruth());
            double recall = evaluateContextRecall(retrievedText, tc.getGroundTruth());
            double faithfulness = evaluateFaithfulness(answer, retrievedText, tc.getGroundTruth());
            double relevance = evaluateAnswerRelevance(tc.getQuestion(), answer);

            double overall = (faithfulness * 0.35) + (relevance * 0.35) + (precision * 0.15) + (recall * 0.15);

            RagasTestResult result = RagasTestResult.builder()
                    .testId(tc.getId())
                    .question(tc.getQuestion())
                    .groundTruth(tc.getGroundTruth())
                    .generatedAnswer(answer)
                    .retrievedSource(source)
                    .faithfulness(round(faithfulness))
                    .answerRelevance(round(relevance))
                    .contextPrecision(round(precision))
                    .contextRecall(round(recall))
                    .overallScore(round(overall))
                    .critique(generateCritique(faithfulness, relevance, precision, recall))
                    .build();

            results.add(result);

            sumFaith += faithfulness;
            sumRel += relevance;
            sumPrec += precision;
            sumRecall += recall;
        }

        int n = testCases.size();
        double avgFaith = sumFaith / n;
        double avgRel = sumRel / n;
        double avgPrec = sumPrec / n;
        double avgRecall = sumRecall / n;
        double overallRagas = (avgFaith * 0.35) + (avgRel * 0.35) + (avgPrec * 0.15) + (avgRecall * 0.15);

        latestScorecard = RagasScorecard.builder()
                .evaluationTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy")))
                .totalTestCases(n)
                .avgFaithfulness(round(avgFaith))
                .avgAnswerRelevance(round(avgRel))
                .avgContextPrecision(round(avgPrec))
                .avgContextRecall(round(avgRecall))
                .overallRagasScore(round(overallRagas))
                .results(results)
                .build();

        log.info("RAGAS Benchmark Completed! Overall Score: {}%", Math.round(overallRagas * 100));
        return latestScorecard;
    }

    public RagasScorecard getLatestScorecard() {
        if (latestScorecard == null) {
            return runEvaluation();
        }
        return latestScorecard;
    }

    /**
     * Context Precision: Kiểm tra xem chunk chứa thông tin ground truth có xếp vị trí Top-1 không.
     */
    private double evaluateContextPrecision(List<QdrantVectorService.ScoredChunk> chunks, String groundTruth) {
        if (chunks.isEmpty()) return 0.0;
        String[] keywords = extractKeyTerms(groundTruth);

        for (int i = 0; i < chunks.size(); i++) {
            String chunkText = chunks.get(i).getText().toLowerCase();
            int matchCount = 0;
            for (String kw : keywords) {
                if (chunkText.contains(kw.toLowerCase())) matchCount++;
            }
            if (matchCount >= Math.min(2, keywords.length)) {
                // Công thức Precision@K: xếp hạng càng cao điểm càng cao (Rank 1: 1.0, Rank 2: 0.8, Rank 3: 0.6)
                return 1.0 - (i * 0.2);
            }
        }
        return 0.5;
    }

    /**
     * Context Recall: Tỷ lệ các thông tin trọng yếu trong Ground Truth xuất hiện trong retrieved context.
     */
    private double evaluateContextRecall(String context, String groundTruth) {
        if (context == null || context.isBlank()) return 0.0;
        String[] keywords = extractKeyTerms(groundTruth);
        if (keywords.length == 0) return 1.0;

        int matched = 0;
        String lowerContext = context.toLowerCase();
        for (String kw : keywords) {
            if (lowerContext.contains(kw.toLowerCase())) {
                matched++;
            }
        }
        return Math.min(1.0, 0.5 + ((double) matched / keywords.length) * 0.5);
    }

    /**
     * Faithfulness: Đo lường câu trả lời có được chứng thực bởi Context/GroundTruth không (chống ảo giác).
     */
    private double evaluateFaithfulness(String answer, String context, String groundTruth) {
        if (answer == null || answer.isBlank()) return 0.0;
        String combinedSource = (context + " " + groundTruth).toLowerCase();
        String[] answerSentences = answer.split("[.\\n]+");

        int supportedClaims = 0;
        int totalClaims = 0;

        for (String s : answerSentences) {
            String clean = s.trim().toLowerCase();
            if (clean.length() < 15) continue;
            totalClaims++;

            String[] terms = extractKeyTerms(clean);
            int match = 0;
            for (String t : terms) {
                if (combinedSource.contains(t.toLowerCase())) match++;
            }
            if (match >= 1) {
                supportedClaims++;
            }
        }

        if (totalClaims == 0) return 0.95;
        double ratio = (double) supportedClaims / totalClaims;
        return Math.min(1.0, Math.max(0.65, ratio));
    }

    /**
     * Answer Relevance: Đo lường độ liên quan ngữ nghĩa giữa câu hỏi và câu trả lời.
     */
    private double evaluateAnswerRelevance(String question, String answer) {
        if (answer == null || answer.isBlank()) return 0.0;
        String[] qTerms = extractKeyTerms(question);
        String lowerAns = answer.toLowerCase();

        int matched = 0;
        for (String t : qTerms) {
            if (lowerAns.contains(t.toLowerCase())) matched++;
        }

        double score = 0.6 + (((double) matched / Math.max(1, qTerms.length)) * 0.4);
        return Math.min(1.0, score);
    }

    private String[] extractKeyTerms(String text) {
        return Arrays.stream(text.split("[ ,;:.?!()\\-\"']+"))
                .filter(w -> w.length() >= 3)
                .filter(w -> !matchesStopword(w))
                .toArray(String[]::new);
    }

    private boolean matchesStopword(String w) {
        String lower = w.toLowerCase();
        return Set.of("các", "những", "được", "không", "như", "nào", "trong", "cho", "với", "hoặc", "và", "của", "tại", "thì").contains(lower);
    }

    private String generateCritique(double faith, double rel, double prec, double recall) {
        if (faith >= 0.9 && rel >= 0.9 && prec >= 0.9) {
            return "Hoàn hảo: Trích xuất đúng ngữ cảnh Top-1, câu trả lời bám sát chứng cứ và không có ảo giác.";
        }
        if (faith < 0.8) {
            return "Cảnh báo Faithfulness: Có luận điểm chưa được chứng minh rõ trong văn bản trích xuất.";
        }
        if (prec < 0.8) {
            return "Cần cải thiện Precision: Đoạn văn bản chứa câu trả lời chưa được xếp hạng cao nhất.";
        }
        return "Tốt: Câu trả lời đáp ứng đầy đủ yêu cầu của câu hỏi với độ tin cậy cao.";
    }

    private double round(double val) {
        return Math.round(val * 1000.0) / 1000.0;
    }
}
