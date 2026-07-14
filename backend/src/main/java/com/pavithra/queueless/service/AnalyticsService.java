package com.pavithra.queueless.service;

import com.pavithra.queueless.dto.business.BusinessAnalyticsResponse;
import com.pavithra.queueless.entity.Business;
import com.pavithra.queueless.entity.TicketStatus;
import com.pavithra.queueless.entity.User;
import com.pavithra.queueless.exception.BadRequestException;
import com.pavithra.queueless.exception.ResourceNotFoundException;
import com.pavithra.queueless.repository.BusinessRepository;
import com.pavithra.queueless.repository.ServiceLogRepository;
import com.pavithra.queueless.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Powers the owner-facing "how's today going" panel. Everything here reads
 * from data the queue already produces as a side effect (Ticket check-ins,
 * ServiceLog entries) - no separate tracking system needed.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final BusinessRepository businessRepository;
    private final TicketRepository ticketRepository;
    private final ServiceLogRepository serviceLogRepository;

    @Transactional(readOnly = true)
    public BusinessAnalyticsResponse getDailyAnalytics(Long businessId, User requester) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found: " + businessId));

        if (!business.getOwner().getId().equals(requester.getId())) {
            throw new BadRequestException("You don't manage this business");
        }

        Instant startOfDay = LocalDate.now(ZoneId.systemDefault())
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant();

        long totalCheckInsToday = ticketRepository.countByBusinessIdAndCheckedInAtAfter(businessId, startOfDay);
        long totalServedToday = ticketRepository.countByBusinessIdAndStatusAndCompletedAtAfter(
                businessId, TicketStatus.COMPLETED, startOfDay);
        long noShowCountToday = ticketRepository.countByBusinessIdAndStatusAndCompletedAtAfter(
                businessId, TicketStatus.NO_SHOW, startOfDay);
        Double avgServiceTimeToday = serviceLogRepository.findAverageServiceTimeSince(businessId, startOfDay);

        return new BusinessAnalyticsResponse(
                businessId,
                totalCheckInsToday,
                totalServedToday,
                noShowCountToday,
                avgServiceTimeToday,
                business.getAvgServiceTimeSeconds()
        );
    }
}
