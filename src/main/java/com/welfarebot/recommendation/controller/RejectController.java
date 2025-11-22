package com.welfarebot.recommendation.controller;

import com.welfarebot.recommendation.dto.RejectRequest;
import com.welfarebot.recommendation.service.RejectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
@Tag(name = "Reject API", description = "추천 제외 처리 API")
public class RejectController {

    private final RejectService rejectService;

    @PostMapping("/{userId}/reject-benefit")
    @Operation(summary = "특정 복지 혜택 거절 처리",
            description = "userId와 benefitId를 받아 이후 추천 결과에서 해당 혜택을 제외합니다.")
    public void reject(@PathVariable Long userId, @RequestBody RejectRequest request) {
        rejectService.reject(userId, request.getBenefitId());
    }
}
