package com.welfarebot.recommendation.controller;

import com.welfarebot.recommendation.dto.ChatRequest;
import com.welfarebot.recommendation.dto.ChatResponse;
import com.welfarebot.recommendation.model.User;
import com.welfarebot.recommendation.service.RecommendationService;
import com.welfarebot.recommendation.service.RecommendationSessionTracker;
import com.welfarebot.recommendation.service.RecommendationSessionTracker.RecommendationSessionState;
import com.welfarebot.recommendation.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
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
    private final RecommendationSessionTracker sessionTracker;
    private final UserService userService;

    @PostMapping("/chat")
    @Operation(
            summary = "챗 기반 복지 추천",
            description = "GPT 신호 분석과 백엔드 minimal condition (신호·정보·pre-pool·추천 이력)을 통과한 경우에만 TOP3 추천을 생성합니다.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ChatRequest.class),
                    examples = @ExampleObject(name = "chat",
                            value = "{\n  \"userId\": 1,\n  \"message\": \"월세도 밀리고 요즘 일자리도 통 안 풀려요\",\n  \"history\": [\n    {\"role\": \"user\", \"message\": \"요즘 생활비가 너무 빠듯해요\"},\n    {\"role\": \"assistant\", \"message\": \"걱정이 많으시겠어요...\"}\n  ]\n}"))),
            responses = @ApiResponse(responseCode = "200", description = "추천 성공 또는 MC 차단 시에도 assistantMessage와 riskLevel을 반환합니다.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ChatResponse.class)))
    )
    public ChatResponse chat(@Valid @RequestBody ChatRequest req, HttpSession session) {
        User user = userService.find(req.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        RecommendationSessionState state = sessionTracker.read(session);
        RecommendationService.RecommendationResult result = recommendationService.chatRecommend(req, user, state);
        if (result.recommendationIssued()) {
            sessionTracker.markIssued(session, result.issuedAt());
        }
        return result.response();
    }
}
