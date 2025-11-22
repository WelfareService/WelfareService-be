package com.welfarebot.recommendation.repository;

import com.welfarebot.recommendation.model.BenefitMatchLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BenefitMatchLogRepository extends JpaRepository<BenefitMatchLog, Long> {

    List<BenefitMatchLog> findByUserIdOrderByCreatedAtDesc(Long userId);
}
