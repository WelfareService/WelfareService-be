package com.welfarebot.recommendation.dto;

import lombok.Data;

@Data
public class RejectRequest {
    private Long userId;
    private String benefitId;
}
