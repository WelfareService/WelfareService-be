package com.welfarebot.recommendation.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

    @Column(name = "user_id")
    private Long userId;

    @ManyToOne(optional = true)
    @JoinColumn(name = "user_id", nullable = true, insertable = false, updatable = false)
    private User user;

    @Column(length = 50)
    private String benefitId;

    private Double baseScore;

    private Double boostedScore;

    @Column(columnDefinition = "TEXT")
    private String appliedBoostTags;

    @Column(columnDefinition = "TEXT")
    private String appliedSignals;

    @Column(length = 30)
    private String decisionType;

    @Column(columnDefinition = "TEXT")
    private String mcFailReasons;

    @Column(columnDefinition = "TEXT")
    private String normalizedSignals;

    @Column(length = 20)
    private String riskLevel;

    private LocalDateTime createdAt;
}
