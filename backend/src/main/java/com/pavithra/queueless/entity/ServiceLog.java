package com.pavithra.queueless.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Records how long each completed ticket actually took to serve.
 * The ETA engine reads the most recent N logs per business to keep
 * avgServiceTimeSeconds accurate instead of a static hardcoded number.
 */
@Entity
@Table(name = "service_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @Column(nullable = false)
    private Long actualServiceTimeSeconds;

    @Column(nullable = false, updatable = false)
    private Instant recordedAt;

    @PrePersist
    void onCreate() {
        this.recordedAt = Instant.now();
    }
}
