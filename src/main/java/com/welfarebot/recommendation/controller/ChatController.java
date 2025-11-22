package com.welfarebot.recommendation.controller;

import com.welfarebot.recommendation.dto.ChatRequest;
import com.welfarebot.recommendation.dto.ChatResponse;
import com.welfarebot.recommendation.service.RecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/recommendations")
@Tag(name = "Recommendation API", description = "GPT 기반 복지 추천 API")
public class ChatController {

    private final RecommendationService recommendationService;

    @PostMapping("/chat")
    @Operation(summary = "챗 기반 복지 추천", description = "사용자 메시지를 입력받아 GPT 신호 분석과 백엔드 점수 계산을 거친 추천 목록을 반환합니다.")
    public ChatResponse chat(@RequestBody ChatRequest req) {
        return recommendationService.chatRecommend(req.getUserId(), req.getMessage());
    }
}
