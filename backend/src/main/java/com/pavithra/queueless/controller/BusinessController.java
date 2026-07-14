package com.pavithra.queueless.controller;

import com.pavithra.queueless.dto.business.BusinessAnalyticsResponse;
import com.pavithra.queueless.dto.business.BusinessRequest;
import com.pavithra.queueless.dto.business.BusinessResponse;
import com.pavithra.queueless.security.UserPrincipal;
import com.pavithra.queueless.service.AiInsightService;
import com.pavithra.queueless.service.AnalyticsService;
import com.pavithra.queueless.service.BusinessService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/businesses")
@RequiredArgsConstructor
@Tag(name = "Businesses", description = "Manage service-providing businesses")
public class BusinessController {

    private final BusinessService businessService;
    private final AnalyticsService analyticsService;
    private final AiInsightService aiInsightService;

    @PostMapping
    public ResponseEntity<BusinessResponse> create(@AuthenticationPrincipal UserPrincipal principal,
                                                     @Valid @RequestBody BusinessRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(businessService.createBusiness(principal.getUser(), request));
    }

    @GetMapping
    public ResponseEntity<List<BusinessResponse>> list(@RequestParam(required = false) String category) {
        return ResponseEntity.ok(businessService.listAll(category));
    }

    @GetMapping("/nearby")
    public ResponseEntity<List<BusinessResponse>> nearby(@RequestParam double lat,
                                                           @RequestParam double lng,
                                                           @RequestParam(defaultValue = "10") double radiusKm) {
        return ResponseEntity.ok(businessService.findNearby(lat, lng, radiusKm));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BusinessResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(businessService.getById(id));
    }

    @GetMapping("/mine")
    public ResponseEntity<List<BusinessResponse>> mine(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(businessService.listByOwner(principal.getUser().getId()));
    }

    @PatchMapping("/{id}/accepting")
    public ResponseEntity<BusinessResponse> toggleAccepting(@PathVariable Long id,
                                                              @AuthenticationPrincipal UserPrincipal principal,
                                                              @RequestParam boolean accepting) {
        return ResponseEntity.ok(businessService.toggleAcceptingCheckIns(id, principal.getUser(), accepting));
    }

    @GetMapping("/{id}/analytics")
    public ResponseEntity<BusinessAnalyticsResponse> analytics(@PathVariable Long id,
                                                                 @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(analyticsService.getDailyAnalytics(id, principal.getUser()));
    }

    @GetMapping("/{id}/insight")
    public ResponseEntity<Map<String, String>> insight(@PathVariable Long id,
                                                         @AuthenticationPrincipal UserPrincipal principal) {
        String insight = aiInsightService.getDailyInsight(id, principal.getUser());
        return ResponseEntity.ok(Map.of("insight", insight));
    }
}
