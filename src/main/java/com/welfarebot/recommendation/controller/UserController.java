package com.welfarebot.recommendation.controller;

import com.welfarebot.recommendation.dto.UserRegisterRequest;
import com.welfarebot.recommendation.model.User;
import com.welfarebot.recommendation.service.UserService;
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
public class UserController {

    private final UserService userService;

    @PostMapping
    public User register(@RequestBody UserRegisterRequest request) {
        User user = new User();
        user.setName(request.getName());
        user.setAge(request.getAge());
        user.setResidence(request.getResidence());
        List<String> tags = request.getBaseTags() != null ? request.getBaseTags() : List.of();
        return userService.register(user, tags);
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> find(@PathVariable("id") Long id) {
        return userService.find(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
