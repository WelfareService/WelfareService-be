package com.welfarebot.recommendation.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private Long userId;
    private String message;
}
