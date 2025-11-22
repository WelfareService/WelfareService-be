package com.welfarebot.recommendation.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "benefit_match_logs")
public class BenefitMatchLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Column(length = 50)
    private String benefitId;

    private Double baseScore;

    private Double boostedScore;

    @Column(columnDefinition = "TEXT")
    private String appliedBoostTags;

    @Column(columnDefinition = "TEXT")
    private String appliedSignals;

    private LocalDateTime createdAt;
}
