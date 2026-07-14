package com.pavithra.queueless.service;

import com.pavithra.queueless.dto.ticket.TicketResponse;
import com.pavithra.queueless.entity.*;
import com.pavithra.queueless.exception.BadRequestException;
import com.pavithra.queueless.exception.ResourceNotFoundException;
import com.pavithra.queueless.repository.BusinessRepository;
import com.pavithra.queueless.repository.ServiceLogRepository;
import com.pavithra.queueless.repository.TicketRepository;
import com.pavithra.queueless.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The heart of QueueLess.
 *
 * ETA algorithm: each business tracks a rolling average service time
 * (avgServiceTimeSeconds), updated with an exponential moving average
 * every time a ticket is completed - so it adapts to how a business is
 * actually running today, not a static number set once.
 *
 * A waiting ticket's estimated wait = (people ahead of it) * avgServiceTime,
 * minus however long the currently in-service customer has already been
 * served (so the estimate tightens as their turn approaches).
 *
 * No-show handling: a background job (expireNoShows) periodically checks
 * every ticket currently "in service" - if it's been sitting there far
 * longer than the business's usual service time with no completion, the
 * customer is assumed to have not shown up. Their ticket is marked
 * NO_SHOW (not counted toward the ETA average, since no service actually
 * happened) and the next person in line is automatically called.
 */
@Service
@RequiredArgsConstructor
public class QueueService {

    private static final Logger log = LoggerFactory.getLogger(QueueService.class);

    private static final double EMA_ALPHA = 0.3; // weight given to the newest observation
    private static final String CACHE_PREFIX = "queue:snapshot:";

    // A ticket is considered a no-show once it's been "in service" for longer than
    // this multiple of the business's average service time...
    private static final double NO_SHOW_GRACE_MULTIPLIER = 2.0;
    // ...but never sooner than this floor, so a business with a very short average
    // (e.g. right after startup) doesn't skip people after just a few seconds.
    private static final long NO_SHOW_MIN_GRACE_SECONDS = 120;

    private final TicketRepository ticketRepository;
    private final BusinessRepository businessRepository;
    private final ServiceLogRepository serviceLogRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    @Transactional
    public TicketResponse checkIn(Long businessId, Long customerId) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found: " + businessId));

