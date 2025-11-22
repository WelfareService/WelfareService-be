package com.welfarebot.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class GptMatchResponse {

    private List<String> signals = Collections.emptyList();

    @JsonAlias({"recommendations"})
    private List<GptMatchScore> scores = Collections.emptyList();

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GptMatchScore {

        @JsonProperty("benefit_id")
        @JsonAlias({"benefitId"})
        private String benefitId;

        private double score;

        private String reason;
    }
}
