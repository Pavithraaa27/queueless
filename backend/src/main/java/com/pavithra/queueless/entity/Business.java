package com.pavithra.queueless.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A service provider that runs a live queue (clinic, salon, govt office, repair shop, etc).
 * avgServiceTimeSeconds is a rolling estimate, kept up to date by ServiceLog entries
 * every time a ticket is completed - this is what powers the ETA prediction.
 */
@Entity
@Table(name = "businesses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Business {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String category; // e.g. CLINIC, SALON, GOVT_OFFICE, REPAIR_SHOP

    @Column(nullable = false)
    private String address;

    private Double latitude;
    private Double longitude;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Builder.Default
    @Column(nullable = false)
    private Double avgServiceTimeSeconds = 600.0; // default 10 min until real data accumulates

    @Builder.Default
    @Column(nullable = false)
    private Boolean acceptingCheckIns = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
