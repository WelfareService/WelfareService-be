package com.welfarebot.recommendation.repository;

import com.welfarebot.recommendation.model.BenefitMatchLog;
import com.welfarebot.recommendation.model.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BenefitMatchLogRepository extends JpaRepository<BenefitMatchLog, Long> {

    List<BenefitMatchLog> findByUserOrderByCreatedAtDesc(User user);
}
