package com.pavithra.queueless.service;

import com.pavithra.queueless.dto.business.BusinessAnalyticsResponse;
import com.pavithra.queueless.entity.User;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Turns today's raw analytics numbers into a short, plain-English summary for
 * the business owner - "12 checked in, 9 served, avg wait 8 min" becomes a
 * sentence a human actually wants to read.
 *
 * Cost-safety by design: if no Anthropic API key is configured (app.ai.enabled
 * is false by default), this falls back to a rule-based template that reads
 * the same stats without calling any external API. Configure ANTHROPIC_API_KEY
 * and set AI_INSIGHTS_ENABLED=true to upgrade to real LLM-generated prose.
 * Either way, the result is cached per business per day so it's generated at
 * most once daily, not on every dashboard refresh.
 */
@Service
@RequiredArgsConstructor
public class AiInsightService {

    private static final Logger log = LoggerFactory.getLogger(AiInsightService.class);
    private static final String CACHE_PREFIX = "insight:";
    private static final String ANTHROPIC_URL = "https://api.anthropic.com/v1/messages";

    private final AnalyticsService analyticsService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.ai.enabled:false}")
    private boolean aiEnabled;

    @Value("${app.ai.anthropic-api-key:}")
    private String apiKey;

    @Value("${app.ai.model:claude-haiku-4-5-20251001}")
    private String model;

    public String getDailyInsight(Long businessId, User owner) {
        // reuses AnalyticsService's ownership check - throws if requester doesn't own this business
        BusinessAnalyticsResponse stats = analyticsService.getDailyAnalytics(businessId, owner);

        String cacheKey = CACHE_PREFIX + businessId + ":" + LocalDate.now();
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof String cachedInsight) {
            return cachedInsight;
        }

        String insight = (aiEnabled && apiKey != null && !apiKey.isBlank())
                ? generateWithLlm(stats)
                : templatedInsight(stats);

        redisTemplate.opsForValue().set(cacheKey, insight, 6, TimeUnit.HOURS);
        return insight;
    }

    private String generateWithLlm(BusinessAnalyticsResponse stats) {
        try {
            String prompt = buildPrompt(stats);

            HttpHeaders headers = new HttpHeaders();
            headers.set("x-api-key", apiKey);
            headers.set("anthropic-version", "2023-06-01");
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = Map.of(
                    "model", model,
                    "max_tokens", 150,
                    "messages", List.of(Map.of("role", "user", "content", prompt))
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    ANTHROPIC_URL, org.springframework.http.HttpMethod.POST, request,
                    (Class<Map<String, Object>>) (Class<?>) Map.class
            );

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
            String text = (String) content.get(0).get("text");
            return text.trim();
        } catch (Exception e) {
            log.warn("AI insight generation failed, falling back to templated summary: {}", e.getMessage());
            return templatedInsight(stats);
        }
    }

    private String buildPrompt(BusinessAnalyticsResponse stats) {
        String todaysAvg = stats.avgServiceTimeSecondsToday() != null
                ? Math.round(stats.avgServiceTimeSecondsToday() / 60.0) + " minutes"
                : "not enough data yet today";

        return String.format(
                "You are writing a short daily summary for the owner of a small business that uses a live " +
                "queue management system. Today's stats: %d customers checked in, %d were served, %d did not " +
                "show up when called, and the average service time today is %s (their long-run average is " +
                "%.0f seconds). Write a friendly 2-3 sentence plain-English summary with one practical " +
                "observation or suggestion if relevant. No markdown, no bullet points, no greeting - just the summary.",
                stats.totalCheckInsToday(), stats.totalServedToday(), stats.noShowCountToday(),
                todaysAvg, stats.currentAvgServiceTimeSeconds()
        );
    }

    /** Rule-based fallback - no external API call, works with zero configuration. */
    private String templatedInsight(BusinessAnalyticsResponse stats) {
        if (stats.totalCheckInsToday() == 0) {
            return "No check-ins yet today — once customers start joining the queue, you'll see a daily summary here.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("You've served ").append(stats.totalServedToday()).append(" of ")
                .append(stats.totalCheckInsToday()).append(" check-ins today");

        if (stats.noShowCountToday() > 0) {
            sb.append(", with ").append(stats.noShowCountToday())
                    .append(stats.noShowCountToday() == 1 ? " no-show" : " no-shows");
        }
        sb.append(". ");

        if (stats.avgServiceTimeSecondsToday() != null) {
            long minutes = Math.round(stats.avgServiceTimeSecondsToday() / 60.0);
            sb.append("Average service time today is around ").append(minutes)
                    .append(minutes == 1 ? " minute." : " minutes.");
        } else {
            sb.append("Complete a few more tickets to start seeing today's average service time.");
        }

        return sb.toString();
    }
}
