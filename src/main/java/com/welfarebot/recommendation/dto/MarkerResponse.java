package com.welfarebot.recommendation.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MarkerResponse {
    private List<Marker> markers;

    @Data
    @Builder
    public static class Marker {
        private String id;
        private String title;
        private Double lat;
        private Double lng;
    }
}
