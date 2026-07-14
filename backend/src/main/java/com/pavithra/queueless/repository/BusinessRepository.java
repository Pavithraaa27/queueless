package com.pavithra.queueless.repository;

import com.pavithra.queueless.entity.Business;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BusinessRepository extends JpaRepository<Business, Long> {
    List<Business> findByCategoryIgnoreCase(String category);
    List<Business> findByOwnerId(Long ownerId);
    List<Business> findByNameContainingIgnoreCase(String name);
}
