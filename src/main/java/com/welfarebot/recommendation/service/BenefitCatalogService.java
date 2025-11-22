package com.welfarebot.recommendation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.welfarebot.recommendation.model.Benefit;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BenefitCatalogService {

    private final ObjectMapper objectMapper;
    private List<Benefit> catalog = new ArrayList<>();
    private Map<String, Benefit> byId = new HashMap<>();

    @PostConstruct
    public void loadCatalog() {
        try {
            InputStream is = new ClassPathResource("benefits.json").getInputStream();
            catalog = objectMapper.readValue(is, new TypeReference<List<Benefit>>() {});
            byId = catalog.stream().collect(Collectors.toMap(Benefit::getBenefit_id, b -> b));
        } catch (Exception e) {
            throw new RuntimeException("benefits.json 로딩 실패", e);
        }
    }

    public Optional<Benefit> findById(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public List<Benefit> getAll() {
        return catalog;
    }
}
