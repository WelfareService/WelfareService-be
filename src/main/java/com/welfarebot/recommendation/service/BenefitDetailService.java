package com.welfarebot.recommendation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.welfarebot.recommendation.dto.BenefitDetailDto;
import com.welfarebot.recommendation.exception.InvalidBenefitIdException;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class BenefitDetailService {

    private static final Path DATA_PATH = Path.of("/mnt/data/benefits.json");

    private final ObjectMapper objectMapper;

    private Map<String, BenefitDetailDto> benefits = Collections.emptyMap();

    @PostConstruct
    public void loadDetails() {
        try (InputStream is = openSourceStream()) {
            List<BenefitDetailDto> detailList = objectMapper.readValue(is, new TypeReference<List<BenefitDetailDto>>() {});
            benefits = detailList.stream()
                    .filter(dto -> normalizeKey(dto.getBenefitId()) != null)
                    .collect(Collectors.toUnmodifiableMap(
                            dto -> normalizeKey(dto.getBenefitId()),
                            dto -> dto,
                            (existing, duplicate) -> existing));
        } catch (IOException e) {
            throw new IllegalStateException("benefits.json 로딩 실패", e);
        }
    }

    public BenefitDetailDto getDetail(String rawBenefitId) {
        String normalized = normalizeKey(rawBenefitId);
        if (normalized == null) {
            throw new InvalidBenefitIdException("benefitId must not be blank");
        }
        BenefitDetailDto dto = benefits.get(normalized);
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Benefit not found: " + rawBenefitId);
        }
        return dto;
    }

    private InputStream openSourceStream() throws IOException {
        if (Files.exists(DATA_PATH)) {
            return Files.newInputStream(DATA_PATH);
        }
        return new ClassPathResource("benefits.json").getInputStream();
    }

    private String normalizeKey(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        String withoutHash = trimHash(trimmed);
        if (withoutHash.isEmpty()) {
            return null;
        }
        return withoutHash.toLowerCase(Locale.ROOT);
    }

    private String trimHash(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == '#') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == '#') {
            end--;
        }
        return value.substring(start, end);
    }

}
