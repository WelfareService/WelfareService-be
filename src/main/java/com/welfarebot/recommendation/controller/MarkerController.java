package com.welfarebot.recommendation.controller;

import com.welfarebot.recommendation.dto.MarkerResponse;
import com.welfarebot.recommendation.service.BenefitCatalogService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/benefits")
public class MarkerController {

    private final BenefitCatalogService catalogService;

    @GetMapping("/locations")
    public MarkerResponse getLocations() {
        List<MarkerResponse.Marker> markers = catalogService.getAll().stream()
                .filter(b -> b.getLocation() != null)
                .filter(b -> b.getLocation().getLat() != null)
                .map(b -> MarkerResponse.Marker.builder()
                        .id(b.getBenefit_id())
                        .title(b.getTitle())
                        .lat(b.getLocation().getLat())
                        .lng(b.getLocation().getLng())
                        .build())
                .toList();

        return MarkerResponse.builder()
                .markers(markers)
                .build();
    }
}
