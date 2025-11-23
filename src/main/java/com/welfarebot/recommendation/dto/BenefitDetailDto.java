package com.welfarebot.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class BenefitDetailDto {

    @JsonProperty("benefit_id")
    private String benefitId;
    private String title;
    private String summary;
    private String description;
    private String category;
    private String website;
    private Location location;

    @Data
    public static class Location {
        private Double lat;
        private Double lng;
    }
}
