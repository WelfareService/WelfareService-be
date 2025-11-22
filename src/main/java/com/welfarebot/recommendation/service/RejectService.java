package com.welfarebot.recommendation.service;

import com.welfarebot.recommendation.model.UserRejectBenefit;
import com.welfarebot.recommendation.repository.UserRejectBenefitRepository;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RejectService {

    private final UserRejectBenefitRepository repository;

    public void reject(Long userId, String benefitId) {
        if (userId == null || benefitId == null || benefitId.isBlank()) {
            return;
        }
        if (repository.existsByUserIdAndBenefitId(userId, benefitId)) {
            return;
        }
        UserRejectBenefit entity = new UserRejectBenefit();
        entity.setUserId(userId);
        entity.setBenefitId(benefitId);
        entity.setCreatedAt(LocalDateTime.now());
        repository.save(entity);
    }

    public Set<String> getRejectedBenefitIds(Long userId) {
        if (userId == null) {
            return Collections.emptySet();
        }
        return repository.findByUserId(userId).stream()
                .map(UserRejectBenefit::getBenefitId)
                .collect(Collectors.toSet());
    }
}
