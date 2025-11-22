package com.welfarebot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RecommendationChatRequest(
        @NotNull(message = "user_id는 필수입니다.")
        Long userId,
        @NotBlank(message = "message는 필수입니다.")
        String message
) {
}
