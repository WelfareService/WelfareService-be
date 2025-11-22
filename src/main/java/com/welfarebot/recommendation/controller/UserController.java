package com.welfarebot.recommendation.controller;

import com.welfarebot.recommendation.dto.UserLoginRequest;
import com.welfarebot.recommendation.dto.UserRegisterRequest;
import com.welfarebot.recommendation.dto.UserResponse;
import com.welfarebot.recommendation.model.User;
import com.welfarebot.recommendation.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
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
    @Operation(summary = "사용자 등록", description = "baseTags를 포함한 사용자 기본 정보를 등록합니다.")
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
                .build();
    }

    @PostMapping("/login")
    @Operation(summary = "사용자 로그인", description = "이름 기반 MVP 로그인 API")
    public ResponseEntity<UserResponse> login(@RequestBody UserLoginRequest request) {
        return userService.loginByName(request.getName())
                .map(user -> ResponseEntity.ok(UserResponse.builder()
                        .id(user.getId())
                        .name(user.getName())
                        .age(user.getAge())
                        .residence(user.getResidence())
                        .baseTags(userService.parseTags(user))
                        .build()))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}")
    @Operation(summary = "사용자 정보 조회", description = "사용자 프로필과 baseTags를 반환합니다.")
    public ResponseEntity<UserResponse> find(@PathVariable("id") Long id) {
        return userService.find(id)
                .map(user -> ResponseEntity.ok(UserResponse.builder()
                        .id(user.getId())
                        .name(user.getName())
                        .age(user.getAge())
                        .residence(user.getResidence())
                        .baseTags(userService.parseTags(user))
                        .build()))
                .orElse(ResponseEntity.notFound().build());
    }
}
