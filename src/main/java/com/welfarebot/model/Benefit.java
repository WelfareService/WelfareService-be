package com.welfarebot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class Benefit {

    @JsonProperty("benefit_id")
    private String id;

    private String title;

    private String category;

    private String provider;

    private String summary;

    @JsonProperty("priority_score")
    private Double priorityScore;

    private List<BenefitLocation> locations = Collections.emptyList();

    @JsonProperty("location")
    public void setLocation(BenefitLocation location) {
        if (location == null) {
            this.locations = Collections.emptyList();
        } else {
            this.locations = List.of(location);
        }
    }

    public BenefitLocation primaryLocation() {
        return (locations == null || locations.isEmpty()) ? null : locations.get(0);
    }
}
