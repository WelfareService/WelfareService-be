package com.welfarebot.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@Data
public class MatchEngineResponse {
    private String assistantMessage;
    private List<String> signals;

    @JsonProperty("insufficient_info")
    private Boolean insufficientInfo;

    public boolean hasSignals() {
        return signals != null && !signals.isEmpty();
    }

    public boolean isInsufficientInfo() {
        return Boolean.TRUE.equals(insufficientInfo);
    }
}
