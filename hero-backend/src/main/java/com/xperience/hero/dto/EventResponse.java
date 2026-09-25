package com.xperience.hero.dto;

import java.time.Instant;

/**
 * Event details safe to return from lookup/close/cancel responses.
 * Deliberately excludes hostToken - see EventCreatedResponse for the
 * one response shape that is allowed to include it.
 */
public record EventResponse(
        Long id,
        String title,
        String description,
        Instant startTime,
        String location,
        Integer maxCapacity,
        String status) {
}
