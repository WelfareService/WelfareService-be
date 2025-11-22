package com.welfarebot.recommendation.repository;

import com.welfarebot.recommendation.model.UserPreRecommendation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPreRecommendationRepository extends JpaRepository<UserPreRecommendation, Long> {

    List<UserPreRecommendation> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
