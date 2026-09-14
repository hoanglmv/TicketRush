package com.ticketrush.service.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class QdrantVectorService {

    @Value("${app.rag.qdrant-url:http://localhost:6333}")
    private String qdrantUrl;

    @Value("${app.rag.collection-name:ticketrush_knowledge}")
    private String collectionName;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory fallback point storage if Qdrant container is not yet ready
    private final Map<String, LocalPoint> localPoints = new ConcurrentHashMap<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoredChunk {
        private String id;
        private double score;
        private String text;
        private String category;
        private String source;
    }

    @Data
    @AllArgsConstructor
    private static class LocalPoint {
        String id;
        List<Double> vector;
        Map<String, Object> payload;
    }

    /**
     * Đảm bảo Collection tồn tại trong Qdrant.
     */
    public boolean ensureCollectionExists() {
        try {
            RestClient client = buildRestClient();
            // 1. Kiểm tra xem collection đã có chưa
            try {
                String checkRes = client.get()
                        .uri("/collections/" + collectionName)
                        .retrieve()
                        .body(String.class);
                if (checkRes != null && checkRes.contains("\"status\":\"ok\"")) {
                    return true;
                }
            } catch (Exception ignored) {}

            // 2. Tạo collection mới với Cosine Distance
            Map<String, Object> createReq = Map.of(
                    "vectors", Map.of(
                            "size", EmbeddingService.VECTOR_DIMENSION,
                            "distance", "Cosine"
                    )
            );

            client.put()
                    .uri("/collections/" + collectionName)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(createReq)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Successfully created Qdrant collection: {}", collectionName);
            return true;
        } catch (Exception e) {
            log.warn("Qdrant not reachable at {} ({}), using In-Memory Vector Store", qdrantUrl, e.getMessage());
            return false;
        }
    }

    /**
     * Lưu vector và metadata vào Qdrant (đồng thời lưu vào local fallback).
     */
    public void upsertPoint(String pointId, List<Double> vector, Map<String, Object> payload) {
        localPoints.put(pointId, new LocalPoint(pointId, vector, payload));

        try {
            RestClient client = buildRestClient();
            Map<String, Object> pointData = Map.of(
                    "id", Math.abs(pointId.hashCode()),
                    "vector", vector,
                    "payload", payload
            );

            Map<String, Object> batchReq = Map.of(
                    "points", List.of(pointData)
            );

            client.put()
                    .uri("/collections/" + collectionName + "/points?wait=true")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(batchReq)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.debug("Upsert to Qdrant failed for id {}, stored in local fallback: {}", pointId, e.getMessage());
        }
    }

    /**
     * Tìm kiếm Top-K chunks có độ tương đồng Cosine cao nhất với vector truy vấn.
     */
    public List<ScoredChunk> searchSimilar(List<Double> queryVector, int limit) {
        // 1. Thử tìm kiếm trên Qdrant
        try {
            RestClient client = buildRestClient();
            Map<String, Object> searchReq = Map.of(
                    "vector", queryVector,
                    "limit", limit,
                    "with_payload", true
            );

            String response = client.post()
                    .uri("/collections/" + collectionName + "/points/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(searchReq)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            JsonNode results = root.path("result");

            if (results.isArray() && !results.isEmpty()) {
                List<ScoredChunk> chunks = new ArrayList<>();
                for (JsonNode item : results) {
                    double score = item.path("score").asDouble();
                    JsonNode payload = item.path("payload");
                    chunks.add(ScoredChunk.builder()
                            .id(item.path("id").asText())
                            .score(score)
                            .text(payload.path("text").asText())
                            .category(payload.path("category").asText())
                            .source(payload.path("source").asText())
                            .build());
                }
                return chunks;
            }
        } catch (Exception e) {
            log.debug("Qdrant search failed ({}), calculating on In-Memory Store", e.getMessage());
        }

        // 2. Fallback: Tính Cosine Similarity trên In-Memory Store
        return searchInMemory(queryVector, limit);
    }

    private List<ScoredChunk> searchInMemory(List<Double> queryVector, int limit) {
        List<ScoredChunk> list = new ArrayList<>();

        for (LocalPoint pt : localPoints.values()) {
            double score = cosineSimilarity(queryVector, pt.vector);
            list.add(ScoredChunk.builder()
                    .id(pt.id)
                    .score(score)
                    .text(String.valueOf(pt.payload.get("text")))
                    .category(String.valueOf(pt.payload.get("category")))
                    .source(String.valueOf(pt.payload.get("source")))
                    .build());
        }

        list.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        return list.stream().limit(limit).toList();
    }

    private double cosineSimilarity(List<Double> v1, List<Double> v2) {
        if (v1 == null || v2 == null || v1.size() != v2.size()) return 0.0;
        double dot = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        for (int i = 0; i < v1.size(); i++) {
            double a = v1.get(i);
            double b = v2.get(i);
            dot += a * b;
            norm1 += a * a;
            norm2 += b * b;
        }
        if (norm1 == 0 || norm2 == 0) return 0.0;
        return dot / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    private RestClient buildRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2500);
        factory.setReadTimeout(4000);

        return RestClient.builder()
                .requestFactory(factory)
                .baseUrl(qdrantUrl)
                .build();
    }
}
