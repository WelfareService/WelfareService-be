package com.welfarebot.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User {

    public static final String DEFAULT_WEIGHT_JSON = """
            {
              "categoryBoost": {
                "주거": 1.0,
                "일자리": 1.0,
                "생계": 1.0,
                "심리": 1.0,
                "건강": 1.0,
                "금융": 1.0,
                "지역": 1.0
              },
              "signalBoost": {
                "주거불안": 1.0,
                "미취업": 1.0,
                "저소득": 1.0,
                "심리위험": 1.0
              }
            }
            """;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private Integer age;

    private String residence;

    @Column(name = "weight_json", columnDefinition = "json")
    private String weightJson;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (weightJson == null || weightJson.isBlank()) {
            weightJson = DEFAULT_WEIGHT_JSON;
        }
    }
}
