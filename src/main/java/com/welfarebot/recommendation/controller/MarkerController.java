package com.welfarebot.recommendation.controller;

import com.welfarebot.recommendation.dto.MarkerResponse;
import com.welfarebot.recommendation.service.BenefitCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/benefits")
@Tag(name = "Benefit Marker API", description = "카카오맵 마커용 혜택 위치 API")
public class MarkerController {

    private final BenefitCatalogService catalogService;

    @GetMapping("/locations")
    @Operation(summary = "전체 혜택 위치 조회",
            description = "위치 정보가 있는 혜택을 카카오맵 마커 형식으로 반환합니다.",
            responses = @ApiResponse(responseCode = "200", description = "마커 응답", content = @Content(schema = @Schema(implementation = MarkerResponse.class))))
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
