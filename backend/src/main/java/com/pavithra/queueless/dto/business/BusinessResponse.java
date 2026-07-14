package com.pavithra.queueless.dto.business;

public record BusinessResponse(
        Long id,
        String name,
        String category,
        String address,
        Double latitude,
        Double longitude,
        Double avgServiceTimeSeconds,
        Boolean acceptingCheckIns,
        Integer currentQueueLength,
        Double distanceKm
) {
}
