package com.pavithra.queueless.repository;

import com.pavithra.queueless.entity.Ticket;
import com.pavithra.queueless.entity.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    @Query("select t from Ticket t where t.business.id = :businessId and t.status = 'WAITING' order by t.checkedInAt asc")
    List<Ticket> findWaitingQueueOrdered(@Param("businessId") Long businessId);

    @Query("select t from Ticket t where t.business.id = :businessId and t.status = 'IN_SERVICE'")
    Optional<Ticket> findCurrentlyInService(@Param("businessId") Long businessId);

    List<Ticket> findByCustomerIdAndStatusIn(Long customerId, List<TicketStatus> statuses);

    List<Ticket> findByStatus(TicketStatus status);

    @Query("select count(t) from Ticket t where t.business.id = :businessId and t.status = 'WAITING' and t.checkedInAt < :checkedInAt")
    long countAheadInQueue(@Param("businessId") Long businessId, @Param("checkedInAt") Instant checkedInAt);

    long countByBusinessIdAndCheckedInAtAfter(Long businessId, Instant after);

    long countByBusinessIdAndStatusAndCompletedAtAfter(Long businessId, TicketStatus status, Instant after);
}
