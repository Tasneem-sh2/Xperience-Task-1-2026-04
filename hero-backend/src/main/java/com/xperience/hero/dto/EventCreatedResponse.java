package com.xperience.hero.dto;

import java.time.Instant;

/**
 * Returned only from event creation. Includes hostToken because this is the
 * one point where the host credential must be handed back to its owner.
 */
public record EventCreatedResponse(
        Long id,
        String title,
        String description,
        Instant startTime,
        String location,
        Integer maxCapacity,
        String status,
        String hostToken) {
}
