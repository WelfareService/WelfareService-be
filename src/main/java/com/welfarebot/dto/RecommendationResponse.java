package com.welfarebot.dto;

import java.util.List;

public record RecommendationResponse(
        List<RecommendationItem> recommendations
) {

    public record RecommendationItem(
            String benefitId,
            String title,
            String category,
            double score,
            String summary,
            Location location
    ) {
    }

    public record Location(
            Double lat,
            Double lng
    ) {
    }
}
