package com.pavithra.queueless.dto.business;

public record BusinessAnalyticsResponse(
        Long businessId,
        long totalCheckInsToday,
        long totalServedToday,
        long noShowCountToday,
        Double avgServiceTimeSecondsToday,
        double currentAvgServiceTimeSeconds
) {
}
