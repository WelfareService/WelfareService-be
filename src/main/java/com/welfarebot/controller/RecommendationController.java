package com.welfarebot.controller;

import com.welfarebot.dto.RecommendationChatRequest;
import com.welfarebot.dto.RecommendationResponse;
import com.welfarebot.service.RecommendationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    @PostMapping("/chat")
    public RecommendationResponse chat(@Valid @RequestBody RecommendationChatRequest request) {
        return recommendationService.handleChat(request);
    }
}
