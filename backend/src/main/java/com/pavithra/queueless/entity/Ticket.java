package com.pavithra.queueless.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A single customer's spot in a business's queue.
 * `queuePosition` and `estimatedWaitSeconds` are recalculated whenever the queue moves
 * (see QueueService) and pushed out over WebSocket.
 */
@Entity
@Table(name = "tickets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status;

    /** 1-based position in the live queue; null once no longer waiting. */
    private Integer queuePosition;

    /** Predicted seconds until this ticket is called, recalculated live. */
    private Long estimatedWaitSeconds;

    @Column(nullable = false, updatable = false)
    private Instant checkedInAt;

    private Instant serviceStartedAt;

    private Instant completedAt;

    /**
     * Optimistic locking guard. If two "call next" requests for the same business
     * land at the same instant, whichever commits second will fail with an
     * OptimisticLockingFailureException instead of silently double-assigning a
     * ticket - see GlobalExceptionHandler, which turns that into a clean 409
     * telling the client to just retry.
     */
    @Version
    private Long version;

    @PrePersist
    void onCreate() {
        this.checkedInAt = Instant.now();
        if (this.status == null) {
            this.status = TicketStatus.WAITING;
        }
    }
}
