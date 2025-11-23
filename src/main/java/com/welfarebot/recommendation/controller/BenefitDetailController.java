package com.welfarebot.recommendation.controller;

import com.welfarebot.recommendation.dto.BenefitDetailDto;
import com.welfarebot.recommendation.service.BenefitDetailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/benefits")
public class BenefitDetailController {

    private final BenefitDetailService benefitDetailService;

    @GetMapping("/{benefitId}")
    public ResponseEntity<BenefitDetailDto> getBenefitDetail(@PathVariable("benefitId") String benefitId) {
        BenefitDetailDto dto = benefitDetailService.getDetail(benefitId);
        return ResponseEntity.ok(dto);
    }
}
