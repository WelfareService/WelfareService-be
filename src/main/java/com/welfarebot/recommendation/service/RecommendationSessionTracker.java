package com.welfarebot.recommendation.service;

import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class RecommendationSessionTracker {

    private static final String ATTR_ISSUED = "recommendationIssued";
    private static final String ATTR_ISSUED_AT = "recommendationIssuedAt";

    public RecommendationSessionState read(HttpSession session) {
        if (session == null) {
            return new RecommendationSessionState(false, null);
        }
        Boolean issued = (Boolean) session.getAttribute(ATTR_ISSUED);
        LocalDateTime issuedAt = (LocalDateTime) session.getAttribute(ATTR_ISSUED_AT);
        return new RecommendationSessionState(Boolean.TRUE.equals(issued), issuedAt);
    }

    public void markIssued(HttpSession session, LocalDateTime issuedAt) {
        if (session == null) {
            return;
        }
        session.setAttribute(ATTR_ISSUED, true);
        session.setAttribute(ATTR_ISSUED_AT, issuedAt != null ? issuedAt : LocalDateTime.now());
    }

    public void reset(HttpSession session) {
        if (session == null) {
            return;
        }
        session.setAttribute(ATTR_ISSUED, false);
        session.removeAttribute(ATTR_ISSUED_AT);
    }

    public record RecommendationSessionState(boolean issued, LocalDateTime lastRecommendationAt) {
    }
}
