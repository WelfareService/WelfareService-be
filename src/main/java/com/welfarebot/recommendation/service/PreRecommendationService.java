package com.welfarebot.recommendation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.welfarebot.recommendation.model.Benefit;
import com.welfarebot.recommendation.model.User;
import com.welfarebot.recommendation.model.UserPreRecommendation;
import com.welfarebot.recommendation.repository.UserPreRecommendationRepository;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PreRecommendationService {

    private static final int POOL_SIZE = 15;

    private final BenefitCatalogService benefitCatalogService;
    private final UserPreRecommendationRepository userPreRecommendationRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void createInitialPool(User user, List<String> baseTags) {
        if (user == null) {
            return;
        }

        userPreRecommendationRepository.deleteByUserId(user.getId());

        List<UserPreRecommendation> recommendations = buildPoolEntities(user, baseTags != null ? baseTags : Collections.emptyList());

        userPreRecommendationRepository.saveAll(recommendations);
    }

    @Transactional(readOnly = true)
    public List<UserPreRecommendation> getPool(User user) {
        if (user == null) {
            return buildPoolEntities(null, Collections.emptyList());
        }
        List<UserPreRecommendation> pool = userPreRecommendationRepository.findByUserId(user.getId());
        if (pool.isEmpty()) {
            List<String> tags = parseTags(user.getBaseTags());
            createInitialPool(user, tags);
            return userPreRecommendationRepository.findByUserId(user.getId());
        }
        return pool;
    }

    private List<UserPreRecommendation> buildPoolEntities(User user, List<String> baseTags) {
        List<String> tags = baseTags != null ? baseTags : Collections.emptyList();
        return benefitCatalogService.getAll().stream()
                .filter(benefit -> benefit.getBenefit_id() != null)
                .map(benefit -> buildPreRecommendation(user, benefit, tags))
                .sorted(Comparator.comparing(UserPreRecommendation::getBaseScore).reversed())
                .limit(POOL_SIZE)
                .collect(Collectors.toList());
    }

    private UserPreRecommendation buildPreRecommendation(User user, Benefit benefit, List<String> baseTags) {
        double score = 0.5;
        String category = benefit.getCategory() != null ? benefit.getCategory().toLowerCase(Locale.ROOT) : "";

        if (baseTags.contains("미취업") && category.contains("일자리")) {
            score += 0.20;
        }
        if (baseTags.contains("저소득")
                && (category.contains("주거")
                || category.contains("생계")
                || category.contains("지역자원"))) {
            score += 0.20;
        }
        if (baseTags.contains("심리위험")
                && (category.contains("심리") || category.contains("건강"))) {
            score += 0.20;
        }

        UserPreRecommendation entity = new UserPreRecommendation();
        entity.setUser(user);
        entity.setBenefitId(benefit.getBenefit_id());
        entity.setBaseScore(Math.min(score, 1.0));
        entity.setCreatedAt(LocalDateTime.now());
        return entity;
    }

    private List<String> parseTags(String tagsJson) {
        if (tagsJson == null) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(tagsJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
