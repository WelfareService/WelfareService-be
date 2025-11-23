package com.welfarebot.recommendation.dto;

import lombok.Data;

@Data
public class ConversationTurn {
    private String role;
    private String message;
}
