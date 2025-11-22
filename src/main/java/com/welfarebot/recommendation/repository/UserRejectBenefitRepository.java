package com.welfarebot.recommendation.repository;

import com.welfarebot.recommendation.model.UserRejectBenefit;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRejectBenefitRepository extends JpaRepository<UserRejectBenefit, Long> {

    List<UserRejectBenefit> findByUserId(Long userId);

    boolean existsByUserIdAndBenefitId(Long userId, String benefitId);
}
