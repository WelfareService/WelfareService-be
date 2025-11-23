package com.welfarebot.recommendation.controller;

import com.welfarebot.recommendation.dto.FollowupRequest;
import com.welfarebot.recommendation.dto.FollowupResponse;
import com.welfarebot.recommendation.service.DeepQuestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
    @Operation(
            summary = "심층 질문 생성",
            description = "GPT가 최근 대화와 신호를 기반으로 공감형 후속 질문을 1문장 생성합니다.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = FollowupRequest.class),
                    examples = @ExampleObject(value = "{\n  \"conversationHistory\": \"사용자: 월세가 밀려요...\",\n  \"signals\": [\"주거불안\"],\n  \"lastUserMessage\": \"요즘 잠도 못자요\",\n  \"lastAssistantMessage\": \"걱정 많으시겠어요...\"\n}"))),
            responses = @ApiResponse(responseCode = "200", description = "추가 질문 1문장", content = @Content(schema = @Schema(implementation = FollowupResponse.class)))
    )
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
