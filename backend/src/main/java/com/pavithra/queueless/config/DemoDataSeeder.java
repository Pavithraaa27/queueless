package com.pavithra.queueless.config;

import com.pavithra.queueless.entity.*;
import com.pavithra.queueless.repository.BusinessRepository;
import com.pavithra.queueless.repository.ServiceLogRepository;
import com.pavithra.queueless.repository.TicketRepository;
import com.pavithra.queueless.repository.UserRepository;
import com.pavithra.queueless.service.QueueService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Populates a demo business with a bit of history and a live queue the first
 * time the app starts against an empty database - so a recruiter (or you,
 * demoing it) sees a working queue immediately instead of an empty state.
 *
 * Only runs once: if any business already exists, it does nothing. Can also
 * be disabled entirely via app.seed-demo-data=false (see docker-compose.yml).
 *
 * All demo accounts use the password "demo1234" - listed in the README.
 */
@Component
@RequiredArgsConstructor
@Order(100)
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final String DEMO_PASSWORD = "demo1234";

    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final TicketRepository ticketRepository;
    private final ServiceLogRepository serviceLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final QueueService queueService;

    @Value("${app.seed-demo-data:true}")
    private boolean seedEnabled;

    @Override
    public void run(ApplicationArguments args) {
        if (!seedEnabled) {
            log.info("Demo data seeding disabled (app.seed-demo-data=false)");
            return;
        }
        if (businessRepository.count() > 0) {
            log.info("Businesses already exist - skipping demo data seed");
            return;
        }

        log.info("Seeding demo data...");

        User owner = createUser("Demo Owner", "owner@demo.queueless.app", Role.BUSINESS_OWNER);

        Business business = Business.builder()
                .name("Sunrise Family Clinic")
                .category("CLINIC")
                .address("14 MG Road, Bengaluru")
                .latitude(12.9756)
                .longitude(77.6068)
                .owner(owner)
                .acceptingCheckIns(true)
                .avgServiceTimeSeconds(420.0)
                .build();
        businessRepository.save(business);

        seedTodaysHistory(business);
        seedLiveQueue(business);

        // assigns queue positions/ETAs to the waiting tickets and caches the snapshot
        queueService.recalculateQueue(business.getId());

        log.info("Demo data ready: business '{}' (id={}), owner login owner@demo.queueless.app / {}",
                business.getName(), business.getId(), DEMO_PASSWORD);
    }

    /** A handful of already-completed visits earlier today, so the ETA average and
     *  analytics panel show real numbers instead of defaults/zeros on first load. */
    private void seedTodaysHistory(Business business) {
        int[] pastServiceTimesSeconds = {360, 480, 300, 540, 390};
        double runningAvg = business.getAvgServiceTimeSeconds();

        for (int i = 0; i < pastServiceTimesSeconds.length; i++) {
            User pastCustomer = createUser(
                    "Demo Patient " + (i + 1),
                    "demo.patient" + (i + 1) + "@queueless.app",
                    Role.CUSTOMER
            );

            Instant start = Instant.now().minusSeconds((long) (pastServiceTimesSeconds.length - i) * 900);
            Instant end = start.plusSeconds(pastServiceTimesSeconds[i]);

            Ticket completed = Ticket.builder()
                    .business(business)
                    .customer(pastCustomer)
                    .status(TicketStatus.COMPLETED)
                    .serviceStartedAt(start)
                    .completedAt(end)
                    .build();
            ticketRepository.save(completed);
            // checkedInAt is set by @PrePersist on first save; back-date it with a follow-up update
            completed.setCheckedInAt(start.minusSeconds(60));
            ticketRepository.save(completed);

            serviceLogRepository.save(ServiceLog.builder()
                    .business(business)
                    .ticket(completed)
                    .actualServiceTimeSeconds((long) pastServiceTimesSeconds[i])
                    .build());

            runningAvg = 0.3 * pastServiceTimesSeconds[i] + 0.7 * runningAvg;
        }

        business.setAvgServiceTimeSeconds(runningAvg);
        businessRepository.save(business);
    }

    /** One customer currently being served, a few more waiting - a live queue to look at immediately. */
    private void seedLiveQueue(Business business) {
        User serving = createUser("Anita Rao", "demo.live1@queueless.app", Role.CUSTOMER);
        ticketRepository.save(Ticket.builder()
                .business(business)
                .customer(serving)
                .status(TicketStatus.IN_SERVICE)
                .serviceStartedAt(Instant.now().minusSeconds(120))
                .build());

        String[] waitingNames = {"Kiran Shah", "Meera Iyer", "Devraj Patil"};
        for (String name : waitingNames) {
            String email = "demo." + name.toLowerCase().replace(" ", ".") + "@queueless.app";
            User waitingCustomer = createUser(name, email, Role.CUSTOMER);
            ticketRepository.save(Ticket.builder()
                    .business(business)
                    .customer(waitingCustomer)
                    .status(TicketStatus.WAITING)
                    .build());
        }
    }

    private User createUser(String name, String email, Role role) {
        return userRepository.save(User.builder()
                .fullName(name)
                .email(email)
                .passwordHash(passwordEncoder.encode(DEMO_PASSWORD))
                .role(role)
                .build());
    }
}
