package com.welfarebot.recommendation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.welfarebot.recommendation.model.User;
import com.welfarebot.recommendation.repository.UserRepository;
import java.time.LocalDateTime;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final PreRecommendationService preRecommendationService;

    public User register(User user, List<String> baseTags) {
        List<String> tags = baseTags != null ? baseTags : List.of();
        try {
            user.setBaseTags(objectMapper.writeValueAsString(tags));
        } catch (Exception e) {
            user.setBaseTags("[]");
        }
        user.setCreatedAt(LocalDateTime.now());
        User saved = userRepository.save(user);
        preRecommendationService.createInitialPool(saved, tags);
        return saved;
    }

    public Optional<User> find(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return userRepository.findById(id);
    }

    public Optional<User> loginByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByName(name);
    }

    public List<String> parseTags(User user) {
        if (user == null || user.getBaseTags() == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(user.getBaseTags(), new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
