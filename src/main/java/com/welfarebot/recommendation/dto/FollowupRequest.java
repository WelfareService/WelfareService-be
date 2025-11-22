package com.welfarebot.recommendation.dto;

import java.util.List;
import lombok.Data;

@Data
public class FollowupRequest {
    private String conversationHistory;
    private List<String> signals;
    private String lastUserMessage;
    private String lastAssistantMessage;
}