        if (!Boolean.TRUE.equals(business.getAcceptingCheckIns())) {
            throw new BadRequestException("This business isn't accepting check-ins right now");
        }

        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + customerId));

        boolean alreadyInQueue = !ticketRepository
                .findByCustomerIdAndStatusIn(customerId, List.of(TicketStatus.WAITING, TicketStatus.IN_SERVICE))
                .isEmpty();
        if (alreadyInQueue) {
            throw new BadRequestException("You're already in a queue. Finish or cancel it first.");
        }

        Ticket ticket = Ticket.builder()
                .business(business)
                .customer(customer)
                .status(TicketStatus.WAITING)
                .build();

        ticketRepository.save(ticket);

        recalculateQueue(businessId);
        return toResponse(refreshed(ticket.getId()));
    }

    @Transactional
    public TicketResponse callNext(Long businessId, User owner) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found: " + businessId));
        assertOwns(business, owner);

        // complete whoever is currently in service
        ticketRepository.findCurrentlyInService(businessId).ifPresent(current -> completeTicket(current, business));

        List<Ticket> waiting = ticketRepository.findWaitingQueueOrdered(businessId);
        if (waiting.isEmpty()) {
            recalculateQueue(businessId);
            return null;
        }

        Ticket next = waiting.get(0);
        next.setStatus(TicketStatus.IN_SERVICE);
        next.setServiceStartedAt(Instant.now());
        next.setQueuePosition(0);
        next.setEstimatedWaitSeconds(0L);
        ticketRepository.save(next);

        recalculateQueue(businessId);
        return toResponse(refreshed(next.getId()));
    }

    @Transactional
    public TicketResponse cancel(Long ticketId, Long requesterId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));

        if (!ticket.getCustomer().getId().equals(requesterId)) {
            throw new BadRequestException("You can only cancel your own ticket");
        }
        if (ticket.getStatus() != TicketStatus.WAITING) {
            throw new BadRequestException("Only a waiting ticket can be cancelled");
        }

        ticket.setStatus(TicketStatus.CANCELLED);
        ticketRepository.save(ticket);

        recalculateQueue(ticket.getBusiness().getId());
        return toResponse(ticket);
    }

    /**
     * Runs every 30 seconds. Looks at every ticket currently marked IN_SERVICE
     * across all businesses; if one has been sitting there far longer than that
     * business's usual service time, assumes the customer didn't show up,
     * marks it NO_SHOW, and automatically calls the next person in that queue.
     */
    @Scheduled(fixedRate = 30_000)
    @Transactional
    public void expireNoShows() {
        List<Ticket> inService = ticketRepository.findByStatus(TicketStatus.IN_SERVICE);

        for (Ticket ticket : inService) {
            Business business = ticket.getBusiness();
            long elapsedSeconds = Duration.between(ticket.getServiceStartedAt(), Instant.now()).getSeconds();
            long graceSeconds = Math.max(
                    NO_SHOW_MIN_GRACE_SECONDS,
                    (long) (business.getAvgServiceTimeSeconds() * NO_SHOW_GRACE_MULTIPLIER)
            );

            if (elapsedSeconds > graceSeconds) {
                log.info("Ticket {} at business {} timed out after {}s (grace was {}s) - marking NO_SHOW",
                        ticket.getId(), business.getId(), elapsedSeconds, graceSeconds);
                markNoShowAndAdvance(ticket, business);
            }
        }
    }

    private void markNoShowAndAdvance(Ticket ticket, Business business) {
        ticket.setStatus(TicketStatus.NO_SHOW);
        ticket.setCompletedAt(Instant.now());
        ticketRepository.save(ticket);
        // Deliberately NOT logging a ServiceLog or updating the EMA here -
        // no actual service happened, so it shouldn't skew the ETA average.

        List<Ticket> waiting = ticketRepository.findWaitingQueueOrdered(business.getId());
        if (!waiting.isEmpty()) {
            Ticket next = waiting.get(0);
            next.setStatus(TicketStatus.IN_SERVICE);
            next.setServiceStartedAt(Instant.now());
            next.setQueuePosition(0);
            next.setEstimatedWaitSeconds(0L);
            ticketRepository.save(next);
        }

        recalculateQueue(business.getId());
    }

    /** Recomputes positions + ETAs for every waiting ticket, caches the snapshot, and broadcasts it live. */
    @Transactional
    public void recalculateQueue(Long businessId) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found: " + businessId));

        List<Ticket> waiting = ticketRepository.findWaitingQueueOrdered(businessId);
        double avgServiceTime = business.getAvgServiceTimeSeconds();

        var inServiceOpt = ticketRepository.findCurrentlyInService(businessId);
        long baseOffsetSeconds = inServiceOpt
                .map(inService -> {
                    long elapsed = Duration.between(inService.getServiceStartedAt(), Instant.now()).getSeconds();
                    return Math.max(0, (long) avgServiceTime - elapsed);
                })
                .orElse(0L);

        int position = 1;
        for (Ticket t : waiting) {
            t.setQueuePosition(position);
            long peopleAheadInWaitingLine = position - 1L;
            t.setEstimatedWaitSeconds(baseOffsetSeconds + (long) (peopleAheadInWaitingLine * avgServiceTime));
            position++;
        }
        ticketRepository.saveAll(waiting);

        List<TicketResponse> snapshot = new java.util.ArrayList<>();
        inServiceOpt.ifPresent(t -> snapshot.add(toResponse(t)));
        waiting.forEach(t -> snapshot.add(toResponse(t)));

        redisTemplate.opsForValue().set(CACHE_PREFIX + businessId, snapshot, 5, TimeUnit.MINUTES);
        messagingTemplate.convertAndSend("/topic/queue/" + businessId, snapshot);
    }

    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public List<TicketResponse> getLiveSnapshot(Long businessId) {
        Object cached = redisTemplate.opsForValue().get(CACHE_PREFIX + businessId);
        if (cached != null) {
            return (List<TicketResponse>) cached;
        }
        List<TicketResponse> snapshot = new java.util.ArrayList<>();
        ticketRepository.findCurrentlyInService(businessId).ifPresent(t -> snapshot.add(toResponse(t)));
        ticketRepository.findWaitingQueueOrdered(businessId).forEach(t -> snapshot.add(toResponse(t)));
        return snapshot;
    }

    private void completeTicket(Ticket current, Business business) {
        current.setStatus(TicketStatus.COMPLETED);
        current.setCompletedAt(Instant.now());
        ticketRepository.save(current);

        long actualSeconds = Duration.between(current.getServiceStartedAt(), current.getCompletedAt()).getSeconds();
        serviceLogRepository.save(ServiceLog.builder()
                .business(business)
                .ticket(current)
                .actualServiceTimeSeconds(actualSeconds)
                .build());

        // exponential moving average keeps the ETA model adaptive to real conditions
        double newAvg = EMA_ALPHA * actualSeconds + (1 - EMA_ALPHA) * business.getAvgServiceTimeSeconds();
        business.setAvgServiceTimeSeconds(newAvg);
        businessRepository.save(business);
    }

    private void assertOwns(Business business, User owner) {
        if (!business.getOwner().getId().equals(owner.getId())) {
            throw new BadRequestException("You don't manage this business");
        }
    }

    private Ticket refreshed(Long ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));
    }

    private TicketResponse toResponse(Ticket t) {
        return new TicketResponse(
                t.getId(), t.getBusiness().getId(), t.getBusiness().getName(),
                t.getCustomer().getId(), t.getCustomer().getFullName(),
                t.getStatus(), t.getQueuePosition(), t.getEstimatedWaitSeconds(), t.getCheckedInAt()
        );
    }
}
