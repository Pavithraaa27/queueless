package com.pavithra.queueless.service;

import com.pavithra.queueless.dto.business.BusinessRequest;
import com.pavithra.queueless.dto.business.BusinessResponse;
import com.pavithra.queueless.entity.Business;
import com.pavithra.queueless.entity.User;
import com.pavithra.queueless.exception.ResourceNotFoundException;
import com.pavithra.queueless.repository.BusinessRepository;
import com.pavithra.queueless.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BusinessService {

    private final BusinessRepository businessRepository;
    private final TicketRepository ticketRepository;

    @Transactional
    public BusinessResponse createBusiness(User owner, BusinessRequest request) {
        Business business = Business.builder()
                .name(request.name())
                .category(request.category())
                .address(request.address())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .owner(owner)
                .build();

        businessRepository.save(business);
        return toResponse(business);
    }

    @Transactional(readOnly = true)
    public List<BusinessResponse> listAll(String category) {
        List<Business> businesses = (category == null || category.isBlank())
                ? businessRepository.findAll()
                : businessRepository.findByCategoryIgnoreCase(category);

        return businesses.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public BusinessResponse getById(Long id) {
        Business business = businessRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found: " + id));
        return toResponse(business);
    }

    @Transactional(readOnly = true)
    public List<BusinessResponse> listByOwner(Long ownerId) {
        return businessRepository.findByOwnerId(ownerId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public BusinessResponse toggleAcceptingCheckIns(Long businessId, User owner, boolean accepting) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found: " + businessId));

        if (!business.getOwner().getId().equals(owner.getId())) {
            throw new ResourceNotFoundException("Business not found: " + businessId);
        }

        business.setAcceptingCheckIns(accepting);
        businessRepository.save(business);
        return toResponse(business);
    }

    private static final double EARTH_RADIUS_KM = 6371.0;

    @Transactional(readOnly = true)
    public List<BusinessResponse> findNearby(double lat, double lng, double radiusKm) {
        return businessRepository.findAll().stream()
                .filter(b -> b.getLatitude() != null && b.getLongitude() != null)
                .map(b -> {
                    double distance = haversineKm(lat, lng, b.getLatitude(), b.getLongitude());
                    return toResponse(b, distance);
                })
                .filter(r -> r.distanceKm() <= radiusKm)
                .sorted(java.util.Comparator.comparingDouble(BusinessResponse::distanceKm))
                .toList();
    }

    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    private BusinessResponse toResponse(Business b) {
        return toResponse(b, null);
    }

    private BusinessResponse toResponse(Business b, Double distanceKm) {
        int queueLength = ticketRepository.findWaitingQueueOrdered(b.getId()).size();
        return new BusinessResponse(
                b.getId(), b.getName(), b.getCategory(), b.getAddress(),
                b.getLatitude(), b.getLongitude(), b.getAvgServiceTimeSeconds(),
                b.getAcceptingCheckIns(), queueLength, distanceKm
        );
    }
}
