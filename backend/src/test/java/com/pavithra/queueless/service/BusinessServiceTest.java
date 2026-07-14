package com.pavithra.queueless.service;

import com.pavithra.queueless.dto.business.BusinessRequest;
import com.pavithra.queueless.dto.business.BusinessResponse;
import com.pavithra.queueless.entity.Business;
import com.pavithra.queueless.entity.Role;
import com.pavithra.queueless.entity.User;
import com.pavithra.queueless.exception.ResourceNotFoundException;
import com.pavithra.queueless.repository.BusinessRepository;
import com.pavithra.queueless.repository.TicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BusinessServiceTest {

    @Mock private BusinessRepository businessRepository;
    @Mock private TicketRepository ticketRepository;

    @InjectMocks
    private BusinessService businessService;

    private final User owner = User.builder().id(1L).fullName("Owner One").email("owner@test.com").role(Role.BUSINESS_OWNER).build();

    @Test
    void createBusiness_savesAndReturnsResponse() {
        BusinessRequest request = new BusinessRequest("Downtown Clinic", "CLINIC", "123 Main St", 12.9, 77.6);
        when(businessRepository.save(any(Business.class))).thenAnswer(inv -> {
            Business b = inv.getArgument(0);
            b.setId(10L);
            return b;
        });
        when(ticketRepository.findWaitingQueueOrdered(10L)).thenReturn(List.of());

        BusinessResponse response = businessService.createBusiness(owner, request);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.name()).isEqualTo("Downtown Clinic");
        assertThat(response.currentQueueLength()).isEqualTo(0);
    }

    @Test
    void getById_throwsWhenNotFound() {
        when(businessRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> businessService.getById(999L));
    }

    @Test
    void toggleAcceptingCheckIns_rejectsNonOwner() {
        Business business = Business.builder().id(10L).name("Downtown Clinic").owner(owner).acceptingCheckIns(true).build();
        User notOwner = User.builder().id(2L).fullName("Someone Else").email("x@test.com").role(Role.BUSINESS_OWNER).build();

        when(businessRepository.findById(10L)).thenReturn(Optional.of(business));

        assertThrows(ResourceNotFoundException.class,
                () -> businessService.toggleAcceptingCheckIns(10L, notOwner, false));
    }

    @Test
    void toggleAcceptingCheckIns_updatesWhenRequesterIsOwner() {
        Business business = Business.builder().id(10L).name("Downtown Clinic").owner(owner).acceptingCheckIns(true).build();
        when(businessRepository.findById(10L)).thenReturn(Optional.of(business));
        when(businessRepository.save(any(Business.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ticketRepository.findWaitingQueueOrdered(anyLong())).thenReturn(List.of());

        BusinessResponse response = businessService.toggleAcceptingCheckIns(10L, owner, false);

        assertThat(response.acceptingCheckIns()).isFalse();
    }

    @Test
    void findNearby_sortsByDistanceAndExcludesBusinessesOutsideRadius() {
        Business close = Business.builder().id(1L).name("Close Clinic").owner(owner)
                .latitude(12.9716).longitude(77.5946).build();
        Business far = Business.builder().id(2L).name("Far Clinic").owner(owner)
                .latitude(13.0827).longitude(77.5946).build(); // ~12.3km north of "close"

        when(businessRepository.findAll()).thenReturn(List.of(close, far));
        when(ticketRepository.findWaitingQueueOrdered(anyLong())).thenReturn(List.of());

        List<BusinessResponse> results = businessService.findNearby(12.9800, 77.5946, 5.0);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).name()).isEqualTo("Close Clinic");
        assertThat(results.get(0).distanceKm()).isNotNull();
    }
}
