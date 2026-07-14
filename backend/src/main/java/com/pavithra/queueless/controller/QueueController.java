package com.pavithra.queueless.controller;

import com.pavithra.queueless.dto.ticket.TicketResponse;
import com.pavithra.queueless.security.UserPrincipal;
import com.pavithra.queueless.service.QueueService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/queue")
@RequiredArgsConstructor
@Tag(name = "Queue", description = "Live queue operations: check-in, call next, cancel, snapshot")
public class QueueController {

    private final QueueService queueService;

    @PostMapping("/{businessId}/check-in")
    public ResponseEntity<TicketResponse> checkIn(@PathVariable Long businessId,
                                                    @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(queueService.checkIn(businessId, principal.getUser().getId()));
    }

    @PostMapping("/{businessId}/call-next")
    public ResponseEntity<TicketResponse> callNext(@PathVariable Long businessId,
                                                     @AuthenticationPrincipal UserPrincipal principal) {
        TicketResponse next = queueService.callNext(businessId, principal.getUser());
        return next == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(next);
    }

    @DeleteMapping("/tickets/{ticketId}")
    public ResponseEntity<TicketResponse> cancel(@PathVariable Long ticketId,
                                                   @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(queueService.cancel(ticketId, principal.getUser().getId()));
    }

    @GetMapping("/{businessId}/snapshot")
    public ResponseEntity<List<TicketResponse>> snapshot(@PathVariable Long businessId) {
        return ResponseEntity.ok(queueService.getLiveSnapshot(businessId));
    }
}
