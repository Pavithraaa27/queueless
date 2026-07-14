package com.pavithra.queueless.dto.ticket;

import com.pavithra.queueless.entity.TicketStatus;

import java.time.Instant;

public record TicketResponse(
        Long id,
        Long businessId,
        String businessName,
        Long customerId,
        String customerName,
        TicketStatus status,
        Integer queuePosition,
        Long estimatedWaitSeconds,
        Instant checkedInAt
) {
}
