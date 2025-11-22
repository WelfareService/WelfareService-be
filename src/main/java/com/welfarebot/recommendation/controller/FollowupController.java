package com.welfarebot.recommendation.controller;

import com.welfarebot.recommendation.dto.FollowupRequest;
import com.welfarebot.recommendation.dto.FollowupResponse;
import com.welfarebot.recommendation.service.DeepQuestionService;
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
@Tag(name = "Follow-up API", description = "GPT 기반 후속 질문 API")
public class FollowupController {

    private final DeepQuestionService deepQuestionService;

    @PostMapping("/followup")
    @Operation(summary = "심층 질문 생성", description = "대화 요약과 신호를 기반으로 맞춤 후속 질문 한 문장을 생성합니다.")
    public FollowupResponse followup(@RequestBody FollowupRequest req) {
        String question = deepQuestionService.generateFollowup(
                req.getConversationHistory(),
                req.getSignals(),
                req.getLastUserMessage(),
                req.getLastAssistantMessage()
        );
        return FollowupResponse.builder()
                .followupQuestion(question)
                .build();
    }
}
