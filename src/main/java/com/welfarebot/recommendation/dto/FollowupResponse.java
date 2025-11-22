package com.welfarebot.recommendation.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FollowupResponse {
    private String followupQuestion;
}
