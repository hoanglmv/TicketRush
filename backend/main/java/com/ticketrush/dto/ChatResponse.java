package com.ticketrush.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {
    private String reply;
    private List<EventCardDto> events;
    private List<String> suggestions;
    private String actionType;
    private Long targetEventId;
    private String logId;
    private Long latencyMs;
    private String modelUsed;
    private Double ragScore;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventCardDto {
        private Long id;
        private String name;
        private String venue;
        private String city;
        private String eventDate;
        private String bannerUrl;
        private Double minPrice;
        private String status;
        private boolean isHot;
    }
}
