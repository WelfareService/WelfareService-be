package com.welfarebot.recommendation.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RecommendationPolicyLoader {

    private final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());

    @Value("classpath:recommendation-policy.yml")
    private Resource policyResource;

    @Getter
    private RecommendationPolicy policy;

    @PostConstruct
    public void loadPolicy() {
        try {
            RecommendationPolicy raw = objectMapper.readValue(policyResource.getInputStream(), RecommendationPolicy.class);
            this.policy = normalize(raw);
            log.info("[Policy] recommendation-policy.yml loaded (baseTagRules={}, signalRules={})",
                    policy.getBaseTagBoost().size(), policy.getSignalBoost().size());
        } catch (IOException e) {
            throw new IllegalStateException("recommendation-policy.yml 로딩 실패", e);
        }
    }

    private RecommendationPolicy normalize(RecommendationPolicy raw) {
        RecommendationPolicy normalized = new RecommendationPolicy();
        normalized.setBoostCap(raw.getBoostCap() != null ? raw.getBoostCap() : 0.2);

        normalized.setReRecommendTriggers(raw.getReRecommendTriggers().stream()
                .filter(trigger -> trigger != null && !trigger.isBlank())
                .map(this::normalizeText)
                .collect(Collectors.toList()));

        normalized.setCategoryKeywords(raw.getCategoryKeywords().entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> normalizeText(entry.getKey()),
                        entry -> entry.getValue().stream()
                                .filter(keyword -> keyword != null && !keyword.isBlank())
                                .map(this::normalizeText)
                                .collect(Collectors.toList()),
                        (a, b) -> a,
                        HashMap::new
                )));

        normalized.setBaseTagBoost(normalizeRules(raw.getBaseTagBoost()));
        normalized.setSignalBoost(normalizeRules(raw.getSignalBoost()));
        return normalized;
    }

    private Map<String, BoostRule> normalizeRules(Map<String, BoostRule> source) {
        if (source == null) {
            return Map.of();
        }
        Map<String, BoostRule> normalized = new HashMap<>();
        source.forEach((key, rule) -> {
            if (key == null || rule == null) {
                return;
            }
            BoostRule normalizedRule = new BoostRule();
            normalizedRule.setBoost(rule.getBoost());
            normalizedRule.setCategories(rule.getCategories() != null
                    ? rule.getCategories().stream()
                    .filter(category -> category != null && !category.isBlank())
                    .map(this::normalizeText)
                    .collect(Collectors.toList())
                    : new ArrayList<>());
            normalized.put(normalizeText(key), normalizedRule);
        });
        return normalized;
    }

    public List<String> getReRecommendTriggers() {
        return policy.getReRecommendTriggers();
    }

    public double getBoostCap() {
        return policy.getBoostCap() != null ? policy.getBoostCap() : 0.2;
    }

    public Map<String, BoostRule> getBaseTagBoostRules() {
        return policy.getBaseTagBoost();
    }

    public Map<String, BoostRule> getSignalBoostRules() {
        return policy.getSignalBoost();
    }

    public Set<String> resolveCategories(String benefitCategory) {
        if (benefitCategory == null || benefitCategory.isBlank()) {
            return Set.of();
        }
        String normalizedCategory = normalizeText(benefitCategory);
        return policy.getCategoryKeywords().entrySet().stream()
                .filter(entry -> containsKeyword(normalizedCategory, entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private boolean containsKeyword(String normalizedCategory, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return false;
        }
        return keywords.stream().anyMatch(normalizedCategory::contains);
    }

    private String normalizeText(String input) {
        return input == null ? "" : input.toLowerCase(Locale.ROOT).trim();
    }

    @Getter
    public static class RecommendationPolicy {
        private Map<String, BoostRule> baseTagBoost = new HashMap<>();
        private Map<String, BoostRule> signalBoost = new HashMap<>();
        private Map<String, List<String>> categoryKeywords = new HashMap<>();
        private List<String> reRecommendTriggers = new ArrayList<>();
        private Double boostCap = 0.2;

        public void setBaseTagBoost(Map<String, BoostRule> baseTagBoost) {
            this.baseTagBoost = baseTagBoost != null ? baseTagBoost : new HashMap<>();
        }

        public void setSignalBoost(Map<String, BoostRule> signalBoost) {
            this.signalBoost = signalBoost != null ? signalBoost : new HashMap<>();
        }

        public void setCategoryKeywords(Map<String, List<String>> categoryKeywords) {
            this.categoryKeywords = categoryKeywords != null ? categoryKeywords : new HashMap<>();
        }

        public void setReRecommendTriggers(List<String> reRecommendTriggers) {
            this.reRecommendTriggers = reRecommendTriggers != null ? reRecommendTriggers : new ArrayList<>();
        }

        public void setBoostCap(Double boostCap) {
            this.boostCap = boostCap;
        }
    }

    @Getter
    public static class BoostRule {
        private List<String> categories = new ArrayList<>();
        private double boost;

        public void setCategories(List<String> categories) {
            this.categories = categories != null ? categories : new ArrayList<>();
        }

        public void setBoost(double boost) {
            this.boost = boost;
        }
    }
}
