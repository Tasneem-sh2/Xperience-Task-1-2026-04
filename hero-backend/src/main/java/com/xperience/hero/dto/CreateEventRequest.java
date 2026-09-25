package com.xperience.hero.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;

public record CreateEventRequest(
        @NotBlank String title,
        String description,
        @NotNull Instant startTime,
        @NotBlank String location,
        @Positive Integer maxCapacity) {
}
