package com.welfarebot.recommendation.dto;

import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class GptMatchResponse {
    private String assistantMessage;
    private List<String> signals;
    private Map<String, Double> scores;
    private Boolean insufficientInfo;
}
