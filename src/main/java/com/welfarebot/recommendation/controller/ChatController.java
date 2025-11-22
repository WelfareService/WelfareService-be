package com.welfarebot.recommendation.controller;

import com.welfarebot.recommendation.dto.ChatRequest;
import com.welfarebot.recommendation.dto.ChatResponse;
import com.welfarebot.recommendation.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/recommendations")
public class ChatController {

    private final RecommendationService recommendationService;

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest req) {
        return recommendationService.chatRecommend(req.getUserId(), req.getMessage());
    }
}
