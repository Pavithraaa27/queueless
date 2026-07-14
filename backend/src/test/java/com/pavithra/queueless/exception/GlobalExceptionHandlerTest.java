package com.pavithra.queueless.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleConcurrentUpdate_returnsConflictWithFriendlyMessage() {
        ResponseEntity<Map<String, Object>> response = handler.handleConcurrentUpdate(
                new OptimisticLockingFailureException("stale entity")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("error")).isEqualTo("This queue was just updated by someone else - please try again");
    }
}
