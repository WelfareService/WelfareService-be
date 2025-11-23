package com.welfarebot.recommendation.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class ChatRequest {
    @NotNull
    private Long userId;
    private String message;
    private List<ConversationTurn> history;
}
