package com.welfarebot.recommendation.controller;

import com.welfarebot.recommendation.dto.FollowupRequest;
import com.welfarebot.recommendation.dto.FollowupResponse;
import com.welfarebot.recommendation.service.DeepQuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/recommendations")
public class FollowupController {

    private final DeepQuestionService deepQuestionService;

    @PostMapping("/followup")
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
