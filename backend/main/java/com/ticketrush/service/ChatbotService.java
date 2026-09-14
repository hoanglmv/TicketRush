package com.ticketrush.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketrush.dto.ChatResponse;
import com.ticketrush.entity.Event;
import com.ticketrush.entity.Zone;
import com.ticketrush.enums.EventStatus;
import com.ticketrush.enums.SeatStatus;
import com.ticketrush.event.AgentTelemetryEvent;
import com.ticketrush.repository.EventRepository;
import com.ticketrush.repository.SeatRepository;
import com.ticketrush.repository.ZoneRepository;
import com.ticketrush.service.kafka.EventProducerService;
import com.ticketrush.service.rag.AgentMetricsService;
import com.ticketrush.service.rag.EmbeddingService;
import com.ticketrush.service.rag.QdrantVectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatbotService {

    private final EventRepository eventRepository;
    private final ZoneRepository zoneRepository;
    private final SeatRepository seatRepository;
    private final EmbeddingService embeddingService;
    private final QdrantVectorService qdrantVectorService;
    private final AgentMetricsService agentMetricsService;
    private final EventProducerService eventProducerService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.llm.provider:openrouter}")
    private String llmProvider;

    @Value("${app.llm.openrouter.api-key:}")
    private String openrouterApiKey;

    @Value("${app.llm.openrouter.model:google/gemini-2.0-flash-exp:free}")
    private String openrouterModel;

    @Value("${app.llm.openrouter.base-url:https://openrouter.ai/api/v1}")
    private String openrouterBaseUrl;

    @Value("${app.llm.groq.api-key:}")
    private String groqApiKey;

    @Value("${app.llm.groq.model:llama3-8b-8192}")
    private String groqModel;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /**
     * Xử lý tin nhắn từ người dùng thông qua AI Engine kết hợp DB Grounding và RAG.
     */
    public ChatResponse processMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return buildWelcomeResponse();
        }

        long startTime = System.currentTimeMillis();
        String userMsg = message.trim();

        // Retrieve RAG semantic knowledge from Vector DB
        long retrievalStart = System.currentTimeMillis();
        List<Double> queryVec = embeddingService.getEmbedding(userMsg);
        List<QdrantVectorService.ScoredChunk> ragChunks = qdrantVectorService.searchSimilar(queryVec, 3);
        long retrievalTime = System.currentTimeMillis() - retrievalStart;

        boolean ragHit = ragChunks != null && !ragChunks.isEmpty() && ragChunks.get(0).getScore() >= 0.35;
        double topScore = (ragChunks != null && !ragChunks.isEmpty()) ? ragChunks.get(0).getScore() : 0.0;
        String ragSource = (ragChunks != null && !ragChunks.isEmpty()) ? ragChunks.get(0).getSource() : "None";

        ChatResponse res = null;
        String modelUsed = "local-rule-engine";
        String status = "FALLBACK";

        // 1. Thử gọi LLM nếu có API key hợp lệ (Ưu tiên OpenRouter, sau đó Groq)
        if (isValidApiKey(openrouterApiKey)) {
            try {
                ChatResponse llmResponse = callOpenRouterLlm(userMsg, ragChunks);
                if (llmResponse != null && llmResponse.getReply() != null && !llmResponse.getReply().isBlank()) {
                    res = enrichWithEventCards(llmResponse, userMsg);
                    modelUsed = openrouterModel;
                    status = "SUCCESS";
                }
            } catch (Exception e) {
                log.warn("OpenRouter API call failed, trying Groq or Intelligent Fallback: {}", e.getMessage());
            }
        }

        if (res == null && isValidApiKey(groqApiKey)) {
            try {
                ChatResponse llmResponse = callGroqLlm(userMsg, ragChunks);
                if (llmResponse != null && llmResponse.getReply() != null && !llmResponse.getReply().isBlank()) {
                    res = enrichWithEventCards(llmResponse, userMsg);
                    modelUsed = "groq/" + groqModel;
                    status = "SUCCESS";
                }
            } catch (Exception e) {
                log.warn("Groq API call failed, switching to Intelligent Fallback: {}", e.getMessage());
            }
        }

        // 2. Fallback Engine: Trợ lý thông minh phân tích ý định kết hợp RAG
        if (res == null) {
            res = handleRuleBasedIntent(userMsg, ragChunks);
        }

        long totalTime = System.currentTimeMillis() - startTime;
        String logId = agentMetricsService.recordLog(userMsg, res.getReply(), totalTime, retrievalTime, ragHit, topScore, ragSource, modelUsed, status);

        res.setLogId(logId);
        res.setLatencyMs(totalTime);
        res.setModelUsed(modelUsed);
        res.setRagScore(topScore);

        // Stream telemetry event to Kafka for real-time async monitoring
        eventProducerService.sendAgentTelemetry(AgentTelemetryEvent.builder()
                .logId(logId)
                .userQuery(userMsg)
                .replySnippet(res.getReply() != null && res.getReply().length() > 100 ? res.getReply().substring(0, 100) : res.getReply())
                .latencyMs(totalTime)
                .retrievalTimeMs(retrievalTime)
                .similarityScore(topScore)
                .ragSource(ragSource)
                .modelUsed(modelUsed)
                .status(status)
                .timestamp(LocalDateTime.now())
                .build());

        return res;
    }

    private boolean isValidApiKey(String key) {
        return key != null && !key.isBlank() && !key.contains("YOUR_") && !key.contains("API_KEY");
    }

    /**
     * Gọi OpenRouter API với System Prompt tăng cường bởi RAG Knowledge.
     */
    private ChatResponse callOpenRouterLlm(String userMsg, List<QdrantVectorService.ScoredChunk> ragChunks) throws Exception {
        return executeOpenAiCompatibleCall(openrouterBaseUrl, openrouterApiKey, openrouterModel, userMsg, ragChunks, true);
    }

    /**
     * Gọi Groq API với System Prompt tăng cường bởi RAG Knowledge.
     */
    private ChatResponse callGroqLlm(String userMsg, List<QdrantVectorService.ScoredChunk> ragChunks) throws Exception {
        return executeOpenAiCompatibleCall("https://api.groq.com/openai/v1", groqApiKey, groqModel, userMsg, ragChunks, false);
    }

    private ChatResponse executeOpenAiCompatibleCall(String baseUrl, String apiKey, String model, String userMsg, 
                                                     List<QdrantVectorService.ScoredChunk> ragChunks, boolean isOpenRouter) throws Exception {
        List<Event> activeEvents = eventRepository.findAll().stream()
                .filter(e -> e.getStatus() == EventStatus.ON_SALE || e.getStatus() == EventStatus.PUBLISHED)
                .limit(15)
                .toList();

        StringBuilder context = new StringBuilder();
        context.append("Hệ thống TicketRush hiện có các sự kiện sau:\n");
        for (Event e : activeEvents) {
            context.append(String.format("- ID: %d | Tên: %s | Thể loại: %s | Địa điểm: %s, %s | Thời gian: %s | Trạng thái: %s\n",
                    e.getId(), e.getName(), e.getCategory(), e.getVenue(), e.getCity(),
                    e.getEventDate() != null ? e.getEventDate().format(DATE_FORMATTER) : "Chưa xác định",
                    e.getStatus()));
        }

        // Bơm tri thức RAG vào Prompt
        if (ragChunks != null && !ragChunks.isEmpty()) {
            context.append("\n[CẨM NANG & ĐIỀU KHOẢN RAG TRÍCH XUẤT TỪ HỆ THỐNG]:\n");
            for (QdrantVectorService.ScoredChunk chunk : ragChunks) {
                if (chunk.getScore() > 0.25) {
                    context.append(String.format("--- Nguồn: %s ---\n%s\n\n", chunk.getSource(), chunk.getText()));
                }
            }
        }

        String systemPrompt = "Bạn là TicketRush Concierge - Trợ lý ảo AI thông minh và thân thiện của nền tảng bán vé sự kiện TicketRush. " +
                "Nhiệm vụ của bạn là hỗ trợ người dùng tìm kiếm sự kiện, tra cứu thông tin vé, kiểm tra ghế ngồi và hướng dẫn mua vé. " +
                "Hãy trả lời ngắn gọn, lịch sự, nhiệt tình bằng tiếng Việt (hoặc ngôn ngữ của người dùng). " +
                "Chỉ đưa ra thông tin dựa trên dữ liệu sự kiện được cung cấp dưới đây. Nếu người dùng hỏi mua vé, hãy khuyên họ chọn sự kiện để xem sơ đồ ghế.\n\n" +
                context;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(4000);
        requestFactory.setReadTimeout(7000);

        RestClient.Builder builder = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(baseUrl);

        if (isOpenRouter) {
            builder.defaultHeader("HTTP-Referer", "https://ticketrush.local")
                   .defaultHeader("X-Title", "TicketRush AI Agent");
        }

        RestClient restClient = builder.build();

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userMsg)
                ),
                "temperature", 0.6,
                "max_tokens", 500
        );

        String jsonResponse = restClient.post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(String.class);

        JsonNode root = objectMapper.readTree(jsonResponse);
        String reply = root.path("choices").get(0).path("message").path("content").asText();

        return ChatResponse.builder()
                .reply(reply)
                .suggestions(List.of("Xem danh sách sự kiện hot", "Cách đặt vé trên TicketRush", "Hàng đợi ảo hoạt động thế nào?"))
                .build();
    }

    /**
     * Bổ sung các Card sự kiện trực quan vào câu trả lời của LLM nếu tìm thấy từ khóa liên quan.
     */
    private ChatResponse enrichWithEventCards(ChatResponse response, String userMsg) {
        List<Event> matchedEvents = findRelevantEvents(userMsg);
        if (!matchedEvents.isEmpty()) {
            response.setEvents(matchedEvents.stream().map(this::toCardDto).limit(3).toList());
            if (matchedEvents.size() == 1) {
                response.setTargetEventId(matchedEvents.get(0).getId());
                response.setActionType("VIEW_EVENT");
            }
        }
        return response;
    }

    /**
     * Engine xử lý ý định thông minh dựa trên ngữ nghĩa và dữ liệu thực tế trong DB kết hợp RAG.
     */
    private ChatResponse handleRuleBasedIntent(String msg, List<QdrantVectorService.ScoredChunk> ragChunks) {
        String lower = msg.toLowerCase();

        // 1. Chào hỏi
        if (matchesAny(lower, "chào", "xin chào", "hello", "hi", "hey", "halo", "alo")) {
            return buildWelcomeResponse();
        }

        // 2. Tra cứu RAG Knowledge (Điều khoản, Cửa vào, Bãi gửi xe, Quy định cấm, Vibe sự kiện)
        if (ragChunks != null && !ragChunks.isEmpty()) {
            QdrantVectorService.ScoredChunk topChunk = ragChunks.get(0);
            if (topChunk.getScore() >= 0.35 && matchesAny(lower, 
                    "quy định", "cấm", "gửi xe", "xe buýt", "xe bus", "cửa vào", "cổng", 
                    "mỹ đình", "quân khu 7", "hàng đẫy", "hòa bình", "lâm viên", "đổi vé", 
                    "sang tên", "hoàn vé", "chuyển nhượng", "qr", "check-in", "dress code", 
                    "trang phục", "acoustic", "rock", "rap", "lãng mạn", "vibe", "trẻ em", 
                    "gia đình", "mang gì", "máy ảnh", "nước", "hết pin", "hỏng màn hình")) {
                
                List<Event> matched = findRelevantEvents(lower);
                return ChatResponse.builder()
                        .reply("📖 **Cẩm nang & Quy định TicketRush:**\n\n" + topChunk.getText())
                        .events(matched.isEmpty() ? null : matched.stream().map(this::toCardDto).limit(2).toList())
                        .suggestions(List.of("Cách đặt vé sự kiện", "Chính sách chuyển nhượng vé", "Sự kiện Hot hôm nay"))
                        .build();
            }
        }

        // 3. Tra cứu còn vé / ghế ngồi cho sự kiện cụ thể
        if (matchesAny(lower, "còn vé", "còn ghế", "chỗ ngồi", "sơ đồ ghế", "giá vé", "khu vip", "hàng ghế")) {
            List<Event> matched = findRelevantEvents(lower);
            if (!matched.isEmpty()) {
                Event target = matched.get(0);
                List<Zone> zones = zoneRepository.findByEventIdOrderBySortOrder(target.getId());
                long availableSeats = seatRepository.countByEventIdAndStatus(target.getId(), SeatStatus.AVAILABLE);

                StringBuilder sb = new StringBuilder();
                sb.append(String.format("🎟️ **%s**\n\n", target.getName()));
                sb.append(String.format("📍 **Địa điểm:** %s (%s)\n", target.getVenue(), target.getCity()));
                sb.append(String.format("⏰ **Thời gian:** %s\n", target.getEventDate().format(DATE_FORMATTER)));
                sb.append(String.format("💺 **Ghế khả dụng:** Còn lại khoảng **%d** ghế trống.\n\n", availableSeats));

                if (!zones.isEmpty()) {
                    sb.append("**Các phân khu & giá vé:**\n");
                    for (Zone z : zones) {
                        sb.append(String.format("- **%s**: %,.0f VNĐ\n", z.getName(), z.getPrice()));
                    }
                }
                sb.append("\nBạn có thể bấm trực tiếp vào thẻ sự kiện bên dưới để vào sơ đồ chọn ghế ngay nhé!");

                return ChatResponse.builder()
                        .reply(sb.toString())
                        .events(List.of(toCardDto(target)))
                        .actionType("SELECT_SEAT")
                        .targetEventId(target.getId())
                        .suggestions(List.of("Cách thanh toán vé", "Hàng đợi ảo hoạt động ra sao?", "Sự kiện khác tại " + target.getCity()))
                        .build();
            }
        }

        // 3. Tra cứu theo thành phố
        if (matchesAny(lower, "hà nội", "hanoi", "hn")) {
            return buildCityEventsResponse("Hanoi", "Hà Nội");
        }
        if (matchesAny(lower, "hồ chí minh", "hcm", "sài gòn", "saigon", "tp.hcm", "tphcm")) {
            return buildCityEventsResponse("Ho Chi Minh", "TP. Hồ Chí Minh");
        }
        if (matchesAny(lower, "đà nẵng", "da nang", "danang")) {
            return buildCityEventsResponse("Da Nang", "Đà Nẵng");
        }
        if (matchesAny(lower, "đà lạt", "da lat", "dalat")) {
            return buildCityEventsResponse("Da Lat", "Đà Lạt");
        }

        // 4. Tra cứu theo thể loại
        if (matchesAny(lower, "concert", "ca nhạc", "âm nhạc", "music", "live music", "liveshow")) {
            return buildCategoryResponse(List.of("CONCERTS", "LIVE_MUSIC"), "Concert & Âm nhạc");
        }
        if (matchesAny(lower, "thể thao", "sport", "bóng đá", "marathon", "chạy")) {
            return buildCategoryResponse(List.of("SPORTS"), "Thể thao");
        }
        if (matchesAny(lower, "kịch", "nghệ thuật", "arts", "triển lãm")) {
            return buildCategoryResponse(List.of("ARTS"), "Nghệ thuật & Sân khấu");
        }
        if (matchesAny(lower, "workshop", "hội thảo", "lớp học")) {
            return buildCategoryResponse(List.of("WORKSHOP"), "Workshop & Hội thảo");
        }

        // 5. Câu hỏi về chính sách, hàng đợi ảo, thanh toán
        if (matchesAny(lower, "hàng đợi", "queue", "xếp hàng", "waiting room", "chờ")) {
            return ChatResponse.builder()
                    .reply("🚦 **Hàng đợi ảo (Virtual Queue) tại TicketRush:**\n\n" +
                            "Đối với các sự kiện Flash Sale cực 'hot', hệ thống sẽ tự động kích hoạt hàng đợi ảo bằng công nghệ Redis ZSET.\n" +
                            "1. Khi mở bán, bạn sẽ nhận được **Số thứ tự xếp hàng (FIFO)** công bằng.\n" +
                            "2. Hệ thống phân bổ theo từng đợt (batch) vào chọn ghế, chống tình trạng sập server.\n" +
                            "3. Khi đến lượt, hệ thống sẽ cấp mã Access Token và tự động chuyển bạn vào bản đồ chọn ghế!")
                    .suggestions(List.of("Thời gian giữ ghế là bao lâu?", "Các sự kiện đang mở bán", "Chính sách hủy vé"))
                    .build();
        }

        if (matchesAny(lower, "giữ ghế", "thời gian", "khóa ghế", "lock", "bao lâu")) {
            return ChatResponse.builder()
                    .reply("⏱️ **Thời gian giữ ghế:**\n\n" +
                            "Khi bạn chọn ghế thành công, hệ thống sẽ **khóa tạm thời ghế trong vòng 10 phút** để bạn hoàn tất thanh toán. " +
                            "Sau 10 phút nếu bạn chưa xác nhận đơn hàng, ghế sẽ tự động được giải phóng để nhường cho người mua khác.")
                    .suggestions(List.of("Phương thức thanh toán", "Xem vé đã mua", "Tìm sự kiện hot"))
                    .build();
        }

        if (matchesAny(lower, "thanh toán", "mua vé", "hướng dẫn", "cách mua")) {
            return ChatResponse.builder()
                    .reply("💳 **Quy trình mua vé tại TicketRush:**\n\n" +
                            "1. Chọn sự kiện bạn yêu thích.\n" +
                            "2. Vào bản đồ ghế Real-time và click chọn vị trí mong muốn.\n" +
                            "3. Kiểm tra thông tin đơn hàng và xác nhận thanh toán trong 10 phút.\n" +
                            "4. Vé điện tử cùng **Mã QR Check-in** sẽ ngay lập tức xuất hiện trong mục **'Vé của tôi'** và gửi về email của bạn!")
                    .suggestions(List.of("Xem sự kiện Hot hôm nay", "Kiểm tra vé của tôi", "Có những sự kiện nào ở TP.HCM?"))
                    .build();
        }

        // 6. Tìm kiếm theo từ khóa tự do trong tên / nghệ sĩ
        List<Event> searched = findRelevantEvents(lower);
        if (!searched.isEmpty()) {
            return ChatResponse.builder()
                    .reply(String.format("🎉 Tôi tìm thấy **%d** sự kiện phù hợp với yêu cầu của bạn:", searched.size()))
                    .events(searched.stream().map(this::toCardDto).limit(4).toList())
                    .suggestions(List.of("Xem chi tiết giá vé", "Concert tại Hà Nội", "Concert tại TP.HCM"))
                    .build();
        }

        // 7. Câu trả lời mặc định nếu không khớp
        List<Event> hotEvents = getFeaturedEvents();
        return ChatResponse.builder()
                .reply("Cảm ơn bạn đã hỏi! Tôi có thể giúp bạn tìm kiếm vé hòa nhạc, thể thao, workshop hoặc giải đáp cách thức xếp hàng và đặt vé. " +
                        "Dưới đây là một số sự kiện nổi bật nhất đang diễn ra:")
                .events(hotEvents.stream().map(this::toCardDto).limit(3).toList())
                .suggestions(List.of("Concert tại TP.HCM", "Concert tại Hà Nội", "Sự kiện Thể thao", "Hàng đợi ảo hoạt động ra sao?"))
                .build();
    }

    private ChatResponse buildWelcomeResponse() {
        List<Event> hot = getFeaturedEvents();
        return ChatResponse.builder()
                .reply("👋 **Xin chào! Tôi là Trợ lý Ảo TicketRush.**\n\n" +
                        "Tôi có thể hỗ trợ bạn tìm kiếm sự kiện, kiểm tra tình trạng ghế trống, giá vé theo khu vực và hướng dẫn đặt chỗ nhanh chóng. Bạn đang quan tâm đến sự kiện hay nghệ sĩ nào hôm nay?")
                .events(hot.stream().map(this::toCardDto).limit(3).toList())
                .suggestions(List.of("Sự kiện Hot nhất 🔥", "Concert tại TP.HCM 🎤", "Concert tại Hà Nội 🏛️", "Sự kiện Thể thao ⚽"))
                .build();
    }

    private ChatResponse buildCityEventsResponse(String cityCode, String cityName) {
        List<Event> list = eventRepository.findAll().stream()
                .filter(e -> e.getCity() != null && e.getCity().equalsIgnoreCase(cityCode))
                .filter(e -> e.getStatus() == EventStatus.ON_SALE || e.getStatus() == EventStatus.PUBLISHED)
                .limit(4)
                .toList();

        if (list.isEmpty()) {
            return ChatResponse.builder()
                    .reply(String.format("Hiện tại chưa có sự kiện nào đang mở bán tại **%s**. Bạn hãy thử xem các thành phố khác nhé!", cityName))
                    .suggestions(List.of("Sự kiện tại TP.HCM", "Sự kiện tại Hà Nội", "Xem tất cả sự kiện"))
                    .build();
        }

        return ChatResponse.builder()
                .reply(String.format("🌟 Các sự kiện nổi bật đang diễn ra tại **%s**:", cityName))
                .events(list.stream().map(this::toCardDto).toList())
                .suggestions(List.of("Xem ghế trống sự kiện đầu tiên", "Concert tại TP.HCM", "Sự kiện Thể thao"))
                .build();
    }

    private ChatResponse buildCategoryResponse(List<String> categories, String categoryName) {
        List<Event> list = eventRepository.findAll().stream()
                .filter(e -> e.getCategory() != null && categories.contains(e.getCategory().toUpperCase()))
                .filter(e -> e.getStatus() == EventStatus.ON_SALE || e.getStatus() == EventStatus.PUBLISHED)
                .limit(4)
                .toList();

        return ChatResponse.builder()
                .reply(String.format("🎵 Danh sách các sự kiện thuộc danh mục **%s** đang mở bán:", categoryName))
                .events(list.stream().map(this::toCardDto).toList())
                .suggestions(List.of("Xem giá vé chi tiết", "Sự kiện tại Hà Nội", "Sự kiện tại TP.HCM"))
                .build();
    }

    private List<Event> findRelevantEvents(String query) {
        List<Event> all = eventRepository.findAll();
        String q = query.toLowerCase();

        return all.stream()
                .filter(e -> e.getName().toLowerCase().contains(q) ||
                        (e.getVenue() != null && e.getVenue().toLowerCase().contains(q)) ||
                        (e.getCategory() != null && e.getCategory().toLowerCase().contains(q)) ||
                        (e.getDescription() != null && e.getDescription().toLowerCase().contains(q)))
                .limit(5)
                .toList();
    }

    private List<Event> getFeaturedEvents() {
        return eventRepository.findAll().stream()
                .filter(Event::isHot)
                .limit(4)
                .toList();
    }

    private ChatResponse.EventCardDto toCardDto(Event e) {
        List<Zone> zones = zoneRepository.findByEventIdOrderBySortOrder(e.getId());
        Double minPrice = zones.isEmpty() ? null : zones.stream().mapToDouble(Zone::getPrice).min().orElse(0.0);

        return ChatResponse.EventCardDto.builder()
                .id(e.getId())
                .name(e.getName())
                .venue(e.getVenue())
                .city(e.getCity())
                .eventDate(e.getEventDate() != null ? e.getEventDate().format(DATE_FORMATTER) : "")
                .bannerUrl(e.getBannerUrl())
                .minPrice(minPrice)
                .status(e.getStatus() != null ? e.getStatus().name() : "")
                .isHot(e.isHot())
                .build();
    }

    private boolean matchesAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }
}
