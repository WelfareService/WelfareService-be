package com.welfarebot.recommendation.signal;

import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CanonicalMapper {

    private static final Map<String, String> KOR_TO_CANONICAL = Map.ofEntries(
            Map.entry("주거불안", "housing_stress"),
            Map.entry("생활비부담", "low_income"),
            Map.entry("저소득", "low_income"),
            Map.entry("생계곤란", "low_income"),
            Map.entry("생계위험", "low_income"),
            Map.entry("미취업", "unemployment"),
            Map.entry("부채", "debt_risk"),
            Map.entry("체납", "arrears"),
            Map.entry("건강위험", "health_risk"),
            Map.entry("의료위험", "medical_expense"),
            Map.entry("돌봄공백", "childcare_gap"),
            Map.entry("심리위험", "mental_health")
    );

    public String toCanonical(String korean) {
        if (korean == null) {
            return null;
        }
        return KOR_TO_CANONICAL.getOrDefault(korean.trim(), null);
    }
}
