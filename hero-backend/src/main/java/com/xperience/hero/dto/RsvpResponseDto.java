package com.xperience.hero.dto;

import java.time.Instant;

public record RsvpResponseDto(
        Long eventId,
        String eventTitle,
        String eventDescription,
        Instant eventStartTime,
        String eventLocation,
        String eventStatus,
        String email,
        String response,
        String attendanceStatus) {
}
