package com.welfarebot.recommendation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.welfarebot.recommendation.config.RecommendationPolicyLoader;
import com.welfarebot.recommendation.config.RecommendationPolicyLoader.BoostRule;
import com.welfarebot.recommendation.dto.ChatRequest;
import com.welfarebot.recommendation.dto.ChatResponse;
import com.welfarebot.recommendation.dto.GptMatchResponse;
import com.welfarebot.recommendation.model.Benefit;
import com.welfarebot.recommendation.model.BenefitMatchLog;
import com.welfarebot.recommendation.model.User;
import com.welfarebot.recommendation.model.UserPreRecommendation;
import com.welfarebot.recommendation.repository.BenefitMatchLogRepository;
import com.welfarebot.recommendation.service.RecommendationSessionTracker.RecommendationSessionState;
import com.welfarebot.recommendation.service.SignalOntologyService.SignalNormalizationResult;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private static final int RECOMMENDATION_COUNT = 3;
    private static final String DECISION_RECOMMEND = "RECOMMEND";
    private static final String DECISION_OVERRIDE = "RECOMMENDATION_OVERRIDE";
    private static final String DECISION_SKIPPED_MC = "SKIPPED_MC_FAIL";
    private static final String DECISION_SKIPPED_ALREADY_ISSUED = "SKIPPED_ALREADY_ISSUED";
    private static final Set<String> CRITICAL_RISK_SIGNALS = Set.of(
            "주거불안",
            "미취업",
            "저소득",
            "생계위험",
            "생계곤란",
            "생활비부담",
            "부채",
            "체납",
            "건강위험",
            "의료위험",
            "돌봄공백"
    );

    private final OpenAiService openAiService;
    private final BenefitCatalogService catalogService;
    private final PreRecommendationService preRecommendationService;
    private final UserService userService;
    private final RejectService rejectService;
    private final BenefitMatchLogRepository benefitMatchLogRepository;
    private final ObjectMapper objectMapper;
    private final RecommendationPolicyLoader policyLoader;
    private final SignalOntologyService signalOntologyService;

    public RecommendationResult chatRecommend(ChatRequest request, User user, RecommendationSessionState sessionState) {
        List<String> userBaseTags = userService.parseTags(user);
        List<String> baseTags = normalizeBaseTags(userBaseTags);

        GptMatchResponse gptResponse = openAiService.analyzeMessage(request.getMessage(), user, request.getHistory());
        SignalNormalizationResult signalResult = signalOntologyService.normalizeSignals(gptResponse.getSignals());
        List<String> canonicalSignals = signalResult.canonicalSignals();
        boolean insufficientInfo = Boolean.TRUE.equals(gptResponse.getInsufficientInfo());
        RiskLevel riskLevel = determineRiskLevel(canonicalSignals);

        MinimalConditionResult mcResult = evaluateMinimalCondition(insufficientInfo, canonicalSignals,
                signalResult.hasUnknownOnly());

        boolean alreadyIssued = isAlreadyIssued(user, sessionState);
        boolean forceOverride = alreadyIssued && isReRecommendTrigger(request.getMessage());

        if (!mcResult.passed()) {
            saveDecisionLog(user != null ? user.getId() : null, DECISION_SKIPPED_MC, mcResult.reasons(), canonicalSignals, riskLevel);
            ChatResponse response = buildResponse(gptResponse.getAssistantMessage(), List.of(), riskLevel, user,
                    userBaseTags, alreadyIssued);
            return new RecommendationResult(response, false, null);
        }

        if (alreadyIssued && !forceOverride) {
            List<String> reasons = List.of("ALREADY_ISSUED");
            saveDecisionLog(user != null ? user.getId() : null, DECISION_SKIPPED_ALREADY_ISSUED, reasons, canonicalSignals, riskLevel);
            ChatResponse response = buildResponse(gptResponse.getAssistantMessage(), List.of(), riskLevel, user,
                    userBaseTags, true);
            return new RecommendationResult(response, false, null);
        }

        List<UserPreRecommendation> pool = preRecommendationService.getPool(user);
        Set<String> rejectedIds = rejectService.getRejectedBenefitIds(request.getUserId());
        List<ScoredResult> scoredResults = pool.stream()
                .map(entry -> scoreEntry(entry, baseTags, canonicalSignals, rejectedIds))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .sorted(Comparator.comparingDouble(ScoredResult::finalScore).reversed())
                .limit(RECOMMENDATION_COUNT)
                .collect(Collectors.toList());

        if (scoredResults.isEmpty()) {
            log.warn("[Recommendation] No candidates remained after scoring. Fallback to catalog results.");
            scoredResults = catalogService.getAll().stream()
                    .limit(RECOMMENDATION_COUNT)
                    .map(benefit -> new ScoredResult(
                            benefit,
                            0.5,
                            0.5,
                            List.of(),
                            List.of(),
                            buildItem(benefit, 0.5)
                    ))
                    .collect(Collectors.toList());
        }

        boolean issued = !scoredResults.isEmpty();
        LocalDateTime issuedAt = issued ? LocalDateTime.now() : null;
        if (issued && user != null) {
            userService.markRecommendationIssued(user);
        }
        boolean recommendationHistory = alreadyIssued || issued;

        String decisionType = forceOverride ? DECISION_OVERRIDE : DECISION_RECOMMEND;
        List<String> logReasons = forceOverride ? List.of("RE_REQUEST_TRIGGER") : List.of();
        scoredResults.forEach(result -> saveMatchLog(user, decisionType, logReasons, canonicalSignals, riskLevel,
                result));

        List<ChatResponse.RecommendationItem> items = scoredResults.stream()
                .map(ScoredResult::item)
                .collect(Collectors.toList());

        ChatResponse response = buildResponse(gptResponse.getAssistantMessage(), items, riskLevel, user,
                userBaseTags, recommendationHistory);

        return new RecommendationResult(response, issued, issuedAt);
    }

    private boolean isAlreadyIssued(User user, RecommendationSessionState sessionState) {
        if (user != null && Boolean.TRUE.equals(user.getRecommendationIssued())) {
            return true;
        }
        return sessionState != null && sessionState.issued();
    }

    private boolean isReRecommendTrigger(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = normalizeForSearch(message);
        return policyLoader.getReRecommendTriggers().stream()
                .map(this::normalizeForSearch)
                .anyMatch(normalized::contains);
    }

    private MinimalConditionResult evaluateMinimalCondition(boolean insufficientInfo,
                                                            List<String> canonicalSignals,
                                                            boolean unknownOnly) {
        List<String> reasons = new ArrayList<>();
        if (insufficientInfo) {
            reasons.add("INSUFFICIENT_INFO");
        }
        if (!signalOntologyService.containsMinimalConditionSignal(canonicalSignals)) {
            reasons.add("NO_CRITICAL_SIGNAL");
        }
        if (unknownOnly) {
            reasons.add("UNKNOWN_SIGNAL");
        }
        return new MinimalConditionResult(reasons.isEmpty(), List.copyOf(reasons));
    }

    private RiskLevel determineRiskLevel(List<String> signals) {
        if (signals == null || signals.isEmpty()) {
            return RiskLevel.LOW;
        }
        Set<String> signalSet = signals.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        long criticalCount = signalSet.stream()
                .filter(CRITICAL_RISK_SIGNALS::contains)
                .count();
        if (criticalCount >= 2) {
            return RiskLevel.HIGH;
        }
        if (criticalCount == 1) {
            return RiskLevel.MID;
        }
        return RiskLevel.LOW;
    }

    private Optional<ScoredResult> scoreEntry(UserPreRecommendation entry,
                                              List<String> baseTags,
                                              List<String> signals,
                                              Set<String> rejected) {
        if (entry == null || entry.getBenefitId() == null) {
            return Optional.empty();
        }
        Benefit benefit = catalogService.findById(entry.getBenefitId()).orElse(null);
        if (benefit == null) {
            return Optional.empty();
        }
        if (rejected.contains(entry.getBenefitId())) {
            return Optional.empty();
        }
        double baseScore = Optional.ofNullable(entry.getBaseScore()).orElse(0.5);
        Set<String> categories = policyLoader.resolveCategories(benefit.getCategory());
        BoostAccumulator accumulator = new BoostAccumulator(policyLoader.getBoostCap());
        List<BoostLog> baseTagLogs = applyBaseTagBoost(baseTags, categories, accumulator);
        List<BoostLog> signalLogs = applySignalBoost(signals, categories, accumulator);

        double finalScore = Math.min(baseScore + accumulator.totalBoost(), 1.0);
        ChatResponse.RecommendationItem item = buildItem(benefit, finalScore);
        return Optional.of(new ScoredResult(benefit, baseScore, finalScore, baseTagLogs, signalLogs, item));
    }

    private List<BoostLog> applyBaseTagBoost(List<String> baseTags,
                                             Set<String> categories,
                                             BoostAccumulator accumulator) {
        if (baseTags == null || baseTags.isEmpty() || categories.isEmpty()) {
            return List.of();
        }
        Map<String, BoostRule> rules = policyLoader.getBaseTagBoostRules();
        List<BoostLog> logs = new ArrayList<>();
        for (String tag : baseTags) {
            BoostRule rule = rules.get(tag);
            if (rule == null) {
                continue;
            }
            boolean match = rule.getCategories().stream().anyMatch(categories::contains);
            if (match) {
                double applied = accumulator.apply(rule.getBoost());
                if (applied > 0) {
                    logs.add(new BoostLog("baseTag:" + tag, applied));
                }
            }
        }
        return logs;
    }

    private List<BoostLog> applySignalBoost(List<String> signals,
                                            Set<String> categories,
                                            BoostAccumulator accumulator) {
        if (signals == null || signals.isEmpty() || categories.isEmpty()) {
            return List.of();
        }
        Map<String, BoostRule> rules = policyLoader.getSignalBoostRules();
        List<BoostLog> logs = new ArrayList<>();
        for (String signal : signals) {
            BoostRule rule = rules.get(signal);
            if (rule == null) {
                continue;
            }
            boolean match = rule.getCategories().stream().anyMatch(categories::contains);
            if (match) {
                double applied = accumulator.apply(rule.getBoost());
                if (applied > 0) {
                    logs.add(new BoostLog("signal:" + signal, applied));
                }
            }
        }
        return logs;
    }

    private ChatResponse.RecommendationItem buildItem(Benefit benefit, double score) {
        return ChatResponse.RecommendationItem.builder()
                .benefitId(benefit.getBenefit_id())
                .title(benefit.getTitle())
                .category(benefit.getCategory())
                .score(score)
                .summary(benefit.getSummary())
                .location(ChatResponse.RecommendationItem.Location.builder()
                        .lat(benefit.getLocation() != null ? benefit.getLocation().getLat() : null)
                        .lng(benefit.getLocation() != null ? benefit.getLocation().getLng() : null)
                        .build())
                .build();
    }

    private ChatResponse buildResponse(String assistantMessage,
                                       List<ChatResponse.RecommendationItem> items,
                                       RiskLevel riskLevel,
                                       User user,
                                       List<String> userBaseTags,
                                       boolean recommendationIssuedFlag) {
        return ChatResponse.builder()
                .assistantMessage(determineAssistantMessage(assistantMessage))
                .recommendations(items)
                .riskLevel(riskLevel.name())
                .userName(resolveUserName(user))
                .residence(resolveResidence(user))
                .baseTags(userBaseTags)
                .recommendationIssued(recommendationIssuedFlag)
                .build();
    }

    private List<String> normalizeBaseTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = tags.stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(tag -> tag.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return List.copyOf(normalized);
    }

    private String normalizeForSearch(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private String determineAssistantMessage(String message) {
        if (message == null || message.isBlank()) {
            return "말씀해주신 상황을 바탕으로 도움될 수 있는 지원을 정리해 보았어요.";
        }
        return message;
    }

    private void saveMatchLog(User user,
                              String decisionType,
                              List<String> mcFailReasons,
                              List<String> normalizedSignals,
                              RiskLevel riskLevel,
                              ScoredResult result) {
        try {
            BenefitMatchLog entity = new BenefitMatchLog();
            entity.setUserId(user != null ? user.getId() : null);
            entity.setBenefitId(result.benefit().getBenefit_id());
            entity.setBaseScore(result.baseScore());
            entity.setBoostedScore(result.finalScore());
            entity.setAppliedBoostTags(toJson(result.tagBoostLogs()));
            entity.setAppliedSignals(toJson(result.signalBoostLogs()));
            entity.setDecisionType(decisionType);
            entity.setMcFailReasons(toJson(mcFailReasons));
            entity.setRiskLevel(riskLevel.name());
            entity.setNormalizedSignals(toJson(normalizedSignals));
            entity.setCreatedAt(LocalDateTime.now());
            benefitMatchLogRepository.save(entity);
        } catch (Exception e) {
            log.warn("[Recommendation] Failed to save match log", e);
        }
    }

    private void saveDecisionLog(Long userId,
                                 String decisionType,
                                 List<String> reasons,
                                 List<String> normalizedSignals,
                                 RiskLevel riskLevel) {
        try {
            BenefitMatchLog entity = new BenefitMatchLog();
            entity.setUserId(userId);
            entity.setDecisionType(decisionType);
            entity.setMcFailReasons(toJson(reasons));
            entity.setAppliedBoostTags("[]");
            entity.setAppliedSignals("[]");
            entity.setRiskLevel(riskLevel.name());
            entity.setNormalizedSignals(toJson(normalizedSignals));
            entity.setCreatedAt(LocalDateTime.now());
            benefitMatchLogRepository.save(entity);
        } catch (Exception e) {
            log.warn("[Recommendation] Failed to save decision log", e);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    private record ScoredResult(
            Benefit benefit,
            double baseScore,
            double finalScore,
            List<BoostLog> tagBoostLogs,
            List<BoostLog> signalBoostLogs,
            ChatResponse.RecommendationItem item
    ) {
    }

    private record BoostLog(String type, double amount) {
    }

    private record MinimalConditionResult(boolean passed, List<String> reasons) {
    }

    public record RecommendationResult(ChatResponse response, boolean recommendationIssued, LocalDateTime issuedAt) {
    }

    private String resolveUserName(User user) {
        if (user == null || user.getName() == null || user.getName().isBlank()) {
            return "알 수 없음";
        }
        return user.getName();
    }

    private String resolveResidence(User user) {
        if (user == null || user.getResidence() == null || user.getResidence().isBlank()) {
            return "미입력";
        }
        return user.getResidence();
    }

    private static class BoostAccumulator {
        private final double cap;
        private double consumed = 0.0;

        private BoostAccumulator(double cap) {
            this.cap = cap;
        }

        double apply(double amount) {
            if (amount <= 0 || consumed >= cap) {
                return 0;
            }
            double remaining = cap - consumed;
            double applied = Math.min(amount, remaining);
            consumed += applied;
            return applied;
        }

        double totalBoost() {
            return consumed;
        }
    }

    private enum RiskLevel {
        HIGH,
        MID,
        LOW
    }
}
