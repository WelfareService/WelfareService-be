package com.welfarebot.recommendation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SignalOntologyService {

    private final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());

    @Value("classpath:signal-ontology.yml")
    private Resource ontologyResource;

    private final Set<String> minimalConditionSignals = ConcurrentHashMap.newKeySet();
    private final Map<String, String> synonymToCanonical = new ConcurrentHashMap<>();
    private final Map<String, List<String>> canonicalToSynonyms = new ConcurrentHashMap<>();

    private static final String FALLBACK_CANONICAL = "기타위험";

    @PostConstruct
    public void init() {
        try {
            SignalOntologyConfig config = objectMapper.readValue(ontologyResource.getInputStream(), SignalOntologyConfig.class);
            minimalConditionSignals.addAll(config.getMinimalConditionSignals().stream()
                    .map(this::normalize)
                    .collect(Collectors.toSet()));

            config.getCanonicalSignals().forEach((canonical, synonyms) -> {
                String canonicalKey = normalize(canonical);
                canonicalToSynonyms.put(canonicalKey, synonyms);
                synonymToCanonical.put(canonicalKey, canonicalKey);
                synonyms.stream()
                        .map(this::normalize)
                        .forEach(synonym -> synonymToCanonical.put(synonym, canonicalKey));
            });
            log.info("[Ontology] signal-ontology.yml loaded (canonical={}, minimal={})",
                    canonicalToSynonyms.size(), minimalConditionSignals.size());
        } catch (IOException e) {
            throw new IllegalStateException("signal-ontology.yml 로딩 실패", e);
        }
    }

    public SignalNormalizationResult normalizeSignals(List<String> rawSignals) {
        if (rawSignals == null || rawSignals.isEmpty()) {
            return new SignalNormalizationResult(List.of(), List.of());
        }
        LinkedHashSet<String> canonicalSignals = new LinkedHashSet<>();
        List<String> unknownSignals = new ArrayList<>();

        rawSignals.stream()
                .filter(Objects::nonNull)
                .map(this::normalize)
                .filter(value -> !value.isBlank())
                .forEach(signal -> {
                    String canonical = synonymToCanonical.get(signal);
                    if (canonical != null) {
                        canonicalSignals.add(canonical);
                    } else {
                        canonicalSignals.add(FALLBACK_CANONICAL);
                        unknownSignals.add(signal);
                        log.info("[Ontology] unknown signal detected: {}", signal);
                    }
                });

        return new SignalNormalizationResult(List.copyOf(canonicalSignals), List.copyOf(unknownSignals));
    }

    public boolean containsMinimalConditionSignal(List<String> normalizedSignals) {
        if (normalizedSignals == null || normalizedSignals.isEmpty()) {
            return false;
        }
        return normalizedSignals.stream()
                .map(this::normalize)
                .anyMatch(minimalConditionSignals::contains);
    }

    private String normalize(String input) {
        return input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
    }

    @Getter
    public static class SignalOntologyConfig {
        private List<String> minimalConditionSignals = new ArrayList<>();
        private Map<String, List<String>> canonicalSignals = new ConcurrentHashMap<>();

        public void setMinimalConditionSignals(List<String> minimalConditionSignals) {
            this.minimalConditionSignals = minimalConditionSignals != null ? minimalConditionSignals : new ArrayList<>();
        }

        public void setCanonicalSignals(Map<String, List<String>> canonicalSignals) {
            this.canonicalSignals = canonicalSignals != null ? canonicalSignals : new ConcurrentHashMap<>();
        }
    }

    public record SignalNormalizationResult(List<String> canonicalSignals, List<String> unknownSignals) {
        public boolean hasUnknownOnly() {
            return !unknownSignals.isEmpty() && canonicalSignals.stream()
                    .allMatch(signal -> signal.equals(FALLBACK_CANONICAL));
        }
    }
}
