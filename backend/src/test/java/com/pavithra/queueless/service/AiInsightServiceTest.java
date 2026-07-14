package com.pavithra.queueless.service;

import com.pavithra.queueless.dto.business.BusinessAnalyticsResponse;
import com.pavithra.queueless.entity.Role;
import com.pavithra.queueless.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiInsightServiceTest {

    @Mock private AnalyticsService analyticsService;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private AiInsightService aiInsightService;

    private final User owner = User.builder().id(1L).fullName("Owner").email("owner@test.com").role(Role.BUSINESS_OWNER).build();

    @Test
    void getDailyInsight_usesTemplatedFallbackWhenAiDisabled() {
        // app.ai.enabled defaults to false and is never set here, so this exercises the no-API-key path
        ReflectionTestUtils.setField(aiInsightService, "aiEnabled", false);

        BusinessAnalyticsResponse stats = new BusinessAnalyticsResponse(10L, 12, 9, 1, 342.5, 360.0);
        when(analyticsService.getDailyAnalytics(10L, owner)).thenReturn(stats);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        String insight = aiInsightService.getDailyInsight(10L, owner);

        assertThat(insight).contains("9 of 12 check-ins");
        assertThat(insight).contains("1 no-show");
        assertThat(insight).contains("6 minutes"); // 342.5s rounds to 6 minutes
        verify(valueOperations).set(eq("insight:10:" + java.time.LocalDate.now()), eq(insight), eq(6L), eq(TimeUnit.HOURS));
    }

    @Test
    void getDailyInsight_returnsCachedValueWithoutRecomputing() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("Cached summary from earlier today.");
        // analyticsService is still called first since it also performs the ownership check
        BusinessAnalyticsResponse stats = new BusinessAnalyticsResponse(10L, 5, 5, 0, 300.0, 300.0);
        when(analyticsService.getDailyAnalytics(10L, owner)).thenReturn(stats);

        String insight = aiInsightService.getDailyInsight(10L, owner);

        assertThat(insight).isEqualTo("Cached summary from earlier today.");
        verify(valueOperations, never()).set(anyString(), any(), anyLong(), any());
    }

    @Test
    void getDailyInsight_handlesZeroCheckInsGracefully() {
        BusinessAnalyticsResponse stats = new BusinessAnalyticsResponse(10L, 0, 0, 0, null, 300.0);
        when(analyticsService.getDailyAnalytics(10L, owner)).thenReturn(stats);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        String insight = aiInsightService.getDailyInsight(10L, owner);

        assertThat(insight).contains("No check-ins yet today");
    }
}
