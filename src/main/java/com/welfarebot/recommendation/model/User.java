package com.welfarebot.recommendation.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Entity
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private Integer age;
    private String residence;

    @Column(columnDefinition = "JSON")
    private String baseTags;

    private Boolean recommendationIssued = Boolean.FALSE;

    private LocalDateTime lastRecommendationAt;

    private LocalDateTime createdAt;
}
