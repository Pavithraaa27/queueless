package com.pavithra.queueless.repository;

import com.pavithra.queueless.entity.ServiceLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ServiceLogRepository extends JpaRepository<ServiceLog, Long> {

    @Query("select s from ServiceLog s where s.business.id = :businessId order by s.recordedAt desc")
    List<ServiceLog> findRecentByBusiness(@Param("businessId") Long businessId, Pageable pageable);

    @Query("select avg(s.actualServiceTimeSeconds) from ServiceLog s where s.business.id = :businessId and s.recordedAt >= :since")
    Double findAverageServiceTimeSince(@Param("businessId") Long businessId, @Param("since") Instant since);
}
