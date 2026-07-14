package com.pavithra.queueless.dto.business;

import jakarta.validation.constraints.NotBlank;

public record BusinessRequest(
        @NotBlank(message = "Name is required") String name,
        @NotBlank(message = "Category is required") String category,
        @NotBlank(message = "Address is required") String address,
        Double latitude,
        Double longitude
) {
}
