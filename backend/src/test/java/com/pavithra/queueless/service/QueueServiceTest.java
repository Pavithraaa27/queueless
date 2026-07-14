package com.pavithra.queueless.service;

import com.pavithra.queueless.dto.ticket.TicketResponse;
import com.pavithra.queueless.entity.*;
import com.pavithra.queueless.repository.BusinessRepository;
import com.pavithra.queueless.repository.ServiceLogRepository;
import com.pavithra.queueless.repository.TicketRepository;
import com.pavithra.queueless.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private BusinessRepository businessRepository;
    @Mock private ServiceLogRepository serviceLogRepository;
    @Mock private UserRepository userRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private QueueService queueService;

    private Business business;
    private User owner;
    private User customer;

    @BeforeEach
    void setUp() {
        owner = User.builder().id(1L).fullName("Owner One").email("owner@test.com").role(Role.BUSINESS_OWNER).build();
        customer = User.builder().id(2L).fullName("Cust One").email("cust@test.com").role(Role.CUSTOMER).build();

        business = Business.builder()
                .id(10L)
                .name("Downtown Clinic")
                .category("CLINIC")
                .address("123 Main St")
                .owner(owner)
                .avgServiceTimeSeconds(300.0) // 5 min average
                .acceptingCheckIns(true)
                .build();

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void checkIn_assignsFirstQueuePositionWithNoBaseOffset() {
        when(businessRepository.findById(10L)).thenReturn(Optional.of(business));
        when(userRepository.findById(2L)).thenReturn(Optional.of(customer));
        when(ticketRepository.findByCustomerIdAndStatusIn(eq(2L), anyList())).thenReturn(List.of());

        Ticket saved = Ticket.builder().id(100L).business(business).customer(customer)
                .status(TicketStatus.WAITING).checkedInAt(Instant.now()).build();

        // capture what gets saved so findById can return a consistent view afterwards
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> {
            Ticket t = inv.getArgument(0);
            if (t.getId() == null) t.setId(100L);
            return t;
        });
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(saved));
        when(ticketRepository.findWaitingQueueOrdered(10L)).thenReturn(new ArrayList<>(List.of(saved)));
        when(ticketRepository.findCurrentlyInService(10L)).thenReturn(Optional.empty());
        when(ticketRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        TicketResponse response = queueService.checkIn(10L, 2L);

        assertThat(response.queuePosition()).isEqualTo(1);
        // no one ahead and no one currently in service -> ETA should be 0
        assertThat(response.estimatedWaitSeconds()).isEqualTo(0L);
        verify(messagingTemplate).convertAndSend(eq("/topic/queue/10"), anyList());
    }

    @Test
    void checkIn_rejectsWhenBusinessNotAcceptingCheckIns() {
        business.setAcceptingCheckIns(false);
        when(businessRepository.findById(10L)).thenReturn(Optional.of(business));

        org.junit.jupiter.api.Assertions.assertThrows(
                com.pavithra.queueless.exception.BadRequestException.class,
                () -> queueService.checkIn(10L, 2L)
        );
    }

    @Test
    void checkIn_rejectsWhenCustomerAlreadyHasActiveTicket() {
        when(businessRepository.findById(10L)).thenReturn(Optional.of(business));
        when(userRepository.findById(2L)).thenReturn(Optional.of(customer));

        Ticket existing = Ticket.builder().id(55L).status(TicketStatus.WAITING).build();
        when(ticketRepository.findByCustomerIdAndStatusIn(eq(2L), anyList())).thenReturn(List.of(existing));

        org.junit.jupiter.api.Assertions.assertThrows(
                com.pavithra.queueless.exception.BadRequestException.class,
                () -> queueService.checkIn(10L, 2L)
        );
    }

    @Test
    void recalculateQueue_secondPersonInLineWaitsOneFullAverageServiceTime() {
        User customer2 = User.builder().id(3L).fullName("Cust Two").email("c2@test.com").role(Role.CUSTOMER).build();

        Ticket first = Ticket.builder().id(101L).business(business).customer(customer)
                .status(TicketStatus.WAITING).checkedInAt(Instant.now().minusSeconds(60)).build();
        Ticket second = Ticket.builder().id(102L).business(business).customer(customer2)
                .status(TicketStatus.WAITING).checkedInAt(Instant.now()).build();

        when(businessRepository.findById(10L)).thenReturn(Optional.of(business));
        when(ticketRepository.findWaitingQueueOrdered(10L)).thenReturn(new ArrayList<>(List.of(first, second)));
        when(ticketRepository.findCurrentlyInService(10L)).thenReturn(Optional.empty());
        when(ticketRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        queueService.recalculateQueue(10L);

        assertThat(first.getQueuePosition()).isEqualTo(1);
        assertThat(first.getEstimatedWaitSeconds()).isEqualTo(0L);

        assertThat(second.getQueuePosition()).isEqualTo(2);
        // one person ahead * 300s avg service time = 300s
        assertThat(second.getEstimatedWaitSeconds()).isEqualTo(300L);
    }

    @Test
    void callNext_completesCurrentTicketAndUpdatesRollingAverageServiceTime() {
        Ticket current = Ticket.builder().id(200L).business(business).customer(customer)
                .status(TicketStatus.IN_SERVICE)
                .serviceStartedAt(Instant.now().minusSeconds(420)) // took 420s (7 min) this time
                .checkedInAt(Instant.now().minusSeconds(500))
                .build();

        User customer2 = User.builder().id(3L).fullName("Cust Two").email("c2@test.com").role(Role.CUSTOMER).build();
        Ticket waitingNext = Ticket.builder().id(201L).business(business).customer(customer2)
                .status(TicketStatus.WAITING).checkedInAt(Instant.now()).build();

        when(businessRepository.findById(10L)).thenReturn(Optional.of(business));
        when(ticketRepository.findCurrentlyInService(10L))
                .thenReturn(Optional.of(current))  // first call: someone finishing up
                .thenReturn(Optional.empty());       // subsequent calls during recalculation: no one yet
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ticketRepository.findWaitingQueueOrdered(10L)).thenReturn(new ArrayList<>(List.of(waitingNext)));
        when(ticketRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(ticketRepository.findById(201L)).thenReturn(Optional.of(waitingNext));

        TicketResponse response = queueService.callNext(10L, owner);

        // the completed ticket should be logged and the business average nudged toward 420s
        verify(serviceLogRepository).save(any(ServiceLog.class));
        assertThat(current.getStatus()).isEqualTo(TicketStatus.COMPLETED);
        // EMA: 0.3 * 420 + 0.7 * 300 = 336
        assertThat(business.getAvgServiceTimeSeconds()).isEqualTo(336.0);

        assertThat(response.status()).isEqualTo(TicketStatus.IN_SERVICE);
        assertThat(waitingNext.getStatus()).isEqualTo(TicketStatus.IN_SERVICE);
    }

    @Test
    void callNext_rejectsWhenRequesterDoesNotOwnBusiness() {
        User someoneElse = User.builder().id(99L).fullName("Not Owner").email("x@test.com").role(Role.BUSINESS_OWNER).build();
        when(businessRepository.findById(10L)).thenReturn(Optional.of(business));

        org.junit.jupiter.api.Assertions.assertThrows(
                com.pavithra.queueless.exception.BadRequestException.class,
                () -> queueService.callNext(10L, someoneElse)
        );
    }

    @Test
    void callNext_returnsNullWhenQueueIsEmpty() {
        when(businessRepository.findById(10L)).thenReturn(Optional.of(business));
        when(ticketRepository.findCurrentlyInService(10L)).thenReturn(Optional.empty());
        when(ticketRepository.findWaitingQueueOrdered(10L)).thenReturn(List.of());
        when(ticketRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        TicketResponse response = queueService.callNext(10L, owner);

        assertThat(response).isNull();
    }

    @Test
    void cancel_marksWaitingTicketCancelledAndRecalculates() {
        Ticket ticket = Ticket.builder().id(300L).business(business).customer(customer)
                .status(TicketStatus.WAITING).checkedInAt(Instant.now()).build();

        when(ticketRepository.findById(300L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));
        when(businessRepository.findById(10L)).thenReturn(Optional.of(business));
        when(ticketRepository.findWaitingQueueOrdered(10L)).thenReturn(new ArrayList<>());
        when(ticketRepository.findCurrentlyInService(10L)).thenReturn(Optional.empty());
        when(ticketRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        TicketResponse response = queueService.cancel(300L, customer.getId());

        assertThat(response.status()).isEqualTo(TicketStatus.CANCELLED);
    }

    @Test
    void cancel_rejectsWhenNotTheTicketOwner() {
        Ticket ticket = Ticket.builder().id(300L).business(business).customer(customer)
                .status(TicketStatus.WAITING).checkedInAt(Instant.now()).build();
        when(ticketRepository.findById(300L)).thenReturn(Optional.of(ticket));

        org.junit.jupiter.api.Assertions.assertThrows(
                com.pavithra.queueless.exception.BadRequestException.class,
                () -> queueService.cancel(300L, 999L)
        );
    }

    @Test
    void cancel_rejectsWhenTicketNotWaiting() {
        Ticket ticket = Ticket.builder().id(300L).business(business).customer(customer)
                .status(TicketStatus.COMPLETED).checkedInAt(Instant.now()).build();
        when(ticketRepository.findById(300L)).thenReturn(Optional.of(ticket));

        org.junit.jupiter.api.Assertions.assertThrows(
                com.pavithra.queueless.exception.BadRequestException.class,
                () -> queueService.cancel(300L, customer.getId())
        );
    }

    @Test
    void getLiveSnapshot_returnsCachedValueWithoutHittingRepositoryWhenPresent() {
        List<TicketResponse> cached = List.of(
                new TicketResponse(1L, 10L, "Downtown Clinic", 2L, "Cust One", TicketStatus.WAITING, 1, 0L, Instant.now())
        );
        when(valueOperations.get("queue:snapshot:10")).thenReturn(cached);

        List<TicketResponse> result = queueService.getLiveSnapshot(10L);

        assertThat(result).isEqualTo(cached);
        verify(ticketRepository, never()).findWaitingQueueOrdered(anyLong());
    }

    @Test
    void getLiveSnapshot_fallsBackToRepositoryOnCacheMiss() {
        when(valueOperations.get("queue:snapshot:10")).thenReturn(null);
        when(ticketRepository.findCurrentlyInService(10L)).thenReturn(Optional.empty());
        when(ticketRepository.findWaitingQueueOrdered(10L)).thenReturn(List.of());

        List<TicketResponse> result = queueService.getLiveSnapshot(10L);

        assertThat(result).isEmpty();
        verify(ticketRepository).findWaitingQueueOrdered(10L);
    }

    @Test
    void expireNoShows_marksTicketNoShowAndPromotesNextAfterGracePeriodExceeded() {
        // avg service time is 300s, so grace = max(120, 300*2) = 600s; make this ticket 700s old
        Ticket stale = Ticket.builder().id(400L).business(business).customer(customer)
                .status(TicketStatus.IN_SERVICE)
                .serviceStartedAt(Instant.now().minusSeconds(700))
                .checkedInAt(Instant.now().minusSeconds(800))
                .build();

        User customer2 = User.builder().id(3L).fullName("Cust Two").email("c2@test.com").role(Role.CUSTOMER).build();
        Ticket waitingNext = Ticket.builder().id(401L).business(business).customer(customer2)
                .status(TicketStatus.WAITING).checkedInAt(Instant.now()).build();

        when(ticketRepository.findByStatus(TicketStatus.IN_SERVICE)).thenReturn(List.of(stale));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ticketRepository.findWaitingQueueOrdered(10L)).thenReturn(new ArrayList<>(List.of(waitingNext)));
        when(businessRepository.findById(10L)).thenReturn(Optional.of(business));
        when(ticketRepository.findCurrentlyInService(10L)).thenReturn(Optional.empty());
        when(ticketRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        queueService.expireNoShows();

        assertThat(stale.getStatus()).isEqualTo(TicketStatus.NO_SHOW);
        assertThat(waitingNext.getStatus()).isEqualTo(TicketStatus.IN_SERVICE);
        verify(serviceLogRepository, never()).save(any()); // no-shows shouldn't affect the ETA average
    }

    @Test
    void expireNoShows_leavesTicketAloneWhenWithinGracePeriod() {
        Ticket recent = Ticket.builder().id(402L).business(business).customer(customer)
                .status(TicketStatus.IN_SERVICE)
                .serviceStartedAt(Instant.now().minusSeconds(60)) // well within grace
                .checkedInAt(Instant.now().minusSeconds(120))
                .build();

        when(ticketRepository.findByStatus(TicketStatus.IN_SERVICE)).thenReturn(List.of(recent));

        queueService.expireNoShows();

        assertThat(recent.getStatus()).isEqualTo(TicketStatus.IN_SERVICE);
        verify(ticketRepository, never()).save(any());
    }
}
