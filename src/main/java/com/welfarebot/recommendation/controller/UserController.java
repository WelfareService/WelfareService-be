package com.welfarebot.recommendation.controller;

import com.welfarebot.recommendation.dto.UserLoginRequest;
import com.welfarebot.recommendation.dto.UserRegisterRequest;
import com.welfarebot.recommendation.dto.UserResponse;
import com.welfarebot.recommendation.model.User;
import com.welfarebot.recommendation.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
@Tag(name = "User API", description = "사용자 등록/조회 API")
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    @Operation(
            summary = "사용자 등록",
            description = "사용자 기본 프로필과 baseTags를 저장하고 사전 추천 풀을 초기화합니다.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = UserRegisterRequest.class),
                    examples = @ExampleObject(value = "{\n  \"name\": \"민지\",\n  \"age\": 27,\n  \"residence\": \"대구 북구\",\n  \"baseTags\": [\"저소득\", \"미취업\"]\n}"))),
            responses = @ApiResponse(responseCode = "200", description = "등록된 사용자 정보", content = @Content(schema = @Schema(implementation = UserResponse.class)))
    )
    public UserResponse register(@RequestBody UserRegisterRequest request) {
        User user = new User();
        user.setName(request.getName());
        user.setAge(request.getAge());
        user.setResidence(request.getResidence());
        List<String> tags = request.getBaseTags() != null ? request.getBaseTags() : List.of();
        User saved = userService.register(user, tags);
        return UserResponse.builder()
                .id(saved.getId())
                .name(saved.getName())
                .age(saved.getAge())
                .residence(saved.getResidence())
                .baseTags(tags)
                .recommendationIssued(saved.getRecommendationIssued())
                .lastRecommendationAt(saved.getLastRecommendationAt())
                .build();
    }

    @PostMapping("/login")
    @Operation(
            summary = "사용자 로그인",
            description = "이름으로 간편 로그인하여 recommendationIssued 상태를 확인합니다.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = UserLoginRequest.class),
                    examples = @ExampleObject(value = "{\n  \"name\": \"민지\"\n}"))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "사용자 발견", content = @Content(schema = @Schema(implementation = UserResponse.class))),
                    @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
            }
    )
    public ResponseEntity<UserResponse> login(@RequestBody UserLoginRequest request) {
        return userService.loginByName(request.getName())
                .map(user -> ResponseEntity.ok(UserResponse.builder()
                        .id(user.getId())
                        .name(user.getName())
                        .age(user.getAge())
                        .residence(user.getResidence())
                        .baseTags(userService.parseTags(user))
                        .recommendationIssued(user.getRecommendationIssued())
                        .lastRecommendationAt(user.getLastRecommendationAt())
                        .build()))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}")
    @Operation(summary = "사용자 정보 조회", description = "recommendationIssued, lastRecommendationAt 포함 전체 프로필을 반환합니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공", content = @Content(schema = @Schema(implementation = UserResponse.class))),
                    @ApiResponse(responseCode = "404", description = "해당 ID 없음")
            })
    public ResponseEntity<UserResponse> find(@PathVariable("id") Long id) {
        return userService.find(id)
                .map(user -> ResponseEntity.ok(UserResponse.builder()
                        .id(user.getId())
                        .name(user.getName())
                        .age(user.getAge())
                        .residence(user.getResidence())
                        .baseTags(userService.parseTags(user))
                        .recommendationIssued(user.getRecommendationIssued())
                        .lastRecommendationAt(user.getLastRecommendationAt())
                        .build()))
                .orElse(ResponseEntity.notFound().build());
    }
}
