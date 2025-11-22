package com.welfarebot.recommendation.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatResponse {
    private String assistantMessage;
    private List<RecommendationItem> recommendations;

    @Data
    @Builder
    public static class RecommendationItem {
        private String benefitId;
        private String title;
        private String category;
        private double score;
        private String summary;
        private Location location;

        @Data
        @Builder
        public static class Location {
            private Double lat;
            private Double lng;
        }
    }
}
