package com.welfarebot.repository;

import com.welfarebot.model.BenefitMatchLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BenefitMatchLogRepository extends JpaRepository<BenefitMatchLog, Long> {
}
