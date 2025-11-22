package com.welfarebot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class BenefitLocation {

    private Double lat;

    private Double lng;

    @JsonProperty("radius_m")
    private Integer radiusMeters;

    private String address;
}
