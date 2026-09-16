package app.service.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeIngestionService implements ApplicationRunner {

    private final EmbeddingService embeddingService;
    private final QdrantVectorService qdrantVectorService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void run(ApplicationArguments args) {
        log.info("Starting TicketRush RAG Knowledge Ingestion...");
        qdrantVectorService.ensureCollectionExists();

        int totalIngested = 0;
        totalIngested += ingestMarkdownDocument("classpath:knowledge/policies_and_faq.md", "POLICY");
        totalIngested += ingestMarkdownDocument("classpath:knowledge/venues_and_logistics.md", "VENUE");
        totalIngested += ingestEventsJson("classpath:knowledge/events_semantic_context.json");
        totalIngested += ingestCrawledEventsJson("classpath:knowledge/ticketbox_crawled_events.json");

        log.info("RAG Knowledge Ingestion complete! Total chunks indexed: {}", totalIngested);
    }

    private int ingestMarkdownDocument(String location, String category) {
        int count = 0;
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource resource = resolver.getResource(location);
            if (!resource.exists()) {
                log.warn("Knowledge document not found at {}", location);
                return 0;
            }

            String content;
            try (InputStream is = resource.getInputStream()) {
                content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }

            // Chia nhỏ văn bản theo các đề mục cấp 2 (##)
            String[] sections = content.split("\n(?=## )");
            for (int i = 0; i < sections.length; i++) {
                String section = sections[i].trim();
                if (section.length() < 30) continue;

                String chunkId = category.toLowerCase() + "_" + (i + 1);
                List<Double> vector = embeddingService.getEmbedding(section);

                Map<String, Object> payload = Map.of(
                        "text", section,
                        "category", category,
                        "source", resource.getFilename() != null ? resource.getFilename() : location
                );

                qdrantVectorService.upsertPoint(chunkId, vector, payload);
                count++;
            }
        } catch (Exception e) {
            log.error("Failed to ingest markdown {}: {}", location, e.getMessage());
        }
        return count;
    }

    private int ingestEventsJson(String location) {
        int count = 0;
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource resource = resolver.getResource(location);
            if (!resource.exists()) return 0;

            String jsonStr;
            try (InputStream is = resource.getInputStream()) {
                jsonStr = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }

            JsonNode root = objectMapper.readTree(jsonStr);
            if (root.isArray()) {
                for (JsonNode item : root) {
                    long eventId = item.path("eventId").asLong();
                    String name = item.path("name").asText();
                    String genre = item.path("genre").asText();
                    String vibe = item.path("vibe").asText();
                    String highlights = item.path("highlights").asText();
                    String target = item.path("targetAudience").asText();

                    String textChunk = String.format("Sự kiện: %s (Mã số: %d)\nThể loại: %s\nPhong cách & Vibe: %s\nĐặc sắc: %s\nKhán giả phù hợp: %s",
                            name, eventId, genre, vibe, highlights, target);

                    String chunkId = "event_" + eventId;
                    List<Double> vector = embeddingService.getEmbedding(textChunk);

                    Map<String, Object> payload = Map.of(
                            "text", textChunk,
                            "category", "EVENT_SEMANTIC",
                            "eventId", eventId,
                            "source", "events_semantic_context.json"
                    );

                    qdrantVectorService.upsertPoint(chunkId, vector, payload);
                    count++;
                }
            }
        } catch (Exception e) {
            log.error("Failed to ingest events json {}: {}", location, e.getMessage());
        }
        return count;
    }

    private int ingestCrawledEventsJson(String location) {
        int count = 0;
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource resource = resolver.getResource(location);
            if (!resource.exists()) return 0;

            String jsonStr;
            try (InputStream is = resource.getInputStream()) {
                jsonStr = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }

            JsonNode root = objectMapper.readTree(jsonStr);
            if (root.isArray()) {
                for (JsonNode item : root) {
                    String id = item.path("id").asText("event_" + System.currentTimeMillis());
                    String title = item.path("title").asText("");
                    String content = item.path("content").asText("");

                    String textChunk = String.format("Sự kiện: %s\n%s", title, content);
                    String chunkId = "crawled_" + id;
                    List<Double> vector = embeddingService.getEmbedding(textChunk);

                    Map<String, Object> payload = Map.of(
                            "text", textChunk,
                            "category", "CRAWLED_EVENT",
                            "title", title,
                            "source", "ticketbox_crawled_events.json"
                    );

                    qdrantVectorService.upsertPoint(chunkId, vector, payload);
                    count++;
                }
            }
        } catch (Exception e) {
            log.warn("Crawled events knowledge ingestion skipped: {}", e.getMessage());
        }
        return count;
    }
}
