package com.welfarebot.recommendation.dto;

import java.util.List;
import lombok.Data;

@Data
public class ChatRequest {
    private Long userId;
    private String message;
    private List<ConversationTurn> history;
}
