package com.welfarebot.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@Data
public class GptMatchResponse {
    private String assistantMessage;
    private List<String> signals;

    @JsonProperty("insufficient_info")
    private Boolean insufficientInfo;
}
