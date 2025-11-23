package com.welfarebot.recommendation.dto;

import java.util.List;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserResponse {
    private Long id;
    private String name;
    private Integer age;
    private String residence;
    private List<String> baseTags;
    private Boolean recommendationIssued;
    private LocalDateTime lastRecommendationAt;
}
