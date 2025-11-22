package com.welfarebot.recommendation.model;

import lombok.Data;

@Data
public class Benefit {
    private String benefit_id;
    private String title;
    private String category;
    private String provider;
    private String summary;
    private Eligibility eligibility;
    private Location location;

    @Data
    public static class Eligibility {
        private Integer age_min;
        private Integer age_max;
    }

    @Data
    public static class Location {
        private Double lat;
        private Double lng;
        private Integer radius_m;
        private String address_text;
    }
}
