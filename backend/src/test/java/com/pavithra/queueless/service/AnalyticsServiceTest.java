package com.pavithra.queueless.service;

import com.pavithra.queueless.dto.business.BusinessAnalyticsResponse;
import com.pavithra.queueless.entity.Business;
import com.pavithra.queueless.entity.Role;
import com.pavithra.queueless.entity.TicketStatus;
import com.pavithra.queueless.entity.User;
import com.pavithra.queueless.exception.BadRequestException;
import com.pavithra.queueless.repository.BusinessRepository;
import com.pavithra.queueless.repository.ServiceLogRepository;
import com.pavithra.queueless.repository.TicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock private BusinessRepository businessRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private ServiceLogRepository serviceLogRepository;

    @InjectMocks
    private AnalyticsService analyticsService;

    private final User owner = User.builder().id(1L).fullName("Owner One").email("owner@test.com").role(Role.BUSINESS_OWNER).build();

    @Test
    void getDailyAnalytics_returnsCountsAndAverages() {
        Business business = Business.builder().id(10L).name("Downtown Clinic").owner(owner)
                .avgServiceTimeSeconds(360.0).build();

        when(businessRepository.findById(10L)).thenReturn(Optional.of(business));
        when(ticketRepository.countByBusinessIdAndCheckedInAtAfter(anyLong(), any())).thenReturn(12L);
        when(ticketRepository.countByBusinessIdAndStatusAndCompletedAtAfter(anyLong(), org.mockito.ArgumentMatchers.eq(TicketStatus.COMPLETED), any())).thenReturn(9L);
        when(ticketRepository.countByBusinessIdAndStatusAndCompletedAtAfter(anyLong(), org.mockito.ArgumentMatchers.eq(TicketStatus.NO_SHOW), any())).thenReturn(1L);
        when(serviceLogRepository.findAverageServiceTimeSince(anyLong(), any())).thenReturn(342.5);

        BusinessAnalyticsResponse response = analyticsService.getDailyAnalytics(10L, owner);

        assertThat(response.totalCheckInsToday()).isEqualTo(12L);
        assertThat(response.totalServedToday()).isEqualTo(9L);
        assertThat(response.noShowCountToday()).isEqualTo(1L);
        assertThat(response.avgServiceTimeSecondsToday()).isEqualTo(342.5);
        assertThat(response.currentAvgServiceTimeSeconds()).isEqualTo(360.0);
    }

    @Test
    void getDailyAnalytics_rejectsNonOwner() {
        Business business = Business.builder().id(10L).name("Downtown Clinic").owner(owner).avgServiceTimeSeconds(300.0).build();
        User notOwner = User.builder().id(99L).fullName("Someone Else").email("x@test.com").role(Role.BUSINESS_OWNER).build();

        when(businessRepository.findById(10L)).thenReturn(Optional.of(business));

        assertThrows(BadRequestException.class, () -> analyticsService.getDailyAnalytics(10L, notOwner));
    }
}
