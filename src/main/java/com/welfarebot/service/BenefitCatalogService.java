package com.welfarebot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.welfarebot.model.Benefit;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BenefitCatalogService {

    private static final String BENEFIT_RESOURCE = "benefits.json";

    private final ObjectMapper objectMapper;

    private List<Benefit> benefits = Collections.emptyList();
    private Map<String, Benefit> benefitMap = Collections.emptyMap();

    @PostConstruct
    public void load() {
        Resource resource = new ClassPathResource(BENEFIT_RESOURCE);
        if (!resource.exists()) {
            log.error("benefits.json 파일을 찾을 수 없습니다.");
            return;
        }
        try (InputStream is = resource.getInputStream()) {
            Benefit[] parsed = objectMapper.readValue(is, Benefit[].class);
            this.benefits = Collections.unmodifiableList(Arrays.asList(parsed));
            this.benefitMap = benefits.stream()
                    .filter(b -> b.getId() != null)
                    .collect(Collectors.toMap(Benefit::getId, b -> b, (a, b) -> a, LinkedHashMap::new));
            log.info("benefits.json에서 {}개의 혜택을 로딩했습니다.", benefits.size());
        } catch (IOException e) {
            log.error("benefits.json 로딩 실패", e);
            this.benefits = Collections.emptyList();
            this.benefitMap = Collections.emptyMap();
        }
    }

    public List<Benefit> getAll() {
        return benefits;
    }

    public Map<String, Benefit> getBenefitMap() {
        return benefitMap;
    }

    public List<Benefit> findByIdsInOrder(List<String> benefitIds) {
        if (benefitIds == null || benefitIds.isEmpty()) {
            return List.of();
        }
        return benefitIds.stream()
                .map(benefitMap::get)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
    }
}
